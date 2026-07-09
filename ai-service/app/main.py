"""
AI Recovery Assistant — FastAPI backend.

Endpoints:
  POST /chat          — диалог с AI-компаньоном
  POST /analyze       — анализ дневника тяги
  GET  /profile       — профиль пользователя
  POST /profile       — обновление профиля (имя, стиль)
  GET  /memory        — просмотр долгой памяти
  POST /memory/clear  — очистка памяти
  GET  /health        — проверка сервиса
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from .models.schemas import (
    ChatRequest, ChatResponse,
    AnalyzeRequest, AnalyzeResponse,
    ProfileUpdate,
)
from .agents.recovery_agent import RecoveryAgent

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

agent: RecoveryAgent | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global agent
    logger.info("Starting AI Recovery Agent...")
    agent = RecoveryAgent()
    yield
    logger.info("Shutting down.")


app = FastAPI(
    title="AI Recovery Assistant",
    description="AI-компаньон восстановления для приложения «Чистый день»",
    version="0.2.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {"status": "ok", "version": "0.2.0"}


@app.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    if not agent:
        raise HTTPException(503, "Agent not ready")
    return agent.chat(req)


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(req: AnalyzeRequest):
    if not agent:
        raise HTTPException(503, "Agent not ready")
    # Use existing static analysis from v1
    entries = req.entries
    if not entries:
        raise HTTPException(400, "No entries provided")

    total = len(entries)
    avg_intensity = round(sum(e.intensity for e in entries) / total, 1) if total else 0

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
    insight = _insight(avg_intensity, top_triggers, best_tools, high_intensity)

    return AnalyzeResponse(
        total_episodes=total,
        avg_intensity=avg_intensity,
        top_triggers=[{"trigger": t, "count": c} for t, c in top_triggers],
        best_tools=[{"tool": t, "helped": h, "used": u} for t, h, u in best_tools],
        risk_period=_risk_period(entries),
        insight=insight,
    )


@app.get("/profile")
async def get_profile(user_id: str = "default"):
    if not agent:
        raise HTTPException(503, "Agent not ready")
    return agent.get_profile(user_id)


@app.post("/profile")
async def update_profile(update: ProfileUpdate, user_id: str = "default"):
    if not agent:
        raise HTTPException(503, "Agent not ready")
    updates = {k: v for k, v in update.model_dump().items() if v is not None}
    return agent.update_profile(user_id, updates)


@app.get("/memory")
async def get_memory(user_id: str = "default"):
    if not agent:
        raise HTTPException(503, "Agent not ready")
    return {"facts": agent.get_memory(user_id)}


@app.post("/memory/clear")
async def clear_memory(user_id: str = "default"):
    if not agent:
        raise HTTPException(503, "Agent not ready")
    agent.clear_memory(user_id)
    return {"status": "cleared"}


# --- v1 static analysis helpers ---

def _insight(avg_intensity, top_triggers, best_tools, high_intensity) -> str:
    parts = []
    if avg_intensity < 4:
        parts.append("Средняя интенсивность тяги невысокая — это хороший знак.")
    elif avg_intensity < 7:
        parts.append("Средняя интенсивность умеренная. Обрати внимание на триггеры.")
    else:
        parts.append("Средняя интенсивность высокая. Возможно, стоит усилить поддержку.")
    if top_triggers:
        parts.append(f"Основной триггер: «{top_triggers[0][0]}» — {top_triggers[0][1]} раз(а).")
    if best_tools:
        t, h, u = best_tools[0]
        pct = round(h / u * 100) if u > 0 else 0
        parts.append(f"Самый эффективный инструмент: «{t}» — помогает в {pct}% случаев.")
    if len(high_intensity) >= 3:
        parts.append("Замечено несколько эпизодов с сильной тягой (≥7). Рекомендую заранее планировать поддержку.")
    return " ".join(parts)


def _risk_period(entries) -> str | None:
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
