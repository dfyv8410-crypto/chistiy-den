"""
Notification Engine — генерация персонализированных push-уведомлений.

Модуль предоставляет:
- NotificationEngine: класс для генерации и отправки уведомлений
- Контекстные проверки: близость к里程碑, качество дня, приветствие по времени
- JSON-хранилище расписания для защиты от спама
"""

import json
import os
import random
from datetime import datetime, date, timedelta
from pathlib import Path
from typing import Optional


# ──────────────────────────────────────────────
#  Константы
# ──────────────────────────────────────────────

MILESTONES = [
    (1, "24 часа"),
    (7, "1 неделя"),
    (14, "2 недели"),
    (30, "1 месяц"),
    (60, "2 месяца"),
    (90, "3 месяца"),
    (180, "6 месяцев"),
    (365, "1 год"),
]

NOTIFICATION_SCHEDULE_PATH = os.getenv(
    "NOTIFICATION_SCHEDULE_PATH",
    str(Path(__file__).resolve().parent / "notification_schedule.json"),
)

# ──────────────────────────────────────────────
#  Шаблоны утренних приветствий
# ──────────────────────────────────────────────

MORNING_TEMPLATES = [
    "{greeting}! {milestone}Сегодня новый день, и он принадлежит тебе. "
    "Может, начнёшь с глубокого вдоха и наметишь одну маленькую цель?",

    "{greeting}! Рада видеть тебя снова. {milestone}"
    "Как насчёт того, чтобы сегодня уделить 5 минут тишине и благодарности?",

    "{greeting} — лучшее время почувствовать свежесть нового дня. {milestone}"
    "Что ты хочешь прожить сегодня особенно осознанно?",

    "{greeting}! Ты уже сделал главное — продолжил свой путь. {milestone}"
    "Попробуй сегодня сделать один маленький шаг, который приблизит тебя к покою.",

    "{greeting}. {milestone}"
    "Прежде чем окунуться в дела, сделай паузу: три глубоких вдоха "
    "и одна мысль о том, за что ты благодарен сегодня.",

    "Просыпайся! {milestone}Сегодняшний день — это чистый лист. "
    "Что бы ты хотел на нём написать?",

    "{greeting}. Знаю, что утро может быть разным, но ты здесь — "
    "и это уже победа. {milestone}Дыши. Ты справишься.",
]

# ──────────────────────────────────────────────
#  Шаблоны вечерних рефлексий
# ──────────────────────────────────────────────

EVENING_TEMPLATES = [
    "День подходит к концу. {acknowledgment} Даже если было непросто, "
    "ты это сделал. {small_win} Завтра будет новый день, а пока — выдохни.",

    "Вечер — время подвести итоги. {acknowledgment} {small_win}"
    "Перед сном подумай об одной вещи, которую ты сделал сегодня для себя.",

    "Как прошёл твой день? {acknowledgment} {small_win}"
    "Завтра попробуй сделать что-то одно, что принесёт тебе радость.",

    "Спокойного вечера. {acknowledgment} {small_win}"
    "Помни: каждый день, который ты остаёшься на своём пути, — это шаг к свободе.",

    "День прожит не зря. {acknowledgment} {small_win}"
    "Перед сном оставь все заботы — завтра ты встретишь их отдохнувшим.",

    "За окном вечереет, и это время для тишины. {acknowledgment} {small_win}"
    "Ты справился сегодня — и это главное.",
]

# ──────────────────────────────────────────────
#  Шаблоны чек-ина при отсутствии
# ──────────────────────────────────────────────

ABSENCE_TEMPLATES = [
    "Привет! Давно не виделись. Просто хочу сказать, что ты не один. "
    "{last_ref}Когда будет готов — я здесь.",

    "Заметила, что тебя не было пару дней. Это нормально — иногда "
    "нужно взять паузу. {last_ref}Без давления, просто знай: ты важен.",

    "Привет. Не писала, потому что уважаю твоё пространство. "
    "Но хочу, чтобы ты знал: ты не забыт. {last_ref}"
    "Возвращайся, когда почувствуешь.",

    "Просто тёплое напоминание: ты справляешься, даже когда молчишь. "
    "{last_ref}Если захочешь поделиться — я здесь и не осужу.",

    "Давно не общались. Надеюсь, у тебя всё хорошо. {last_ref}"
    "Помни: даже в тишине ты не одинок. Жду тебя без срока.",

    "Тишина — это тоже разговор. {last_ref}Я рядом, даже когда ты молчишь. "
    "Возвращайся, когда будет комфортно.",
]

