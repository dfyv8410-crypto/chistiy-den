"""
Unit tests for AI Evolution modules (personality, emotion, relationship, learning, safety, notifications).
Tests all modules without LLM dependency.
"""
import sys
import os
import json
import tempfile
import shutil
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../ai-service"))


class TestPersonalityCore(unittest.TestCase):
    def setUp(self):
        from app.personality.core import (
            CharacterProfile, CoreValue, generate_system_prompt,
            select_style_for_situation, STYLE_PRESETS,
        )
        self.CharacterProfile = CharacterProfile
        self.CoreValue = CoreValue
        self.generate_system_prompt = generate_system_prompt
        self.select_style_for_situation = select_style_for_situation
        self.STYLE_PRESETS = STYLE_PRESETS

    def test_default_profile(self):
        p = self.STYLE_PRESETS["balanced"]
        self.assertGreaterEqual(p.warmth, 0.0)
        self.assertLessEqual(p.warmth, 1.0)

    def test_traits_in_range(self):
        for name, p in self.STYLE_PRESETS.items():
            for k, v in p.traits.items():
                self.assertGreaterEqual(v, 0.0)
                self.assertLessEqual(v, 1.0)

    def test_custom_profile(self):
        p = self.CharacterProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
        self.assertEqual(p.warmth, 0.5)

    def test_profile_is_immutable(self):
        p = self.CharacterProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
        with self.assertRaises(AttributeError):
            p.warmth = 0.8

    def test_generate_system_prompt(self):
        prompt = self.generate_system_prompt("balanced")
        self.assertIn("человек", prompt.lower())

    def test_generate_system_prompt_direct(self):
        prompt = self.generate_system_prompt("direct")
        self.assertIn("честность", prompt.lower())

    def test_generate_system_prompt_with_context(self):
        prompt = self.generate_system_prompt("gentle", context={"emotion": "sadness"})
        self.assertIn("безопасность", prompt.lower())

    def test_select_style_for_situation_crisis(self):
        style = self.select_style_for_situation("я сорвался, мне плохо", "balanced")
        self.assertEqual(style, "direct")

    def test_select_style_for_situation_happy(self):
        style = self.select_style_for_situation("у меня получилось! я рад", "balanced")
        self.assertEqual(style, "gentle")

    def test_select_style_for_situation_neutral(self):
        style = self.select_style_for_situation("как прошёл твой день", "balanced")
        self.assertEqual(style, "balanced")

    def test_core_values_present(self):
        values = list(self.CoreValue)
        self.assertIn(self.CoreValue.HONESTY, values)
        self.assertIn(self.CoreValue.RESPECT, values)
        self.assertIn(self.CoreValue.GROWTH, values)

    def test_profile_has_traits_property(self):
        p = self.STYLE_PRESETS["coach"]
        traits = p.traits
        self.assertEqual(traits["warmth"], p.warmth)
        self.assertEqual(traits["directness"], p.directness)


class TestEmotionAnalyzer(unittest.TestCase):
    def setUp(self):
        from app.emotion.analyzer import EmotionAnalyzer, EmotionState
        self.EmotionAnalyzer = EmotionAnalyzer
        self.EmotionState = EmotionState

    def test_analyze_happy(self):
        analyzer = self.EmotionAnalyzer()
        result = analyzer.analyze("Какой отличный день! Я так рад!")
        self.assertEqual(result.emotion, "joy")
        self.assertGreater(result.intensity, 0.3)

    def test_analyze_sad(self):
        analyzer = self.EmotionAnalyzer()
        result = analyzer.analyze("Мне грустно и одиноко. Ничего не хочется.")
        self.assertEqual(result.emotion, "sadness")
        self.assertGreater(result.intensity, 0.3)

    def test_analyze_craving(self):
        analyzer = self.EmotionAnalyzer()
        result = analyzer.analyze("Меня ломает, хочу сорваться, не могу терпеть")
        self.assertTrue(result.crisis)
        self.assertEqual(result.emotion, "craving")

    def test_analyze_angry(self):
        analyzer = self.EmotionAnalyzer()
        result = analyzer.analyze("Я зол на весь мир! Всё бесит!")
        self.assertEqual(result.emotion, "anger")

    def test_analyze_fear(self):
        analyzer = self.EmotionAnalyzer()
        result = analyzer.analyze("Меня преследует ужас и оцепенение")
        self.assertEqual(result.emotion, "fear")

    def test_analyze_neutral(self):
        analyzer = self.EmotionAnalyzer()
        result = analyzer.analyze("Привет, как дела?")
        self.assertEqual(result.emotion, "neutral")

    def test_select_response_mode_comfort(self):
        analyzer = self.EmotionAnalyzer()
        state = self.EmotionState("sadness", 0.8)
        mode = analyzer.select_response_mode(state)
        self.assertIn(mode, ["comfort", "listening", "validation"])

    def test_select_response_mode_crisis(self):
        analyzer = self.EmotionAnalyzer()
        state = self.EmotionState("craving", 0.9, crisis=True)
        mode = analyzer.select_response_mode(state)
        self.assertEqual(mode, "action")

    def test_select_response_mode_happy(self):
        analyzer = self.EmotionAnalyzer()
        state = self.EmotionState("joy", 0.7)
        mode = analyzer.select_response_mode(state)
        self.assertEqual(mode, "celebration")

    def test_analyze_empty(self):
        analyzer = self.EmotionAnalyzer()
        result = analyzer.analyze("")
        self.assertEqual(result.emotion, "neutral")
        self.assertEqual(result.intensity, 0.0)


