"""
AI Recovery Agent — основной класс AI-компаньона.
Объединяет LLM, память, анализ личности и кризис-режим.
"""

import json
import logging
from datetime import datetime
from typing import Optional

from ..services.llm_service import LLMService
from ..memory.memory_store import ShortMemory, LongMemory
from ..models.schemas import ChatRequest, ChatResponse

logger = logging.getLogger(__name__)

CRISIS_KEYWORDS = [
    "сорвус", "срываюсь", "хочу употребить", "не могу больше",
    "всё кончено", "плохо", "страшно", "помогите",
    "не справляюсь", "ужасно", "тошно",
]


class RecoveryAgent:
    def __init__(self, llm: Optional[LLMService] = None,
                 memory: Optional[LongMemory] = None):
        self.llm = llm or LLMService()
        self.memory = memory or LongMemory()
        self.short_memories: dict[str, ShortMemory] = {}

    def _get_short(self, user_id: str) -> ShortMemory:
        if user_id not in self.short_memories:
            self.short_memories[user_id] = ShortMemory(user_id=user_id)
        return self.short_memories[user_id]

    def _detect_crisis(self, text: str) -> bool:
        text_lower = text.lower()
        return any(kw in text_lower for kw in CRISIS_KEYWORDS)

    def _build_context(self, user_id: str, extra: Optional[dict] = None) -> str:
        """Собирает контекст из памяти + данных пользователя."""
        profile = self.memory.get_profile(user_id)
        facts = self.memory.get_facts(user_id, limit=5)
        parts = []
        if profile.get("ai_name"):
            parts.append(f"Тебя зовут «{profile['ai_name']}».")
        if profile.get("style"):
            parts.append(f"Стиль общения: {profile['style']}.")
        if facts:
            fact_texts = [f"- {f['content']}" for f in facts]
            parts.append("Что ты знаешь о пользователе:\n" + "\n".join(fact_texts))
        if extra:
            diary = extra.get("diary", [])
            if diary:
                lines = []
                for e in diary:
                    parts_list = [f"  - Ситуация: {e.get('situation','?')}", f"  - Интенсивность: {e.get('intensity','?')}/10"]
                    if e.get('trigger'): parts_list.append(f"  - Триггер: {e['trigger']}")
                    if e.get('emotions'): parts_list.append(f"  - Эмоции: {', '.join(e['emotions'])}")
                    if e.get('tools'): parts_list.append(f"  - Помогло: {', '.join(e['tools'])}")
                    if e.get('summary'): parts_list.append(f"  - Вывод: {e['summary']}")
                    lines.append(f"📅 {e.get('date','?')}:\n" + "\n".join(parts_list))
                parts.append("Недавние записи дневника пользователя (используй для контекста, НЕ цитируй дословно):\n" + "\n\n".join(lines[-3:]))
            prog = extra.get("progress", {})
            if prog.get("days") is not None:
                parts.append(f"Прогресс: {prog['days']} дней чистоты, медаль — {prog.get('medal','—')}.")
            steps = extra.get("steps", 0)
            if steps:
                parts.append(f"Выполнено шагов: {steps}.")
            events = extra.get("recentEvents", [])
            if events:
                ev_lines = [f"  - {e.get('type','?')} (интенсивность: {e.get('intensity','?')})" for e in events[-2:]]
                parts.append("Последние события:\n" + "\n".join(ev_lines))
        return "\n".join(parts)

    def chat(self, req: ChatRequest) -> ChatResponse:
        user_id = req.user_id
        text = req.message.strip()
        if not text:
            return ChatResponse(reply="Расскажи, что происходит. Я здесь, чтобы слушать.")

        short = self._get_short(user_id)
        for h in req.history:
            if h.get("role") in ("user", "assistant"):
                short.add(h["role"], h["content"])

        crisis = self._detect_crisis(text)
        sentiment = self.llm.analyze_sentiment(text)
        crisis = crisis or sentiment.get("crisis", False)

        profile = self.memory.get_profile(user_id)
        style = profile.get("style", "balanced")
        system = self.llm.generate_personality(style)

        context = self._build_context(user_id, extra=req.context)
        if context:
            system = f"{system}\n\nКонтекст:\n{context}"

        if crisis:
            system += (
                "\n\nВАЖНО: Пользователь в кризисе. "
                "Режим «Помощь сейчас». Действуй по алгоритму:\n"
                "1. Стабилизация — предложи сделать паузу и подышать.\n"
                "2. Проверь состояние коротким вопросом.\n"
                "3. Предложи одно безопасное действие.\n"
                "4. Если нужно — напомни о SOS, звонке наставнику, 112.\n"
                "Не пиши длинные ответы. Максимум 3 коротких предложения."
            )

        short.add("user", text)
        messages = short.get_context()

        try:
            reply = self.llm.chat(
                messages=messages,
                system=system,
                temperature=0.7 if not crisis else 0.5,
                max_tokens=300,
            )
        except Exception as e:
            logger.error(f"LLM error: {e}")
            reply = (
                "Извини, я временно не могу ответить. "
                "Попробуй ещё раз через минуту. "
                "Если тебе нужна срочная помощь — позвони наставнику или 112."
            )

        short.add("assistant", reply)

        # Сохраняем важные факты в долгую память
        self._learn_from_conversation(user_id, text, sentiment)

        return ChatResponse(
            reply=reply,
            emotion=sentiment.get("emotion", "neutral"),
            crisis=crisis,
        )

    def _learn_from_conversation(self, user_id: str, text: str, sentiment: dict):
        """Извлекает факты из сообщения для долгой памяти."""
        intensity = sentiment.get("intensity", 0)
        emotion = sentiment.get("emotion", "neutral")

        if intensity >= 7 and emotion in ("craving", "anxiety", "anger", "sadness"):
            self.memory.add_fact(user_id, {
                "type": "struggle",
                "content": f"Сильная {emotion} (уровень {intensity})",
                "importance": min(intensity, 10),
            })

        # Извлекаем упоминания инструментов
        tools = ["молитв", "звонк", "групп", "дыха", "спорт", "прогулк",
                 "разговор", "медита", "чтени", "дневник", "шаг"]
        for tool in tools:
            if tool in text.lower():
                self.memory.add_fact(user_id, {
                    "type": "tool",
                    "content": f"Использует инструмент: {tool}",
                    "importance": 3,
                })

    def update_profile(self, user_id: str, updates: dict) -> dict:
        profile = self.memory.get_profile(user_id)
        profile.update(updates)
        self.memory.save_profile(user_id, profile)
        if updates.get("ai_name"):
            self.memory.add_fact(user_id, {
                "type": "preference",
                "content": f"Назвал помощника: {updates['ai_name']}",
                "importance": 5,
            })
        return profile

    def get_profile(self, user_id: str) -> dict:
        return self.memory.get_profile(user_id)

    def get_memory(self, user_id: str) -> list[dict]:
        return self.memory.get_facts(user_id)

    def clear_memory(self, user_id: str):
        self.memory.delete_facts(user_id)
        if user_id in self.short_memories:
            self.short_memories[user_id].clear()
