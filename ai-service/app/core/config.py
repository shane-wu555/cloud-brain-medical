import os
from dataclasses import dataclass


@dataclass(frozen=True)
class AiSettings:
    provider: str
    openai_base_url: str
    openai_api_key: str | None
    openai_model: str
    timeout_seconds: float
    allow_fallback: bool
    rag_database_url: str | None
    rag_schema: str
    rag_embedding_dim: int
    rag_connect_timeout_seconds: float

    @property
    def llm_enabled(self) -> bool:
        return self.provider == "openai_compatible" and bool(self.openai_api_key)


def settings() -> AiSettings:
    return AiSettings(
        provider=os.getenv("AI_PROVIDER", "mock").strip().lower(),
        openai_base_url=os.getenv("AI_OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/"),
        openai_api_key=os.getenv("AI_OPENAI_API_KEY") or None,
        openai_model=os.getenv("AI_OPENAI_MODEL", "gpt-4o-mini"),
        timeout_seconds=float(os.getenv("AI_TIMEOUT_SECONDS", "55")),
        allow_fallback=os.getenv("AI_ALLOW_FALLBACK", "true").strip().lower() != "false",
        rag_database_url=os.getenv("AI_RAG_DATABASE_URL") or os.getenv("DATABASE_URL") or None,
        rag_schema=os.getenv("AI_RAG_SCHEMA", "ai"),
        rag_embedding_dim=int(os.getenv("AI_RAG_EMBEDDING_DIM", "64")),
        rag_connect_timeout_seconds=float(os.getenv("AI_RAG_CONNECT_TIMEOUT_SECONDS", "3")),
    )
