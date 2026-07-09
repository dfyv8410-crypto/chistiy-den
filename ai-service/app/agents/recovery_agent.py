"""
AI Recovery Agent v2 — эволюционировавший AI-компаньон.
Объединяет личность, эмоциональный интеллект, память отношений,
обучение, безопасность и живое присутствие.
"""

import json
import logging
from datetime import datetime
from typing import Optional

from ..services.llm_service import LLMService
from ..memory.memory_store import ShortMemory, LongMemory
from ..personality.core import (
    generate_system_prompt,
    select_style_for_situation,
    CharacterProfile,
)
from ..emotion.analyzer import EmotionAnalyzer, EmotionState
from ..relationship.memory import RelationshipMemory
from ..learning.engine import LearningEngine
from ..safety.guardrails import SafetyGuardrails
from ..notifications.engine import NotificationEngine
from ..models.schemas import ChatRequest, ChatResponse

logger = logging.getLogger(__name__)


class RecoveryAgent:
    def __init__(
        self,
        llm: Optional[LLMService] = None,
        memory: Optional[LongMemory] = None,
    ):
        self.llm = llm or LLMService()
        self.memory = memory or LongMemory()
        self.short_memories: dict[str, ShortMemory] = {}
        self.emotion = EmotionAnalyzer()
        self.relationship = RelationshipMemory()
        self.learning = LearningEngine()
        self.safety = SafetyGuardrails()
        self.notifications = NotificationEngine()

    def _get_short(self, user_id: str) -> ShortMemory:
        if user_id not in self.short_memories:
            self.short_memories[user_id] = ShortMemory(user_id=user_id)
        return self.short_memories[user_id]

    def _build_context(self, user_id: str, extra: Optional[dict] = None) -> str:
        parts = []
        facts = self.memory.get_facts(user_id, limit=5)
        if facts:
            fact_texts = [f"- {f['content']}" for f in facts]
            parts.append("Что я знаю об этом человеке:\n" + "\n".join(fact_texts))
        rel = self.relationship.get_relationship_summary(user_id)
        if rel:
            parts.append(self.relationship._format_for_prompt(rel))
        if extra:
            diary = extra.get("diary", [])
            if diary:
                lines = []
                for e in diary:
                    pl = [f"  - Ситуация: {e.get('situation','?')}", f"  - Интенсивность: {e.get('intensity','?')}/10"]
                    if e.get('trigger'): pl.append(f"  - Триггер: {e['trigger']}")
                    if e.get('emotions'): pl.append(f"  - Эмоции: {', '.join(e['emotions'])}")
                    if e.get('tools'): pl.append(f"  - Помогло: {', '.join(e['tools'])}")
                    if e.get('summary'): pl.append(f"  - Вывод: {e['summary']}")
                    lines.append(f"📅 {e.get('date','?')}:\n" + "\n".join(pl))
                parts.append("Недавние записи дневника (используй для контекста, НЕ цитируй):\n" + "\n\n".join(lines[-3:]))
            prog = extra.get("progress", {})
            if prog.get("days") is not None:
                d = prog['days']
                medal_hint = ""
                if d > 0 and d < 30:
                    medal_hint = f"Скоро первая медаль! Осталось {30-d} дней."
                elif d < 60:
                    medal_hint = f"До оранжевой медали осталось {60-d} дней."
                parts.append(f"Прогресс: {d} дней чистоты, медаль — {prog.get('medal','—')}. {medal_hint}")
            steps = extra.get("steps", 0)
            if steps:
                parts.append(f"Выполнено шагов: {steps}.")
            events = extra.get("recentEvents", [])
            if events:
                ev_lines = [f"  - {e.get('type','?')} (интенсивность: {e.get('intensity','?')})" for e in events[-2:]]
                parts.append("Последние события:\n" + "\n".join(ev_lines))
        insights = self.learning.get_insights(user_id)
        if insights:
            parts.append("Мои наблюдения:\n" + "\n".join(f"- {i}" for i in insights[:3]))
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

        # 1. Эмоциональный анализ
        emotion_state = self.emotion.analyze(text, context=req.context)
        response_mode = self.emotion.select_response_mode(emotion_state)

        # 2. Кризис-детект (усиленный)
        crisis_check = self.safety.check_crisis(text, req.context or {})
        crisis = crisis_check["crisis"] or emotion_state.crisis

        # 3. Выбор стиля личности под ситуацию
        profile = self.memory.get_profile(user_id)
        user_style = profile.get("style", "balanced")
        active_style = select_style_for_situation(text, user_style)
        if crisis:
            active_style = "direct"

        # 4. Сбор контекста
        context = self._build_context(user_id, extra=req.context)

        # 5. Генерация системного промпта (личность + ценности)
        system = generate_system_prompt(
            active_style,
            context={
                "user_name": profile.get("ai_name"),
                "crisis": crisis,
                "emotion": emotion_state.emotion,
                "response_mode": response_mode,
            },
        )
        if context:
            system += f"\n\nКонтекст:\n{context}"

        # 6. Используем обучение — какой режим ответа лучше для этого пользователя
        best_mode = self.learning.get_best_response_mode(user_id, emotion_state)
        if best_mode and best_mode != response_mode:
            response_mode = best_mode

        # 7. Добавляем режим ответа в system prompt
        mode_instructions = {
            "comfort": "Сейчас человеку нужна поддержка и тепло. Будь мягким, принимающим.",
            "guidance": "Человеку нужно направление. Предложи 1-2 конкретных шага.",
            "listening": "Просто слушай. Не давай советов, отражай чувства коротко.",
            "action": "Предложи конкретное действие. Спроси, готов ли человек попробовать.",
            "hope": "Напомни о прошлых успехах. Покажи, что у человека есть силы.",
            "connection": "Напомни, что человек не один. Спроси о поддержке рядом.",
            "validation": "Признай, что чувства нормальны. Не пытайся «исправить» эмоции.",
            "silence": "Ответь коротко. Оставь пространство для размышления.",
            "celebration": "Порадуйся вместе с человеком. Признай его усилия.",
        }
        instruction = mode_instructions.get(response_mode, "")
        if instruction:
            system += f"\n\nРежим ответа: {instruction}"

        if crisis:
            crisis_type = crisis_check.get("type", "relapse")
            system += (
                f"\n\nКРИЗИС: тип «{crisis_type}». "
                "Действуй по протоколу безопасности:\n"
                "1. Стабилизация — предложи паузу и дыхание.\n"
                "2. Проверь состояние коротким вопросом.\n"
                "3. Предложи одно безопасное действие.\n"
                "4. Обязательно напомни о SOS, звонке наставнику, 112.\n"
                "Максимум 3 коротких предложения. Без списков."
            )

        short.add("user", text)
        messages = short.get_context()

        # 8. Генерация ответа через LLM
        try:
            temp = 0.7
            if crisis:
                temp = 0.5
            elif response_mode in ("comfort", "validation"):
                temp = 0.8
            reply = self.llm.chat(
                messages=messages,
                system=system,
                temperature=temp,
                max_tokens=300,
            )
        except Exception as e:
            logger.error(f"LLM error: {e}")
            reply = (
                "Извини, я временно не могу ответить. "
                "Попробуй ещё раз через минуту. "
                "Если тебе нужна срочная помощь — позвони наставнику или 112."
            )

        # 9. Проверка безопасности ответа
        safety_check = self.safety.check_response(reply, {"crisis": crisis, "emotion": emotion_state.emotion})
        if not safety_check["safe"] and safety_check["modified_response"]:
            reply = safety_check["modified_response"]
            logger.warning(f"Response modified for safety: {safety_check['issues']}")

        short.add("assistant", reply)

        # 10. Обучение на взаимодействии
        self._learn_from_conversation(user_id, text, emotion_state)
        self.learning.record_outcome(user_id, response_mode, text)
        self.relationship.record_interaction(user_id, text, emotion_state, response_mode)

        return ChatResponse(
            reply=reply,
            emotion=emotion_state.emotion,
            crisis=crisis,
        )

    def _learn_from_conversation(self, user_id: str, text: str, emotion: EmotionState):
        if emotion.intensity >= 0.7 and emotion.emotion in ("craving", "anxiety", "anger", "sadness"):
            self.memory.add_fact(user_id, {
                "type": "struggle",
                "content": f"Сильная {emotion.emotion} (уровень {emotion.intensity:.0%})",
                "importance": min(int(emotion.intensity * 10), 10),
            })
            self.relationship.record_crisis(user_id, int(emotion.intensity * 10), "active")
        tools = ["молитв", "звонк", "групп", "дыха", "спорт", "прогулк",
                 "разговор", "медита", "чтени", "дневник", "шаг"]
        for tool in tools:
            if tool in text.lower():
                self.memory.add_fact(user_id, {
                    "type": "tool",
                    "content": f"Использует инструмент: {tool}",
                    "importance": 3,
                })
                self.learning.record_tool_suggestion(user_id, tool)
                self.learning.record_tool_outcome(user_id, tool, True)

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
            self.relationship.record_achievement(user_id, "named_ai", f"Дал имя: {updates['ai_name']}")
        return profile

    def get_profile(self, user_id: str) -> dict:
        return self.memory.get_profile(user_id)

    def get_memory(self, user_id: str) -> list[dict]:
        return self.memory.get_facts(user_id)

    def clear_memory(self, user_id: str):
        self.memory.delete_facts(user_id)
        if user_id in self.short_memories:
            self.short_memories[user_id].clear()

    # --- Новые методы для компаньона ---

    def generate_notification(self, notif_type: str, user_id: str, context: dict = None) -> Optional[str]:
        if not self.notifications.should_notify(notif_type, user_id, context or {}):
            return None
        return self.notifications.generate_push_text(notif_type, user_id, context or {})

    def get_daily_checkin(self, user_id: str, context: dict = None) -> dict:
        hour = datetime.now().hour
        if hour < 12:
            text = self.notifications.generate_morning(user_id, context or {})
        elif hour < 18:
            text = "Как проходит твой день? Всё в порядке?"
        else:
            text = self.notifications.generate_evening(user_id, context or {})
        return {"text": text, "type": "daily"}

    def get_relationship_status(self, user_id: str) -> dict:
        summary = self.relationship.get_relationship_summary(user_id)
        return {
            "stage": summary.get("stage", "new"),
            "interactions": summary.get("interaction_count", 0),
            "last_interaction": summary.get("last_interaction", ""),
            "achievements": len(summary.get("achievements", [])),
            "summary": self.relationship._format_for_prompt(summary) if summary else "",
        }