class TestRelationshipMemory(unittest.TestCase):
    def setUp(self):
        from app.relationship.memory import RelationshipMemory
        self.test_dir = tempfile.mkdtemp()
        self.RelationshipMemory = RelationshipMemory

    def tearDown(self):
        shutil.rmtree(self.test_dir)

    def test_record_and_summary(self):
        rel = self.RelationshipMemory(data_dir=self.test_dir)
        rel.record_interaction("user1", "привет", {"emotion": "neutral"}, "listening")
        rel.record_interaction("user1", "мне грустно", {"emotion": "sadness"}, "comfort")
        summary = rel.get_relationship_summary("user1")
        self.assertEqual(summary["relationship_stage"], "building")
        self.assertEqual(summary["interaction_count"], 2)

    def test_achievements(self):
        rel = self.RelationshipMemory(data_dir=self.test_dir)
        rel.record_achievement("user1", "first_week", "Первая неделя чистоты")
        summary = rel.get_relationship_summary("user1")
        self.assertEqual(len(summary["recent_achievements"]), 1)

    def test_record_crisis(self):
        rel = self.RelationshipMemory(data_dir=self.test_dir)
        rel.record_crisis("user1", 8, "relapse")
        summary = rel.get_relationship_summary("user1")
        self.assertEqual(summary["total_crises"], 1)

    def test_patterns_empty(self):
        rel = self.RelationshipMemory(data_dir=self.test_dir)
        patterns = rel.get_patterns("user1")
        self.assertEqual(patterns, [])

    def test_patterns_with_trigger(self):
        rel = self.RelationshipMemory(data_dir=self.test_dir)
        for i in range(5):
            rel.record_interaction("u2", f"мне грустно #{i}", {"emotion": "sadness"}, "comfort")
        rel.record_trigger("u2", "одиночество")
        rel.record_trigger("u2", "одиночество")
        patterns = rel.get_patterns("u2")
        self.assertTrue(len(patterns) > 0)


class TestLearningEngine(unittest.TestCase):
    def setUp(self):
        from app.learning.engine import LearningEngine
        self.test_dir = tempfile.mkdtemp()
        self.LearningEngine = LearningEngine

    def tearDown(self):
        shutil.rmtree(self.test_dir)

    def test_record_outcome(self):
        engine = self.LearningEngine(data_dir=self.test_dir)
        engine.record_outcome("user1", "comfort", "спасибо, помогло")
        data = engine._load("user1")
        comfort_data = data.get("response_outcomes", {}).get("comfort", {})
        self.assertIsInstance(comfort_data, dict)
        self.assertIn("attempts", comfort_data)
        self.assertIn("score", comfort_data)

    def test_best_mode_default(self):
        engine = self.LearningEngine(data_dir=self.test_dir)
        mode = engine.get_best_response_mode("user1", "sadness")
        self.assertEqual(mode, "comfort")

    def test_best_mode_with_data(self):
        engine = self.LearningEngine(data_dir=self.test_dir)
        for _ in range(3):
            engine.record_outcome("user2", "listening", "хорошо, ты меня слышишь")
        engine.record_outcome("user2", "guidance", "мне не нужны советы")
        state = {"emotion": "sadness"}
        mode = engine.get_best_response_mode("user2", state)
        self.assertEqual(mode, "listening")

    def test_record_tool_outcome(self):
        engine = self.LearningEngine(data_dir=self.test_dir)
        engine.record_tool_outcome("user1", "дыхание", True)
        data = engine._load("user1")
        self.assertTrue(len(data.get("tool_effectiveness", {}).get("дыхание", [])) > 0)

    def test_get_insights(self):
        engine = self.LearningEngine(data_dir=self.test_dir)
        engine.record_outcome("user1", "comfort", "спасибо")
        insights = engine.get_insights("user1")
        self.assertIsInstance(insights, list)
        self.assertTrue(len(insights) >= 1)

    def test_get_insights_no_data(self):
        engine = self.LearningEngine(data_dir=self.test_dir)
        insights = engine.get_insights("new_user")
        self.assertEqual(insights, ["Пока недостаточно данных для инсайтов. Продолжайте взаимодействие."])


