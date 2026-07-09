from pydantic import BaseModel
from typing import Optional


class Message(BaseModel):
    role: str  # "user" | "assistant" | "system"
    content: str


class ChatRequest(BaseModel):
    message: str
    history: list[dict] = []
    user_id: str = "default"


class ChatResponse(BaseModel):
    reply: str
    emotion: Optional[str] = None
    crisis: bool = False


class DiaryEntry(BaseModel):
    date: str
    intensity: int
    trigger: Optional[str] = None
    emotion: Optional[str] = None
    tools_used: list[str] = []
    tools_helped: list[str] = []
    duration_min: Optional[int] = None
    outcome: Optional[str] = None


class UserProfile(BaseModel):
    user_id: str = "default"
    ai_name: str = "Помощник"
    style: str = "balanced"
    support_level: str = "medium"
    preferences: dict = {}


class ProfileUpdate(BaseModel):
    ai_name: Optional[str] = None
    style: Optional[str] = None
    support_level: Optional[str] = None
    preferences: Optional[dict] = None


class MemoryEntry(BaseModel):
    type: str  # "fact" | "success" | "struggle" | "tool" | "preference"
    content: str
    timestamp: str
    importance: int = 1


class AnalyzeRequest(BaseModel):
    entries: list[DiaryEntry]


class AnalyzeResponse(BaseModel):
    total_episodes: int
    avg_intensity: float
    top_triggers: list[dict]
    best_tools: list[dict]
    risk_period: Optional[str] = None
    insight: str
