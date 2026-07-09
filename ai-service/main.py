"""
AI Recovery Assistant — FastAPI backend.

Architecture:
- Stateless API (no DB, relies on PWA's IndexedDB via export)
- Per-user analysis via data payload
- Designed for gradual integration: start with /analyze, add /chat later

Endpoints:
  POST /analyze   — анализирует дневник тяги, возвращает инсайты
  POST /chat      — диалоговая поддержка (заглушка)
  GET  /health    — проверка сервиса
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional

app = FastAPI(
    title="AI Recovery Assistant",
    description="Сервис анализа и поддержки для приложения «Чистый день»",
    version="0.1.0",
)


class DiaryEntry(BaseModel):
    date: str
    intensity: int  # 0-10
    trigger: Optional[str] = None
    emotion: Optional[str] = None
    tools_used: list[str] = []
    tools_helped: list[str] = []
    duration_min: Optional[int] = None
    outcome: Optional[str] = None


class AnalyzeRequest(BaseModel):
    entries: list[DiaryEntry]


class AnalyzeResponse(BaseModel):
    total_episodes: int
    avg_intensity: float
    top_triggers: list[dict]
    best_tools: list[dict]
    risk_period: Optional[str] = None
    insight: str


class ChatRequest(BaseModel):
    message: str
    history: list[dict] = []


class ChatResponse(BaseModel):
    reply: str


@app.get("/health")
async def health():
    return {"status": "ok", "version": "0.1.0"}


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(req: AnalyzeRequest):
    entries = req.entries
    if not entries:
        raise HTTPException(status_code=400, detail="No entries provided")

    total = len(entries)
    avg_intensity = round(sum(e.intensity for e in entries) / total, 1)

    triggers = {}
    for e in entries:
        if e.trigger:
            triggers[e.trigger] = triggers.get(e.trigger, 0) + 1

    tools_success = {}
    for e in entries:
        for t in e.tools_used:
            if t not in tools_success:
                tools_success[t] = {"used": 0, "helped": 0}
            tools_success[t]["used"] += 1
        for t in e.tools_helped:
            if t in tools_success:
                tools_success[t]["helped"] += 1

    top_triggers = sorted(triggers.items(), key=lambda x: -x[1])[:5]
    best_tools = sorted(
        [(t, d["helped"], d["used"]) for t, d in tools_success.items() if d["used"] > 0],
        key=lambda x: -x[1] / x[2] if x[2] > 0 else 0,
    )[:5]

    high_intensity = [e for e in entries if e.intensity >= 7]
    insight = _generate_insight(avg_intensity, top_triggers, best_tools, high_intensity)

    return AnalyzeResponse(
        total_episodes=total,
        avg_intensity=avg_intensity,
        top_triggers=[{"trigger": t, "count": c} for t, c in top_triggers],
        best_tools=[{"tool": t, "helped": h, "used": u} for t, h, u in best_tools],
        risk_period=_find_risk_period(entries),
        insight=insight,
    )


@app.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    return ChatResponse(
        reply="Я здесь, чтобы поддержать тебя. Расскажи, что происходит. "
              "Помни: это чувство временное, и ты уже справлялся раньше."
    )


def _generate_insight(
    avg_intensity: float,
    top_triggers: list,
    best_tools: list,
    high_intensity: list,
) -> str:
    parts = []
    if avg_intensity < 4:
        parts.append("Средняя интенсивность тяги невысокая — это хороший знак.")
    elif avg_intensity < 7:
        parts.append("Средняя интенсивность умеренная. Обрати внимание на триггеры.")
    else:
        parts.append("Средняя интенсивность высокая. Возможно, стоит усилить поддержку.")

    if top_triggers:
        parts.append(f"Основной триггер: «{top_triggers[0][0]}» — встречается {top_triggers[0][1]} раз(а).")

    if best_tools:
        t, h, u = best_tools[0]
        pct = round(h / u * 100) if u > 0 else 0
        parts.append(f"Самый эффективный инструмент: «{t}» — помогает в {pct}% случаев.")

    if len(high_intensity) >= 3:
        parts.append("Замечено несколько эпизодов с сильной тягой (≥7). Рекомендую заранее планировать поддержку.")

    return " ".join(parts)


def _find_risk_period(entries: list) -> Optional[str]:
    from collections import defaultdict
    hourly = defaultdict(int)
    for e in entries:
        try:
            h = int(e.date[11:13]) if len(e.date) >= 13 else 12
            hourly[h] += 1
        except (ValueError, IndexError):
            pass
    if not hourly:
        return None
    peak_hour = max(hourly, key=hourly.get)
    period = (
        "ночь" if 0 <= peak_hour < 6
        else "утро" if 6 <= peak_hour < 12
        else "день" if 12 <= peak_hour < 18
        else "вечер"
    )
    return f"чаще всего тяга возникает {period} ({peak_hour}:00, {hourly[peak_hour]} раз(а))"
