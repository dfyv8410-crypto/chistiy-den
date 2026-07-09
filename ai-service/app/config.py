"""Конфигурация AI сервиса."""

import os


class Settings:
    model: str = os.getenv("AI_MODEL", "llama3.2:1b")
    ollama_url: str = os.getenv("OLLAMA_URL", "http://localhost:11434")
    memory_path: str = os.getenv("MEMORY_PATH", "memory_store.json")
    cors_origins: list[str] = ["*"]


settings = Settings()
