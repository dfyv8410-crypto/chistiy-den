"""
Модуль обучения — AI учится на прошлых взаимодействиях.

LearningEngine:
  - Анализирует реакции пользователя на разные режимы ответа
  - Отслеживает эффективность предложенных инструментов
  - Выявляет паттерны триггеров и успешных интервенций
  - Хранит данные в JSON-файлах (data/learning/{user_id}.json)
"""

import json
import os
import time
from typing import Optional, List

LEARNING_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data/learning")

POSITIVE_KEYWORDS = [
    "спасибо", "помогло", "помогает", "благодарю", "отлично", "здорово",
    "супер", "классно", "замечательно", "прекрасно", "отличная идея",
    "хорошая мысль", "помог", "помогла", "помогли", "thanks", "thank you",
    "great", "helpful", "лучше", "полегчало", "отпустило", "сработало",
    "ты прав", "ты права", "верно", "действительно", "да, так и есть",
    "и правда", "действительно помогло", "в точку", "ты меня понимаешь",
    "приятно", "рад", "рада", "успокоился", "успокоилась", "стало легче",
]

NEGATIVE_KEYWORDS = [
    "не помогло", "бесполезно", "ужасно", "отстань", "зря", "не то",
    "не работает", "не помогает", "ерунда", "глупость", "чушь",
    "нет", "не хочу", "не надо", "оставь", "прекрати", "забудь",
    "бесит", "раздражает", "достал", "достала", "надоел", "надоела",
    "не понимаешь", "не в тему", "мимо", "не то что нужно",
    "всё равно", "плевать", "всё бесполезно", "хуже", "стало хуже",
    "не лезь", "отвянь", "отвали", "закройся",
]

NEUTRAL_KEYWORDS = [
    "ладно", "ок", "ok", "понятно", "ясно", "ага", "ну",
    "хорошо", "посмотрим", "может быть", "наверное", "попробую",
    "что дальше", "расскажи ещё", "а что ещё", "думаю",
]


def _classify_reaction(text: str) -> str:
    """Классифицирует реакцию пользователя как positive/neutral/negative.

    Использует keyword-matching по русскоязычным ключевым словам.
    """
    if not text or not text.strip():
        return "neutral"

    text_lower = text.lower().strip()

    # Негативные проверяем первыми (они приоритетнее)
    for kw in NEGATIVE_KEYWORDS:
        if kw in text_lower:
            return "negative"

    for kw in POSITIVE_KEYWORDS:
        if kw in text_lower:
            return "positive"

    for kw in NEUTRAL_KEYWORDS:
        if kw in text_lower:
            return "neutral"

    # Эмодзи-эвристики
    positive_emojis = {"😊", "🙏", "❤️", "👍", "🔥", "💪", "✨", "🎉", "😌", "🥰"}
    negative_emojis = {"😠", "😡", "💔", "👎", "😤", "😭", "😞", "😒"}

    if any(e in text for e in positive_emojis):
        return "positive"
    if any(e in text for e in negative_emojis):
        return "negative"

    return "neutral"


def _default_profile() -> dict:
    """Возвращает профиль обучения по умолчанию."""
    return {
        "response_outcomes": {},
        "tool_effectiveness": {},
        "trigger_patterns": {},
        "successful_interventions": [],
        "failed_approaches": [],
        "user_vocabulary": [],
        "learning_version": 1,
    }


