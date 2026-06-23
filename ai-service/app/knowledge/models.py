from typing import Optional

from pydantic import BaseModel, Field


class KnowledgeSourceDto(BaseModel):
    source_id: str = Field(alias="sourceId")
    source_type: str = Field(alias="sourceType")
    business_id: Optional[str] = Field(default=None, alias="businessId")
    title: str
    content: str
    score: Optional[float] = None

    model_config = {"populate_by_name": True}


class KnowledgeSearchResponse(BaseModel):
    query: str
    sources: list[KnowledgeSourceDto]

    model_config = {"populate_by_name": True}


class KnowledgeReindexResponse(BaseModel):
    indexed_counts: dict[str, int] = Field(alias="indexedCounts")

    model_config = {"populate_by_name": True}
