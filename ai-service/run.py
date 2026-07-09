#!/usr/bin/env python3
"""Точка входа для AI Recovery Backend.

Usage:
    # Установка зависимостей
    pip install -e .

    # Запуск сервера (по умолчанию http://localhost:8000)
    python run.py

    # С моделью Ollama
    python run.py --model llama3.2:1b --ollama-url http://localhost:11434
"""

import argparse
import uvicorn

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI Recovery Backend")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--model", default="llama3.2:1b")
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    args = parser.parse_args()

    import os
    os.environ["AI_MODEL"] = args.model
    os.environ["OLLAMA_URL"] = args.ollama_url

    uvicorn.run(
        "app.main:app",
        host=args.host,
        port=args.port,
        reload=True,
        log_level="info",
    )