# ──────────────────────────────────────────────
#  Шаблоны поддержки после кризиса
# ──────────────────────────────────────────────

AFTER_CRISIS_TEMPLATES = [
    "Ты прошёл через это. Серьёзно — ты молодец. {tools_ref}"
    "Я горжусь твоей силой. Продолжай дышать — я рядом.",

    "Кризис позади, и ты выстоял. Это говорит о твоей внутренней силе. "
    "{tools_ref}Если захочешь поговорить о том, что помогло — "
    "я всегда готова слушать.",

    "Ты справился с очень трудным моментом. {tools_ref}"
    "Знай: каждый раз, проходя через это, ты становишься сильнее. "
    "Я верю в тебя.",

    "Важно не то, что ты упал, а то, что ты поднялся. "
    "Ты поднялся. {tools_ref}Я рядом, чтобы поддержать "
    "тебя на каждом шагу.",

    "Только что пережитый кризис — это тяжело. Но ты не сдался. "
    "{tools_ref}Не забывай: ты всегда можешь опереться на меня.",

    "Ты выдержал бурю. {tools_ref}Каждый раз, проходя через "
    "это, ты учишься чему-то новому о своей силе. Я восхищаюсь тобой.",
]

# ──────────────────────────────────────────────
#  Шаблоны push-текстов (1 предложение)
# ──────────────────────────────────────────────

PUSH_TEMPLATES = {
    "morning": [
        "Доброе утро! Твой новый день начинается здесь.",
        "Утро — время тишины и намерений. Загляни на минутку.",
        "Просыпайся — сегодня тебя ждёт твой путь.",
        "Новый день — новый шаг. Я рядом, как всегда.",
        "С добрым утром. Сделай вдох — и начни.",
        "Ты уже проснулся? Тогда давай проживём этот день осознанно.",
    ],
    "evening": [
        "Вечер. Время подвести итоги и выдохнуть.",
        "День прожит. Ты справился — отметь это.",
        "Перед сном оставь все заботы. Завтра будет новый день.",
        "Спокойной ночи. Ты сделал достаточно.",
        "Вечер — твоё время. Побудь с собой в тишине.",
        "Как прошёл день? Загляни, если хочешь поделиться.",
    ],
    "absence_checkin": [
        "Просто тёплое напоминание: ты не один.",
        "Давно не виделись. Надеюсь, у тебя всё хорошо.",
        "Тишина — это нормально. Я здесь, когда будешь готов.",
        "Без давления, просто знай: ты важен и тебя ждут.",
        "Ты не забыт. Возвращайся, когда почувствуешь.",
    ],
    "after_crisis": [
        "Ты справился с кризисом. Я горжусь тобой.",
        "Кризис позади. Ты выстоял — это главное.",
        "Ты прошёл через трудный момент. Я рядом.",
        "Важно не то, что ты упал, а то, что поднялся. Ты поднялся.",
        "Ты не один в этом. Я здесь, чтобы поддержать.",
    ],
}


