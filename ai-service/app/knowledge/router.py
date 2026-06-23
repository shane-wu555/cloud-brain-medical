from fastapi import APIRouter, Query

from app.core.rag import reindex_from_hospital_data, retrieve

from .models import KnowledgeReindexResponse, KnowledgeSearchResponse, KnowledgeSourceDto

router = APIRouter(tags=["knowledge"])


@router.get("/knowledge/search", response_model=KnowledgeSearchResponse, response_model_by_alias=True)
def search_knowledge(q: str = Query(..., min_length=1), limit: int = Query(default=5, ge=1, le=20)) -> KnowledgeSearchResponse:
    sources = [
        KnowledgeSourceDto(
            sourceId=source.source_id,
            sourceType=source.source_type,
            businessId=source.business_id,
            title=source.title,
            content=source.content,
            score=source.score,
        )
        for source in retrieve(q, limit)
    ]
    return KnowledgeSearchResponse(query=q, sources=sources)


@router.post("/knowledge/reindex", response_model=KnowledgeReindexResponse, response_model_by_alias=True)
def reindex_knowledge() -> KnowledgeReindexResponse:
    return KnowledgeReindexResponse(indexedCounts=reindex_from_hospital_data())