class LearningEngine:
    """Двигатель обучения — анализирует историю взаимодействий и выдаёт инсайты.

    Хранит per-user данные в JSON-файлах по пути data/learning/{user_id}.json.
    """

    def __init__(self, data_dir: str = None):
        self._data_dir = data_dir or LEARNING_DIR
        os.makedirs(self._data_dir, exist_ok=True)

    # ── загрузка / сохранение ──────────────────────────────────────

    def _user_path(self, user_id: str) -> str:
        return os.path.join(self._data_dir, f"{user_id}.json")

    def _load(self, user_id: str) -> dict:
        path = self._user_path(user_id)
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
                if "learning_version" not in data:
                    data["learning_version"] = 1
                data.setdefault("user_vocabulary", [])
                return data
        except (FileNotFoundError, json.JSONDecodeError):
            return _default_profile()

    def _save(self, user_id: str, data: dict):
        path = self._user_path(user_id)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    # ── запись исхода ────────────────────────────────────────────

    def record_outcome(self, user_id: str, response_mode: str, user_reaction: str):
        """Анализирует реакцию пользователя и обновляет скоринг response_outcomes.

        Args:
            user_id:         ID пользователя.
            response_mode:   Один из режимов (comfort, guidance, action, …).
            user_reaction:   Текст реакции пользователя.
        """
        data = self._load(user_id)
        outcomes = data.setdefault("response_outcomes", {})
        mode_data = outcomes.setdefault(response_mode, {"attempts": 0, "positive": 0, "score": 0.5})

        classification = _classify_reaction(user_reaction)
        mode_data["attempts"] += 1

        if classification == "positive":
            mode_data["positive"] += 1
        elif classification == "negative":
            mode_data["positive"] -= 0.5  # штраф

        # Обновляем скользящий счёт (0.0–1.0)
        positive_ratio = mode_data["positive"] / mode_data["attempts"]
        mode_data["score"] = max(0.0, min(1.0, positive_ratio))

        # Сохраняем успешные/неудачные интервенции
        if classification == "positive":
            data["successful_interventions"].append({
                "mode": response_mode,
                "reaction": user_reaction,
                "classification": "positive",
                "date": time.time(),
            })
        elif classification == "negative":
            data["failed_approaches"].append({
                "mode": response_mode,
                "reaction": user_reaction,
                "classification": "negative",
                "date": time.time(),
            })

        self._save(user_id, data)

    # ── лучший режим ──────────────────────────────────────────────

    def get_best_response_mode(self, user_id: str, emotion_state: str) -> str:
        """Возвращает режим ответа, который исторически лучше всего работал
        для данного пользователя в указанном эмоциональном состоянии.

        Args:
            user_id:        ID пользователя.
            emotion_state:  Эмоция (sadness, anxiety, anger, …).

        Returns:
            Название режима (comfort, guidance, action, …).
        """
        data = self._load(user_id)
        outcomes = data.get("response_outcomes", {})

        if not outcomes:
            return self._default_mode_for_emotion(emotion_state)

        # Сортируем режимы по score (убывание)
        sorted_modes = sorted(
            outcomes.items(),
            key=lambda x: x[1].get("score", 0.5),
            reverse=True,
        )

        if sorted_modes:
            return sorted_modes[0][0]

        return self._default_mode_for_emotion(emotion_state)

    @staticmethod
    def _default_mode_for_emotion(emotion: str) -> str:
        """Возвращает режим по умолчанию для эмоции, если данных ещё нет."""
        mapping = {
            "sadness": "comfort",
            "fear": "comfort",
            "shame": "validation",
            "guilt": "validation",
            "loneliness": "connection",
            "anxiety": "guidance",
            "anger": "action",
            "craving": "action",
            "joy": "celebration",
            "gratitude": "celebration",
            "hope": "celebration",
        }
        return mapping.get(emotion, "listening")

    # ── эффективные инструменты ───────────────────────────────────

    def get_effective_tools_for_user(
        self, user_id: str, trigger: Optional[str] = None
    ) -> List[str]:
        """Возвращает инструменты, отсортированные по эффективности.

        Args:
            user_id: ID пользователя.
            trigger: Опциональный триггер для фильтрации.

        Returns:
            Список названий инструментов от наиболее к наименее эффективному.
        """
        data = self._load(user_id)
        tools = data.get("tool_effectiveness", {})

        if not tools:
            return []

        # Сортируем: % helped > 0.5, затем по количеству helped, затем suggested
        def sort_key(item):
            name, meta = item
            # Отсекаем инструменты с 0 suggested
            if meta.get("suggested", 0) == 0:
                return (0, 0, 0)
            ratio = meta.get("helped", 0) / meta["suggested"]
            return (1 if ratio >= 0.5 else 0, meta.get("helped", 0), meta["suggested"])

        sorted_tools = sorted(tools.items(), key=sort_key, reverse=True)
        return [name for name, _ in sorted_tools]

    def record_tool_suggestion(self, user_id: str, tool_name: str):
        """Фиксирует, что инструмент был предложен пользователю.

        Args:
            user_id:   ID пользователя.
            tool_name: Название инструмента.
        """
        data = self._load(user_id)
        tools = data.setdefault("tool_effectiveness", {})
        meta = tools.setdefault(tool_name, {"suggested": 0, "helped": 0})
        meta["suggested"] += 1
        meta["last_suggested"] = time.time()
        self._save(user_id, data)

    def record_tool_outcome(self, user_id: str, tool_name: str, helped: bool):
        """Фиксирует, помог ли предложенный инструмент.

        Args:
            user_id:   ID пользователя.
            tool_name: Название инструмента.
            helped:    True если помогло, False если нет.
        """
        data = self._load(user_id)
        tools = data.setdefault("tool_effectiveness", {})
        meta = tools.setdefault(tool_name, {"suggested": 0, "helped": 0})
        if helped:
            meta["helped"] += 1
        self._save(user_id, data)

    # ── инсайты ──────────────────────────────────────────────────

    def get_insights(self, user_id: str) -> List[str]:
        """Генерирует человекочитаемые инсайты на основе накопленных данных.

        Args:
            user_id: ID пользователя.

        Returns:
            Список строк-инсайтов на русском языке.
        """
        data = self._load(user_id)
        insights = []

        # Инсайты о режимах ответа
        outcomes = data.get("response_outcomes", {})
        if outcomes:
            best_mode = max(outcomes.items(), key=lambda x: x[1].get("score", 0))
            best_name, best_meta = best_mode
            attempts = best_meta.get("attempts", 0)
            positive = best_meta.get("positive", 0)
            if attempts > 0:
                insights.append(
                    f"Режим «{best_name}» сработал положительно в {positive} из {attempts} случаев."
                )

            worst_mode = min(outcomes.items(), key=lambda x: x[1].get("score", 1))
            worst_name, worst_meta = worst_mode
            if worst_meta.get("attempts", 0) > 1 and worst_meta.get("score", 1) < 0.3:
                insights.append(
                    f"Режим «{worst_name}» оказался неэффективным "
                    f"(оценка {worst_meta.get('score', 0):.1f})."
                )

        # Инсайты об инструментах
        tools = data.get("tool_effectiveness", {})
        effective_tools = [
            (name, meta)
            for name, meta in tools.items()
            if meta.get("suggested", 0) > 0
        ]
        if effective_tools:
            best_tool = max(
                effective_tools,
                key=lambda x: x[1].get("helped", 0) / x[1]["suggested"],
            )
            bt_name, bt_meta = best_tool
            bt_ratio = round(bt_meta["helped"] / bt_meta["suggested"] * 100)
            insights.append(
                f"Самый эффективный инструмент: «{bt_name}» — "
                f"помогает в {bt_ratio}% случаев ({bt_meta['helped']} из {bt_meta['suggested']})."
            )

        # Инсайты о триггерах
        patterns = data.get("trigger_patterns", {})
        if patterns:
            top_trigger = max(patterns.items(), key=lambda x: x[1].get("frequency", 0))
            trig_name, trig_meta = top_trigger
            insights.append(
                f"Самый частый триггер: «{trig_name}» "
                f"(встречался {trig_meta.get('frequency', 0)} раз(а))."
            )

        if not insights:
            insights.append("Пока недостаточно данных для инсайтов. Продолжайте взаимодействие.")

        return insights

    # ── управление триггерами ─────────────────────────────────────

    def record_trigger(
        self,
        user_id: str,
        trigger: str,
        intensity: float,
        time_of_day: Optional[str] = None,
    ):
        """Записывает эпизод триггера для выявления паттернов.

        Args:
            user_id:    ID пользователя.
            trigger:    Ключевое слово-триггер.
            intensity:  Интенсивность (0.0–1.0).
            time_of_day: Время суток (утро, день, вечер, ночь).
        """
        data = self._load(user_id)
        patterns = data.setdefault("trigger_patterns", {})

        if trigger not in patterns:
            patterns[trigger] = {
                "times": [],
                "intensity": 0.0,
                "frequency": 0,
            }

        meta = patterns[trigger]
        meta["frequency"] += 1
        if time_of_day and time_of_day not in meta["times"]:
            meta["times"].append(time_of_day)
        # Скользящая средняя интенсивность
        prev = meta["intensity"]
        meta["intensity"] = round((prev * (meta["frequency"] - 1) + intensity) / meta["frequency"], 2)

        self._save(user_id, data)

    # ── словарь пользователя ──────────────────────────────────────

    def record_vocabulary(self, user_id: str, words: List[str]):
        """Пополняет словарь характерных слов/фраз пользователя.

        Args:
            user_id: ID пользователя.
            words:   Список слов или фраз для добавления.
        """
        data = self._load(user_id)
        vocab = data.setdefault("user_vocabulary", [])
        existing = set(vocab)
        for w in words:
            w_clean = w.strip().lower()
            if w_clean and w_clean not in existing:
                vocab.append(w_clean)
                existing.add(w_clean)
        # Ограничиваем размер словаря
        if len(vocab) > 200:
            data["user_vocabulary"] = vocab[-200:]
        self._save(user_id, data)

    # ── удаление данных ───────────────────────────────────────────

    def delete_profile(self, user_id: str):
        """Удаляет все накопленные данные обучения для пользователя."""
        path = self._user_path(user_id)
        try:
            os.remove(path)
        except FileNotFoundError:
            pass