class NotificationEngine:
    """Генератор персонализированных уведомлений.

    Учитывает контекст пользователя, историю взаимодействий
    и защищает от спама через JSON-хранилище расписания.
    """

    def __init__(self, schedule_path: str = NOTIFICATION_SCHEDULE_PATH):
        self.schedule_path = schedule_path
        self._schedule = self._load_schedule()

    # ──────────────────────────────────────────────
    #  Управление расписанием
    # ──────────────────────────────────────────────

    def _load_schedule(self) -> dict:
        """Загружает расписание уведомлений из JSON-файла."""
        try:
            with open(self.schedule_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            return {}

    def _save_schedule(self) -> None:
        """Сохраняет расписание уведомлений в JSON-файл."""
        Path(self.schedule_path).parent.mkdir(parents=True, exist_ok=True)
        with open(self.schedule_path, "w", encoding="utf-8") as f:
            json.dump(self._schedule, f, ensure_ascii=False, indent=2)

    def _get_user_schedule(self, user_id: str) -> dict:
        """Возвращает расписание для конкретного пользователя."""
        if user_id not in self._schedule:
            self._schedule[user_id] = {
                "last_notification_time": None,
                "last_types": {},
            }
        return self._schedule[user_id]

    # ──────────────────────────────────────────────
    #  Контекстные помощники
    # ──────────────────────────────────────────────

    @staticmethod
    def _russian_days(count: int) -> str:
        """Возвращает «день», «дня» или «дней» в зависимости от числа."""
        if 11 <= count % 100 <= 14:
            return "дней"
        last_digit = count % 10
        if last_digit == 1:
            return "день"
        if 2 <= last_digit <= 4:
            return "дня"
        return "дней"

    def _is_close_to_milestone(self, days_clean: Optional[int]) -> Optional[str]:
        """Проверяет, близок ли пользователь к следующей медали (в пределах 7 дней).

        Returns:
            Строку-напоминание о близости к медали, или None.
        """
        if days_clean is None or days_clean < 0:
            return None
        for goal_days, medal_name in MILESTONES:
            remaining = goal_days - days_clean
            if 0 < remaining <= 7:
                day_word = self._russian_days(remaining)
                return f"До медали «{medal_name}» осталось всего {remaining} {day_word}! "
            if days_clean == goal_days:
                return f"Ты только что получил медаль «{medal_name}»! Поздравляю! "
        return None

    def _get_day_quality(self, context: dict) -> str:
        """Определяет качество дня пользователя по контексту.

        Returns:
            "хороший", "сложный" или "обычный".
        """
        if context.get("crisis_resolved"):
            return "сложный"
        achievements = context.get("achievements_today", [])
        if isinstance(achievements, list) and len(achievements) >= 2:
            return "хороший"
        emotion = context.get("current_emotion", "")
        if emotion in ("sadness", "anxiety", "fear", "shame", "guilt"):
            return "сложный"
        if emotion in ("joy", "gratitude", "hope"):
            return "хороший"
        if context.get("has_diary_entry_today") and context.get("intensity_today", 0) < 0.4:
            return "хороший"
        return "обычный"

    @staticmethod
    def _get_time_based_greeting() -> str:
        """Возвращает приветствие в зависимости от времени суток."""
        hour = datetime.now().hour
        if 5 <= hour < 12:
            return "Доброе утро"
        if 12 <= hour < 18:
            return "Добрый день"
        return "Добрый вечер"

    def _is_in_crisis(self, context: dict) -> bool:
        """Проверяет, находится ли пользователь в кризисном состоянии."""
        if context.get("crisis"):
            return True
        emotion = context.get("current_emotion")
        if emotion in ("fear", "craving", "shame", "crisis"):
            return True
        return False

    # ──────────────────────────────────────────────
    #  Генерация уведомлений
    # ──────────────────────────────────────────────

    def generate_morning(self, user_id: str, context: dict) -> str:
        """Генерирует утреннее приветствие.

        Параметры context:
            days_clean (int): дней без срыва
            medal (str): текущая медаль
            last_visit_date (str): дата последнего визита
            relationship_age (int): дней с начала отношений
            current_emotion (str): текущая эмоция

        Returns:
            Строка с утренним уведомлением (2-3 предложения).
        """
        days_clean = context.get("days_clean")
        milestone = self._is_close_to_milestone(days_clean)
        if milestone is None:
            milestone = ""

        greeting = self._get_time_based_greeting()
        template = random.choice(MORNING_TEMPLATES)
        return template.format(greeting=greeting, milestone=milestone)

    def generate_evening(self, user_id: str, context: dict) -> str:
        """Генерирует вечернюю рефлексию.

        Параметры context:
            has_diary_entry_today (bool): был ли дневник
            intensity_today (float): интенсивность дня (0-1)
            achievements_today (list): список достижений

        Returns:
            Строка с вечерним уведомлением (2-3 предложения).
        """
        day_quality = self._get_day_quality(context)

        acknowledgment = {
            "хороший": "Рада, что день прошёл хорошо.",
            "сложный": "Знаю, день был непростым.",
            "обычный": "Ещё один день прожит.",
        }[day_quality]

        achievements = context.get("achievements_today", [])
        if isinstance(achievements, list) and achievements:
            small_win = f"Ты сделал: {achievements[0]}. Это важно."
        elif context.get("has_diary_entry_today"):
            small_win = "Ты вёл дневник — это уже большая забота о себе."
        else:
            small_win = "Иногда маленький шаг — это просто быть. Ты справился."

        template = random.choice(EVENING_TEMPLATES)
        return template.format(acknowledgment=acknowledgment, small_win=small_win)

    def generate_absence_checkin(self, user_id: str, context: dict) -> str:
        """Генерирует чек-ин при отсутствии пользователя 2+ дня.

        Параметры context:
            days_absent (int): дней отсутствия
            last_known_state (str): последнее известное состояние
            relationship_age (int): дней с начала отношений

        Returns:
            Строка с чек-ином (2-3 предложения, без давления).
        """
        last_state = context.get("last_known_state", "")
        last_ref = ""
        if last_state:
            pleasant_states = ("joy", "gratitude", "hope", "neutral")
            if last_state in pleasant_states:
                last_ref = "Помню, как в прошлый раз тебе было немного легче. "
            else:
                last_ref = "Помню, тебе было непросто в прошлый раз. Надеюсь, сейчас всё наладилось. "
        else:
            last_ref = ""

        template = random.choice(ABSENCE_TEMPLATES)
        return template.format(last_ref=last_ref)

    def generate_after_crisis(self, user_id: str, context: dict) -> str:
        """Генерирует сообщение после кризисного эпизода.

        Параметры context:
            crisis_resolved (bool): разрешён ли кризис
            tools_used (list): какие инструменты помогли

        Returns:
            Строка с поддержкой (2-3 предложения).
        """
        tools = context.get("tools_used", [])
        if isinstance(tools, list) and tools:
            tools_ref = f"Ты использовал: {', '.join(tools)}. Это сработало."
        else:
            tools_ref = "Ты справился сам — это огромная сила."

        template = random.choice(AFTER_CRISIS_TEMPLATES)
        return template.format(tools_ref=tools_ref)

    def generate_push_text(self, notification_type: str, user_id: str, context: dict) -> str:
        """Генерирует короткий текст для push-уведомления (1 предложение).

        Args:
            notification_type: "morning" | "evening" | "absence_checkin" | "after_crisis"

        Returns:
            Одно предложение для push-уведомления.
        """
        templates = PUSH_TEMPLATES.get(notification_type, PUSH_TEMPLATES["morning"])
        return random.choice(templates)

    # ──────────────────────────────────────────────
    #  Rate limiting
    # ──────────────────────────────────────────────

    def should_notify(self, notification_type: str, user_id: str, context: dict) -> bool:
        """Проверяет, можно ли отправить уведомление сейчас.

        Правила:
        - Не чаще 1 уведомления в 6 часов.
        - Не отправлять, если пользователь в кризисе.
        - Не отправлять тот же тип чаще 1 раза в день.

        Args:
            notification_type: тип уведомления
            context: контекст с полем crisis

        Returns:
            True, если уведомление можно отправить.
        """
        if self._is_in_crisis(context):
            return False

        user_schedule = self._get_user_schedule(user_id)
        now = datetime.now()

        last_notification_time = user_schedule.get("last_notification_time")
        if last_notification_time:
            try:
                last_dt = datetime.strptime(last_notification_time, "%Y-%m-%dT%H:%M:%S")
            except ValueError:
                last_dt = datetime.strptime(last_notification_time, "%Y-%m-%dT%H:%M:%S.%f")
            if now - last_dt < timedelta(hours=6):
                return False

        last_types = user_schedule.get("last_types", {})
        today = date.today().isoformat()
        if last_types.get(notification_type) == today:
            return False

        user_schedule["last_notification_time"] = now.strftime("%Y-%m-%dT%H:%M:%S")
        user_schedule.setdefault("last_types", {})[notification_type] = today
        self._save_schedule()
        return True
