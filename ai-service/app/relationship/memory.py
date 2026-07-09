# -*- coding: utf-8 -*-
"""
Модуль памяти отношений.

RelationshipMemory запоминает не просто факты, а контекст общения:
паттерны, эффективные инструменты, историю взаимодействия.
Хранится в JSON-файлах: data/relationships/{user_id}.json
"""

import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Union

DATA_DIR = Path("data/relationships")


class RelationshipMemory:
    """
    Память отношений — запоминает контекст и историю взаимодействия с пользователем.

    Хранит эмоционально значимые события, предпочтения в общении,
    эффективные инструменты и паттерны поведения.
    """

    _defaults: dict = {
        "important_events": [],
        "achievements": [],
        "struggles": [],
        "effective_tools": {},
        "triggers": {},
        "communication_preferences": {
            "style": None,
            "preferred_time": None,
            "topics": [],
        },
        "relationship_stage": "new",
        "interaction_count": 0,
        "last_interaction": None,
        "meaningful_moments": [],
    }

    def __init__(self, data_dir: Union[str, Path] = DATA_DIR):
        self.data_dir = Path(data_dir)
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self._cache = {}  # type: Dict[str, dict]

    def _user_path(self, user_id: str) -> Path:
        return self.data_dir / f"{user_id}.json"

    def _load(self, user_id: str) -> dict:
        if user_id in self._cache:
            return self._cache[user_id]
        path = self._user_path(user_id)
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            data = dict(self._defaults)
        self._cache[user_id] = data
        return data

    def _save(self, user_id: str):
        path = self._user_path(user_id)
        path.parent.mkdir(parents=True, exist_ok=True)
        data = self._cache.get(user_id, dict(self._defaults))
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    # ── запись взаимодействий ──────────────────────────────────────

    def record_interaction(
        self,
        user_id: str,
        text: str,
        emotion_state: Optional[str] = None,
        response_mode: Optional[str] = None,
    ):
        """
        Записать каждое взаимодействие с эмоциональным контекстом.

        Args:
            user_id: идентификатор пользователя
            text: что сказал пользователь
            emotion_state: эмоциональное состояние (например, "тревога", "злость")
            response_mode: выбранный режим ответа (например, "supportive", "direct")
        """
        data = self._load(user_id)
        data["interaction_count"] += 1
        data["last_interaction"] = datetime.now(timezone.utc).isoformat()

        if data["interaction_count"] == 1:
            data["important_events"].append({
                "type": "first_interaction",
                "description": "Первый разговор",
                "timestamp": data["last_interaction"],
                "text_preview": text[:200],
            })
            data["relationship_stage"] = "building"

        if emotion_state:
            event = {
                "type": "interaction",
                "emotion": emotion_state,
                "response_mode": response_mode,
                "timestamp": data["last_interaction"],
                "text_preview": text[:200],
            }
            data["meaningful_moments"].append(event)
            if len(data["meaningful_moments"]) > 100:
                data["meaningful_moments"] = data["meaningful_moments"][-100:]

        self._save(user_id)

    def record_achievement(self, user_id: str, achievement_type: str, description: str):
        """
        Записать достижение или веху.

        Args:
            user_id: идентификатор пользователя
            achievement_type: тип достижения (например, "step_completed", "sober_days")
            description: описание достижения
        """
        data = self._load(user_id)
        entry = {
            "type": achievement_type,
            "description": description,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        data["achievements"].append(entry)
        if len(data["achievements"]) > 200:
            data["achievements"] = data["achievements"][-200:]

        if len(data["achievements"]) in (1, 5, 10, 25, 50, 100):
            data["important_events"].append({
                "type": "milestone",
                "description": f"Достигнуто {len(data['achievements'])} достижений",
                "timestamp": datetime.now(timezone.utc).isoformat(),
            })

        self._save(user_id)

    def record_crisis(self, user_id: str, severity: str, resolution: Optional[str] = None):
        """
        Записать кризисный эпизод.

        Args:
            user_id: идентификатор пользователя
            severity: серьёзность ("low", "medium", "high", "critical")
            resolution: как разрешилась ситуация
        """
        data = self._load(user_id)
        entry = {
            "type": "crisis",
            "severity": severity,
            "resolution": resolution,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        data["struggles"].append(entry)
        if len(data["struggles"]) > 100:
            data["struggles"] = data["struggles"][-100:]

        data["important_events"].append({
            "type": "crisis_survived",
            "description": f"Кризис ({severity}) преодолён",
            "timestamp": datetime.now(timezone.utc).isoformat(),
        })

        if severity in ("high", "critical") and data["relationship_stage"] == "building":
            data["relationship_stage"] = "established"

        self._save(user_id)

    def record_effective_tool(self, user_id: str, tool_name: str, worked: bool):
        """
        Запомнить, какой инструмент помог (или не помог) пользователю.

        Args:
            user_id: идентификатор пользователя
            tool_name: название инструмента (например, "breathing", "journaling")
            worked: сработало ли
        """
        data = self._load(user_id)
        tools = data["effective_tools"]
        if tool_name not in tools:
            tools[tool_name] = {"attempts": 0, "successes": 0}
        tools[tool_name]["attempts"] += 1
        if worked:
            tools[tool_name]["successes"] += 1
        self._save(user_id)

    def record_trigger(self, user_id: str, trigger: str):
        """
        Запомнить триггер пользователя.

        Args:
            user_id: идентификатор пользователя
            trigger: описание триггера
        """
        data = self._load(user_id)
        triggers = data["triggers"]
        triggers[trigger] = triggers.get(trigger, 0) + 1
        self._save(user_id)

    # ── получение данных ───────────────────────────────────────────

    def get_relationship_summary(self, user_id: str) -> dict:
        """
        Получить сводку отношений для подстановки в системный промпт.
        """
        data = self._load(user_id)
        return {
            "relationship_stage": data["relationship_stage"],
            "interaction_count": data["interaction_count"],
            "last_interaction": data["last_interaction"],
            "total_achievements": len(data["achievements"]),
            "total_crises": len(data["struggles"]),
            "total_events": len(data["important_events"]),
            "effective_tools": dict(
                sorted(
                    data["effective_tools"].items(),
                    key=lambda x: -x[1]["successes"],
                )[:10]
            ),
            "triggers": dict(
                sorted(
                    data["triggers"].items(),
                    key=lambda x: -x[1],
                )[:10]
            ),
            "communication_preferences": data["communication_preferences"],
            "recent_achievements": data["achievements"][-5:],
            "recent_events": data["important_events"][-5:],
        }

    def get_patterns(self, user_id: str) -> List[str]:
        """
        Сформировать человекочитаемые паттерны на основе истории.
        """
        data = self._load(user_id)
        patterns = []

        tools = data["effective_tools"]
        for name, stats in tools.items():
            if stats["attempts"] >= 2 and stats["successes"] / stats["attempts"] >= 0.6:
                patterns.append(
                    f"В похожих ситуациях тебе помогало {name} "
                    f"(сработало в {stats['successes']} из {stats['attempts']} раз)"
                )

        triggers = data["triggers"]
        for trigger, freq in sorted(triggers.items(), key=lambda x: -x[1])[:5]:
            patterns.append(
                f"{trigger} — это твой триггер (отмечено {freq} раз)"
            )

        if data["interaction_count"] > 10 and data["relationship_stage"] == "deep":
            patterns.append(
                "Мы уже достаточно долго общаемся — я хорошо понимаю твои реакции "
                "и могу предугадывать, что тебе нужно в сложные моменты"
            )

        if len(data["achievements"]) >= 5:
            patterns.append(
                "Ты последовательно достигаешь целей — это твоя сильная сторона"
            )

        if data.get("communication_preferences", {}).get("style"):
            style = data["communication_preferences"]["style"]
            patterns.append(f"Тебе комфортнее, когда я общаюсь {style}")

        return patterns

    def get_relationship_age(self, user_id: str) -> str:
        """
        Сколько дней мы уже общаемся.
        """
        data = self._load(user_id)
        if not data["last_interaction"]:
            return "Мы ещё не начали разговор"

        events = data["important_events"]
        first = None
        for ev in events:
            if ev["type"] == "first_interaction":
                first = ev.get("timestamp")
                break

        if not first:
            return "Мы уже общаемся некоторое время"

        try:
            first_dt = datetime.fromisoformat(first)
            delta = datetime.now(timezone.utc) - first_dt
            days = max(1, delta.days)
            if days == 1:
                return "Мы общаемся уже 1 день"
            return f"Мы общаемся уже {days} дней"
        except (ValueError, TypeError):
            return "Мы уже общаемся некоторое время"

    # ── форматирование для промпта ──────────────────────────────────

    def format_for_prompt(self, summary: dict) -> str:
        """
        Превратить сводку отношений в тёплый естественный текст.

        Результат читается как фрагмент диалога, а не выгрузка данных.
        """
        parts = []
        age_str = summary.get("_age", "")
        if age_str:
            parts.append(age_str)
        else:
            parts.append("Мы уже общаемся некоторое время")

        stage_labels = {
            "new": "мы только начинаем знакомиться",
            "building": "наши отношения постепенно становятся глубже",
            "established": "между нами уже сложилось доверие",
            "deep": "я хорошо тебя знаю и чувствую твоё состояние",
        }
        stage = summary.get("relationship_stage", "new")
        parts.append(stage_labels.get(stage, ""))

        achievements = summary.get("total_achievements", 0)
        crises = summary.get("total_crises", 0)
        events = summary.get("total_events", 0)
        interactions = summary.get("interaction_count", 0)

        if achievements > 0 or crises > 0:
            fragments = []
            if achievements > 0:
                fragments.append(f"ты достиг {achievements} важных целей")
            if crises > 0:
                fragments.append(f"пережил {crises} сложных периодов")
            if events > 0:
                fragments.append(f"у нас было {events} значимых моментов")
            parts.append("За это время " + ", ".join(fragments))

        tools = summary.get("effective_tools", {})
        if tools:
            tool_list = []
            for name, stats in list(tools.items())[:5]:
                pct = round(stats["successes"] / stats["attempts"] * 100) if stats["attempts"] else 0
                tool_list.append(f"{name} (помогает в {pct}% случаев)")
            parts.append(f"В трудные моменты тебе особенно помогает: {', '.join(tool_list)}")

        triggers = summary.get("triggers", {})
        if triggers:
            trigger_list = list(triggers.keys())[:5]
            parts.append(f"Я знаю, что тебя может задеть: {', '.join(trigger_list)}")

        recent_achievements = summary.get("recent_achievements", [])
        if recent_achievements:
            last = recent_achievements[-1]
            parts.append(
                f"Недавно ты {last.get('description', 'сделал важный шаг').lower()} — "
                f"я этим горжусь"
            )

        return " ".join(part for part in parts if part).strip()

    def get_formatted_relationship(self, user_id: str) -> str:
        """
        Получить готовый текст о наших отношениях для системного промпта.
        """
        summary = self.get_relationship_summary(user_id)
        age = self.get_relationship_age(user_id)
        summary["_age"] = age
        return self.format_for_prompt(summary)

    # ── обслуживание ────────────────────────────────────────────────

    def cleanup(self, user_id: str, max_events: int = 200, max_moments: int = 100):
        """
        Обрезать историю до разумных пределов.
        """
        data = self._load(user_id)
        if len(data["important_events"]) > max_events:
            data["important_events"] = data["important_events"][-max_events:]
        if len(data["meaningful_moments"]) > max_moments:
            data["meaningful_moments"] = data["meaningful_moments"][-max_moments:]
        if len(data["achievements"]) > 200:
            data["achievements"] = data["achievements"][-200:]
        if len(data["struggles"]) > 100:
            data["struggles"] = data["struggles"][-100:]
        self._save(user_id)

    def backup(self, user_id: str, backup_dir=None):
        if backup_dir is None:
            backup_dir = "data/relationships/backups"
        """
        Создать резервную копию файла отношений.
        """
        src = self._user_path(user_id)
        if not src.exists():
            return
        backup_path = Path(backup_dir)
        backup_path.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        dst = backup_path / f"{user_id}_{timestamp}.json"
        shutil.copy2(src, dst)

    def delete_user(self, user_id: str):
        """
        Полностью удалить данные пользователя.
        """
        self._cache.pop(user_id, None)
        path = self._user_path(user_id)
        if path.exists():
            path.unlink()

    def set_communication_preference(
        self,
        user_id: str,
        style: Optional[str] = None,
        preferred_time: Optional[str] = None,
        topics=None,
    ):
        """
        Обновить предпочтения в общении.
        """
        data = self._load(user_id)
        prefs = data["communication_preferences"]
        if style:
            prefs["style"] = style
        if preferred_time:
            prefs["preferred_time"] = preferred_time
        if topics is not None:
            existing = set(prefs.get("topics", []))
            existing.update(topics)
            prefs["topics"] = sorted(existing)
        self._save(user_id)
