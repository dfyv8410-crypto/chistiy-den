"""
Tests for RecoveryAgent (run with pytest, needs Python 3.11+)
Run: cd ai-service && pip install -e ".[dev]" && pytest ../tests/ai/
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "ai-service"))

from app.models.schemas import ChatRequest, ProfileUpdate
from app.agents.recovery_agent import RecoveryAgent


class FakeLLM:
    """Mock LLM that returns canned responses."""
    def chat(self, messages, system, temperature=0.7, max_tokens=512):
        return "Я понимаю, тебе сейчас непросто. Расскажи, что происходит."

    def analyze_sentiment(self, text):
        return {"emotion": "neutral", "intensity": 0, "crisis": False}

    def generate_personality(self, style="balanced"):
        return f"Ты — AI-помощник восстановления. Стиль: {style}."


def test_agent_hello():
    agent = RecoveryAgent(llm=FakeLLM())
    req = ChatRequest(message="Привет", user_id="test1")
    resp = agent.chat(req)
    assert resp.reply, "Reply should not be empty"
    assert not resp.crisis, "Not a crisis"
    print("✓ test_agent_hello")


def test_agent_empty():
    agent = RecoveryAgent(llm=FakeLLM())
    req = ChatRequest(message="", user_id="test1")
    resp = agent.chat(req)
    assert resp.reply, "Should have fallback reply"
    print("✓ test_agent_empty")


def test_agent_crisis():
    agent = RecoveryAgent(llm=FakeLLM())
    req = ChatRequest(message="Мне очень плохо, хочу сорваться", user_id="test2")
    resp = agent.chat(req)
    assert resp.crisis, "Should detect crisis"
    print("✓ test_agent_crisis")


def test_agent_with_context():
    agent = RecoveryAgent(llm=FakeLLM())
    context = {
        "diary": [
            {"date": "2026-07-08", "intensity": 7, "trigger": "ссора",
             "situation": "Поссорился с женой", "tools": ["позвонил наставнику"]}
        ],
        "progress": {"days": 45, "medal": "Оранжевая"},
        "steps": 3,
        "recentEvents": []
    }
    req = ChatRequest(message="Мне тяжело", user_id="test3", context=context)
    resp = agent.chat(req)
    assert resp.reply, "Should reply with context"
    print("✓ test_agent_with_context")


def test_profile_update():
    agent = RecoveryAgent(llm=FakeLLM())
    profile = agent.update_profile("test4", {"ai_name": "Мудрец", "style": "coach"})
    assert profile.get("ai_name") == "Мудрец"
    p2 = agent.get_profile("test4")
    assert p2.get("style") == "coach"
    print("✓ test_profile_update")


def test_long_memory():
    agent = RecoveryAgent(llm=FakeLLM())
    facts = agent.get_memory("test5")
    assert isinstance(facts, list), "Memory should be a list"
    print("✓ test_long_memory")


if __name__ == "__main__":
    test_agent_hello()
    test_agent_empty()
    test_agent_crisis()
    test_agent_with_context()
    test_profile_update()
    test_long_memory()
    print("\nAll tests passed ✓")
