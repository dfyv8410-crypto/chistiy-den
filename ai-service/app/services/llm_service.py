"""
LLM Service — абстракция над Ollama.
Позволяет переключить модель без изменения кода.
"""

import json
import logging
from typing import Optional

logger = logging.getLogger(__name__)


class LLMService:
    def __init__(self, model: str = "llama3.2:1b", base_url: str = "http://localhost:11434"):
        self.model = model
        self.base_url = base_url.rstrip("/")
        self._client = None

    @property
    def client(self):
        if self._client is None:
            import ollama
            self._client = ollama.Client(host=self.base_url)
        return self._client

    def chat(self, messages: list[dict], system: Optional[str] = None,
             temperature: float = 0.7, max_tokens: int = 512) -> str:
        """Send a chat request to Ollama. Returns the reply text."""
        full_messages = []
        if system:
            full_messages.append({"role": "system", "content": system})
        full_messages.extend(messages)

        try:
            response = self.client.chat(
                model=self.model,
                messages=full_messages,
                options={
                    "temperature": temperature,
                    "num_predict": max_tokens,
                }
            )
            return response["message"]["content"].strip()
        except Exception as e:
            logger.error(f"Ollama chat error: {e}")
            raise

    def analyze_sentiment(self, text: str) -> dict:
        """Определяет эмоциональную окраску сообщения."""
        system = (
            "Определи эмоцию в сообщении пользователя. "
            "Ответь строго JSON: {\"emotion\": \"...\", \"intensity\": 0-10, \"crisis\": false}\n"
            "Варианты emotion: neutral, anxiety, anger, sadness, craving, hope, fear, joy, loneliness, tired"
        )
        try:
            reply = self.chat(
                messages=[{"role": "user", "content": text}],
                system=system,
                temperature=0.1,
                max_tokens=100,
            )
            return json.loads(reply)
        except Exception:
            return {"emotion": "neutral", "intensity": 0, "crisis": False}

    def generate_personality(self, style: str = "balanced") -> str:
        """System prompt для личности AI."""
        base = (
            "Ты — AI-помощник восстановления для людей, работающих по программе 12 шагов. "
            "Твой тон: спокойный, мудрый, терпеливый, поддерживающий, ненавязчивый. "
            "Ты НЕ врач, НЕ ставишь диагнозы. "
            "Используй формулировки: «Я замечаю...», «Возможно...», «Стоит обратить внимание...». "
            "Если пользователь говорит о сильной тяге или кризисе — переключись в режим «Помощь сейчас»: "
            "1) стабилизация (дышать, пауза), 2) проверка состояния, 3) уточнение, "
            "4) безопасное действие, 5) поддержка. "
            "При опасных состояниях предлагай обратиться к близким, специалистам или экстренным службам. "
            "Отвечай кратко (2-4 предложения), задавай уточняющие вопросы. "
            "Не используй списки и маркдаун."
        )
        style_notes = {
            "gentle": "Будь особенно мягким и заботливым. Используй тёплые слова.",
            "direct": "Будь прямым и чётким. Минимум украшений, максимум пользы.",
            "coach": "Будь как наставник: поддерживай, но подталкивай к действию. Добавляй вопросы для размышления.",
        }
        extra = style_notes.get(style, "")
        return f"{base}\n{extra}".strip()
