import os
from dataclasses import dataclass
from dotenv import load_dotenv

load_dotenv(override=True)


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

    ct_classifier_model: str | None
    ct_segmentation_model: str | None
    ct_metal_segmentation_model: str | None
    ct_metal_classifier_model: str | None

    @property
    def llm_enabled(self) -> bool:
        return self.provider == "openai_compatible" and bool(self.openai_api_key)


def settings() -> AiSettings:
    return AiSettings(
        provider=os.getenv("AI_PROVIDER", "openai_compatible").strip().lower(),
        openai_base_url=os.getenv("AI_OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/"),
        openai_api_key=os.getenv("AI_OPENAI_API_KEY") or None,
        openai_model=os.getenv("AI_OPENAI_MODEL", "gpt-4o-mini"),
        timeout_seconds=float(os.getenv("AI_TIMEOUT_SECONDS", "55")),
        allow_fallback=os.getenv("AI_ALLOW_FALLBACK", "false").strip().lower() == "true",
        rag_database_url=os.getenv("AI_RAG_DATABASE_URL") or os.getenv("DATABASE_URL") or None,
        rag_schema=os.getenv("AI_RAG_SCHEMA", "ai"),
        rag_embedding_dim=int(os.getenv("AI_RAG_EMBEDDING_DIM", "64")),
        rag_connect_timeout_seconds=float(os.getenv("AI_RAG_CONNECT_TIMEOUT_SECONDS", "3")),
        
        # CT models
        ct_classifier_model=os.getenv("CT_CLASSIFIER_MODEL"),
        ct_segmentation_model=os.getenv("CT_SEGMENTATION_MODEL"),
        ct_metal_segmentation_model=os.getenv("CT_METAL_SEGMENTATION_MODEL"),
        ct_metal_classifier_model=os.getenv("CT_METAL_CLASSIFIER_MODEL"),
    )
