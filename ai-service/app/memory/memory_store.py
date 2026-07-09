"""
Memory System for AI Recovery Companion.

Two-tier:
- ShortMemory: current conversation context (in-memory, per-user)
- LongMemory: persistent facts using ChromaDB (vector embeddings)
"""

import json
import time
from typing import Optional
from dataclasses import dataclass, field


@dataclass
class ShortMemory:
    user_id: str
    messages: list[dict] = field(default_factory=list)
    max_turns: int = 20

    def add(self, role: str, content: str):
        self.messages.append({"role": role, "content": content})
        if len(self.messages) > self.max_turns * 2:
            self.messages = self.messages[-self.max_turns * 2:]

    def get_context(self) -> list[dict]:
        return self.messages[-self.max_turns * 2:]

    def clear(self):
        self.messages = []


class LongMemory:
    """Persistent memory using a simple JSON file (lightweight, no external deps).
    In production, replace with ChromaDB / FAISS.
    """

    def __init__(self, path: str = "memory_store.json"):
        self.path = path
        self._store: dict = {}
        self._load()

    def _load(self):
        try:
            with open(self.path) as f:
                self._store = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            self._store = {"profiles": {}, "facts": {}}

    def _save(self):
        with open(self.path, "w") as f:
            json.dump(self._store, f, ensure_ascii=False, indent=2)

    def get_profile(self, user_id: str) -> dict:
        return self._store.setdefault("profiles", {}).get(user_id, {})

    def save_profile(self, user_id: str, profile: dict):
        self._store.setdefault("profiles", {})[user_id] = profile
        self._save()

    def add_fact(self, user_id: str, fact: dict):
        facts = self._store.setdefault("facts", {}).setdefault(user_id, [])
        fact["timestamp"] = time.time()
        facts.append(fact)
        if len(facts) > 500:
            facts[:] = facts[-500:]
        self._save()

    def get_facts(self, user_id: str, limit: int = 20) -> list[dict]:
        facts = self._store.setdefault("facts", {}).get(user_id, [])
        return sorted(facts, key=lambda x: -x.get("importance", 1))[:limit]

    def delete_facts(self, user_id: str):
        self._store.setdefault("facts", {})[user_id] = []
        self._save()

    def delete_profile(self, user_id: str):
        self._store.setdefault("profiles", {}).pop(user_id, None)
        self._save()