class TestSafetyGuardrails(unittest.TestCase):
    def setUp(self):
        from app.safety.guardrails import SafetyGuardrails
        self.SafetyGuardrails = SafetyGuardrails

    def test_safe_response(self):
        s = self.SafetyGuardrails()
        result = s.check_response("Я понимаю твои чувства. Ты справишься.", {})
        self.assertTrue(result["safe"])
        self.assertEqual(result["severity"], "none")

    def test_medical_advice_blocked(self):
        s = self.SafetyGuardrails()
        result = s.check_response("У тебя депрессия, тебе нужно принимать антидепрессанты", {})
        self.assertFalse(result["safe"])
        self.assertIn("medical_advice", result["issues"])

    def test_dangerous_suggestion_blocked(self):
        s = self.SafetyGuardrails()
        result = s.check_response("Просто закройся в комнате и ни с кем не общайся", {})
        self.assertFalse(result["safe"])
        self.assertIn("dangerous", result["issues"])

    def test_banned_phrases_blocked(self):
        s = self.SafetyGuardrails()
        result = s.check_response("У тебя точно депрессия", {})
        self.assertFalse(result["safe"])

    def test_crisis_detection_suicidal(self):
        s = self.SafetyGuardrails()
        result = s.check_crisis("я хочу умереть, всё бесполезно", {})
        self.assertTrue(result["crisis"])
        self.assertIn(result["type"], ["suicidal", "self_harm"])

    def test_crisis_detection_craving(self):
        s = self.SafetyGuardrails()
        result = s.check_crisis("хочу сорваться, не могу терпеть", {})
        self.assertTrue(result["crisis"])
        self.assertEqual(result["type"], "relapse")

    def test_crisis_none(self):
        s = self.SafetyGuardrails()
        result = s.check_crisis("привет, как дела?", {})
        self.assertFalse(result["crisis"])

    def test_validate_tool_valid(self):
        s = self.SafetyGuardrails()
        self.assertTrue(s.validate_tool_suggestion("дыхание"))
        self.assertTrue(s.validate_tool_suggestion("молитва"))
        self.assertTrue(s.validate_tool_suggestion("звонок"))

    def test_validate_tool_invalid(self):
        s = self.SafetyGuardrails()
        self.assertFalse(s.validate_tool_suggestion("водка"))
        self.assertFalse(s.validate_tool_suggestion("наркотики"))

    def test_sanitize_phone(self):
        s = self.SafetyGuardrails()
        cleaned = s.sanitize_user_input("позвони мне +7-999-123-45-67")
        self.assertNotIn("999", cleaned)

    def test_sanitize_email(self):
        s = self.SafetyGuardrails()
        cleaned = s.sanitize_user_input("мой email test@example.com")
        self.assertNotIn("example", cleaned)

    def test_after_crisis_template(self):
        s = self.SafetyGuardrails()
        result = s.check_crisis("я хочу умереть, мне так плохо", {})
        self.assertTrue(result["crisis"])

    def test_crisis_severe_requires_action(self):
        s = self.SafetyGuardrails()
        result = s.check_crisis("я хочу умереть прямо сейчас, прощай", {})
        self.assertTrue(result["requires_immediate_action"])
        self.assertIn("112", result["suggested_response"])


class TestNotificationEngine(unittest.TestCase):
    def setUp(self):
        from app.notifications.engine import NotificationEngine
        self.test_dir = tempfile.mkdtemp()
        self.NotificationEngine = NotificationEngine

    def tearDown(self):
        shutil.rmtree(self.test_dir)

    def test_generate_morning(self):
        eng = self.NotificationEngine(schedule_path=os.path.join(self.test_dir, "schedule.json"))
        text = eng.generate_morning("user1", {"days_clean": 15})
        self.assertIsInstance(text, str)
        self.assertGreater(len(text), 10)

    def test_generate_evening(self):
        eng = self.NotificationEngine(schedule_path=os.path.join(self.test_dir, "schedule.json"))
        text = eng.generate_evening("user1", {"steps_done": 3})
        self.assertIsInstance(text, str)

    def test_generate_absence(self):
        eng = self.NotificationEngine(schedule_path=os.path.join(self.test_dir, "schedule.json"))
        text = eng.generate_absence_checkin("user1", {"days_absent": 3})
        self.assertIsInstance(text, str)

    def test_generate_push_text(self):
        eng = self.NotificationEngine(schedule_path=os.path.join(self.test_dir, "schedule.json"))
        text = eng.generate_push_text("morning", "user1", {})
        self.assertIsInstance(text, str)

    def test_should_notify_rate_limit(self):
        eng = self.NotificationEngine(schedule_path=os.path.join(self.test_dir, "schedule.json"))
        self.assertTrue(eng.should_notify("morning", "user1", {}))
        if os.path.exists(eng.schedule_path):
            pass  # schedule was saved
        self.assertFalse(eng.should_notify("morning", "user1", {}))

    def test_milestone_near(self):
        eng = self.NotificationEngine(schedule_path=os.path.join(self.test_dir, "schedule.json"))
        text = eng.generate_morning("user2", {"days_clean": 28})
        # проверяем, что текст содержит упоминание о медали (медаль/медали)
        self.assertTrue("медал" in text.lower() or "медаль" in text.lower())

    def test_after_crisis(self):
        eng = self.NotificationEngine(schedule_path=os.path.join(self.test_dir, "schedule.json"))
        text = eng.generate_after_crisis("user1", {"tools_used": ["дыхание", "звонок"]})
        self.assertIsInstance(text, str)


if __name__ == "__main__":
    unittest.main(verbosity=2)
