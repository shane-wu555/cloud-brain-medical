import hashlib
import logging
import math
import re
from dataclasses import dataclass
from typing import Iterable

try:
    import psycopg
except ModuleNotFoundError:  # pragma: no cover - exercised in minimal local envs
    psycopg = None

from .config import settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class KnowledgeSource:
    source_id: str
    title: str
    content: str
    source_type: str = "RULE"
    business_id: str | None = None
    score: float | None = None


_RULE_SOURCES = [
    KnowledgeSource(
        source_id="rule-neuro-risk-001",
        source_type="HOSPITAL_RULE",
        business_id="neuro-risk",
        title="神经系统急诊危险信号",
        content="突发剧烈头痛、意识障碍、抽搐、偏瘫、言语不清、瞳孔异常属于神经系统急诊危险信号，应优先急诊评估。",
    ),
    KnowledgeSource(
        source_id="rule-head-ct-001",
        source_type="HOSPITAL_RULE",
        business_id="head-ct",
        title="头部 CT 检查适应证提示",
        content="头部外伤、突发剧烈头痛、疑似脑出血、局灶神经功能缺损或意识改变时，可结合病情考虑头部 CT。",
    ),
    KnowledgeSource(
        source_id="rule-med-safety-001",
        source_type="HOSPITAL_RULE",
        business_id="medication-safety",
        title="用药建议安全边界",
        content="AI 只能生成用药建议草稿，正式处方必须由医生结合过敏史、禁忌证、肝肾功能和药品说明书人工确认。",
    ),
    KnowledgeSource(
        source_id="rule-ai-boundary-001",
        source_type="HOSPITAL_RULE",
        business_id="ai-human-confirmation",
        title="AI 医疗数据人工确认规则",
        content="AI 不得直接生成最终诊断、生效处方、正式报告或发布排班，所有 AI 草稿必须保留来源并由授权人员确认。",
    ),
    KnowledgeSource(
        source_id="rule-schedule-001",
        source_type="HOSPITAL_RULE",
        business_id="schedule-confirmation",
        title="AI 排班建议确认规则",
        content="AI 可根据科室需求、医生可用性、请假和历史挂号量生成排班建议，但必须由管理员确认后才能发布并同步号源。",
    ),
]

_MANAGED_SOURCE_TYPES = ("HOSPITAL_RULE", "DEPARTMENT", "DOCTOR", "SCHEDULE", "DOCTOR_EVENT", "MEDICAL_ITEM", "DRUG")


def retrieve(query: str, limit: int = 3, source_types: Iterable[str] | None = None) -> list[KnowledgeSource]:
    allowed_source_types = _source_type_filter(source_types)
    config = settings()
    if config.rag_database_url:
        try:
            return search_persistent(query, limit, allowed_source_types)
        except Exception:
            if not config.allow_fallback:
                raise
    return search_memory(query, limit, allowed_source_types)


def search_memory(query: str, limit: int = 3, source_types: tuple[str, ...] | None = None) -> list[KnowledgeSource]:
    sources = _filter_sources(_RULE_SOURCES, source_types)
    terms = [term for term in re.split(r"[\s，。；、,.!?！？]+", query) if term]
    if not terms:
        return sources[:limit]

    def score(source: KnowledgeSource) -> int:
        text = f"{source.title} {source.content}"
        return sum(1 for term in terms if term in text)

    ranked = sorted(sources, key=score, reverse=True)
    selected = [source for source in ranked if score(source) > 0]
    return (selected or sources)[:limit]


def ensure_schema() -> None:
    config = settings()
    if not config.rag_database_url:
        return
    if psycopg is None:
        raise RuntimeError("psycopg is required for pgvector RAG")
    schema = _identifier(config.rag_schema)
    dim = config.rag_embedding_dim
    with _connect(config) as conn:
        with conn.cursor() as cur:
            cur.execute("create extension if not exists vector")
            cur.execute(f"create schema if not exists {schema}")
            cur.execute(
                f"""
                create table if not exists {schema}.knowledge_document (
                    id varchar(128) primary key,
                    source_type varchar(64) not null,
                    business_id varchar(128),
                    title text not null,
                    content text not null,
                    embedding vector({dim}) not null,
                    metadata jsonb not null default '{{}}'::jsonb,
                    updated_at timestamp not null default now()
                )
                """
            )
            cur.execute(
                f"""
                create index if not exists idx_knowledge_document_source
                on {schema}.knowledge_document(source_type, business_id)
                """
            )


def reindex_from_hospital_data() -> dict[str, int]:
    config = settings()
    if not config.rag_database_url:
        raise RuntimeError("AI_RAG_DATABASE_URL is not configured")
    if psycopg is None:
        raise RuntimeError("psycopg is required for pgvector RAG")
    ensure_schema()
    documents = list(_rule_documents())
    with _connect(config) as conn:
        documents.extend(_business_documents(conn))
        schema = _identifier(config.rag_schema)
        with conn.cursor() as cur:
            for doc in documents:
                vector = _vector_literal(embed(f"{doc.title}\n{doc.content}", config.rag_embedding_dim))
                cur.execute(
                    f"""
                    insert into {schema}.knowledge_document
                        (id, source_type, business_id, title, content, embedding, metadata, updated_at)
                    values (%s, %s, %s, %s, %s, %s::vector, %s::jsonb, now())
                    on conflict (id) do update set
                        source_type = excluded.source_type,
                        business_id = excluded.business_id,
                        title = excluded.title,
                        content = excluded.content,
                        embedding = excluded.embedding,
                        metadata = excluded.metadata,
                        updated_at = now()
                    """,
                    (
                        doc.source_id,
                        doc.source_type,
                        doc.business_id,
                        doc.title,
                        doc.content,
                        vector,
                        "{}",
                    ),
                )
            indexed_ids = [doc.source_id for doc in documents]
            if indexed_ids:
                cur.execute(
                    f"""
                    delete from {schema}.knowledge_document
                    where source_type = any(%s) and not (id = any(%s))
                    """,
                    (list(_MANAGED_SOURCE_TYPES), indexed_ids),
                )
    counts: dict[str, int] = {}
    for doc in documents:
        counts[doc.source_type] = counts.get(doc.source_type, 0) + 1
    return counts


def search_persistent(query: str, limit: int = 3, source_types: tuple[str, ...] | None = None) -> list[KnowledgeSource]:
    config = settings()
    if not config.rag_database_url:
        return search_memory(query, limit, source_types)
    if psycopg is None:
        return search_memory(query, limit, source_types)
    ensure_schema()
    schema = _identifier(config.rag_schema)
    vector = _vector_literal(embed(query, config.rag_embedding_dim))
    with _connect(config) as conn:
        with conn.cursor() as cur:
            where_clause = ""
            params: list = [vector]
            if source_types:
                where_clause = "where source_type = any(%s)"
                params.append(list(source_types))
            params.extend([vector, limit])
            cur.execute(
                f"""
                select id, source_type, business_id, title, content,
                       1 - (embedding <=> %s::vector) as score
                from {schema}.knowledge_document
                {where_clause}
                order by embedding <=> %s::vector
                limit %s
                """,
                tuple(params),
            )
            rows = cur.fetchall()
    if not rows:
        return search_memory(query, limit, source_types)
    return [
        KnowledgeSource(
            source_id=row[0],
            source_type=row[1],
            business_id=row[2],
            title=row[3],
            content=row[4],
            score=float(row[5]) if row[5] is not None else None,
        )
        for row in rows
    ]


def embed(text: str, dim: int = 64) -> list[float]:
    vector = [0.0] * dim
    tokens = _tokens(text)
    if not tokens:
        tokens = [text or "empty"]
    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % dim
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[index] += sign
    norm = math.sqrt(sum(value * value for value in vector)) or 1.0
    return [round(value / norm, 6) for value in vector]


def _tokens(text: str) -> list[str]:
    parts = [part for part in re.split(r"[\s，。；、,.!?！？:：()（）/]+", text.lower()) if part]
    chars = [text[i : i + 2] for i in range(max(0, len(text) - 1)) if not text[i].isspace()]
    return parts + chars


def _source_type_filter(source_types: Iterable[str] | None) -> tuple[str, ...] | None:
    if source_types is None:
        return None
    normalized = tuple(sorted({source_type for source_type in source_types if source_type}))
    return normalized or None


def _filter_sources(sources: Iterable[KnowledgeSource], source_types: tuple[str, ...] | None) -> list[KnowledgeSource]:
    if not source_types:
        return list(sources)
    allowed = set(source_types)
    return [source for source in sources if source.source_type in allowed]


def _rule_documents() -> Iterable[KnowledgeSource]:
    return _RULE_SOURCES


def _business_documents(conn) -> list[KnowledgeSource]:
    documents: list[KnowledgeSource] = []
    documents.extend(_query_documents(conn, "DEPARTMENT", "doctor", """
        select id, name, coalesce(description, '') from doctor.department where active = true
    """, lambda row: KnowledgeSource(
        source_id=f"department-{row[0]}",
        source_type="DEPARTMENT",
        business_id=row[0],
        title=f"科室：{row[1]}",
        content=f"{row[1]}。{row[2]}",
    )))
    documents.extend(_query_documents(conn, "DOCTOR", "doctor", """
        select d.id, d.name, coalesce(d.title, ''), p.name, coalesce(d.specialty, ''), d.role_type
        from doctor.staff d join doctor.department p on p.id = d.department_id
        where d.active = true
    """, lambda row: KnowledgeSource(
        source_id=f"doctor-{row[0]}",
        source_type="DOCTOR",
        business_id=row[0],
        title=f"医生：{row[1]}",
        content=f"{row[1]}，{row[2]}，科室 {row[3]}，角色 {row[5]}，擅长 {row[4]}。",
    )))
    documents.extend(_query_documents(conn, "SCHEDULE", "doctor", """
        select s.id, d.name, p.name, s.work_date, s.period, s.capacity, s.status
        from doctor.schedule s
        join doctor.staff d on d.id = s.staff_id
        join doctor.department p on p.id = s.department_id
        where s.status = 'PUBLISHED'
    """, lambda row: KnowledgeSource(
        source_id=f"schedule-{row[0]}",
        source_type="SCHEDULE",
        business_id=row[0],
        title=f"排班：{row[1]} {row[3]} {row[4]}",
        content=f"医生 {row[1]}，科室 {row[2]}，日期 {row[3]}，时段 {row[4]}，号源容量 {row[5]}，状态 {row[6]}。",
    )))
    documents.extend(_query_documents(conn, "DOCTOR_EVENT", "doctor", """
        select e.id, d.name, p.name, e.event_type, sl.event_date, sl.period, coalesce(e.note, '')
        from doctor.doctor_event e
        join doctor.staff d on d.id = e.staff_id
        join doctor.department p on p.id = d.department_id
        join doctor.doctor_event_slot sl on sl.event_id = e.id
    """, lambda row: KnowledgeSource(
        source_id=f"doctor-event-{row[0]}-{row[4]}-{row[5]}",
        source_type="DOCTOR_EVENT",
        business_id=row[0],
        title=f"医生安排：{row[1]} {row[4]} {row[5]}",
        content=f"医生 {row[1]}，科室 {row[2]}，安排类型 {row[3]}，日期 {row[4]}，时段 {row[5]}，备注 {row[6]}。",
    )))
    documents.extend(_query_documents(conn, "MEDICAL_ITEM", "doctor", """
        select code, name, category, price from doctor.medical_item where active = true
    """, lambda row: KnowledgeSource(
        source_id=f"medical-item-{row[0]}",
        source_type="MEDICAL_ITEM",
        business_id=row[0],
        title=f"医技项目：{row[1]}",
        content=f"{row[1]}，类型 {row[2]}，院内价格 {row[3]} 元。开单后需缴费，缴费后进入对应医技队列。",
    )))
    documents.extend(_query_documents(conn, "DRUG", "pharmacy", """
        select code, drug_name, specification, unit, unit_price
        from pharmacy.drug where active = true
    """, lambda row: KnowledgeSource(
        source_id=f"drug-{row[0]}",
        source_type="DRUG",
        business_id=row[0],
        title=f"药品：{row[1]}",
        content=f"{row[1]}，规格 {row[2]}，单位 {row[3]}，价格 {row[4]} 元。处方必须由医生确认并缴费后由药房发药。",
    )))
    return documents


def _query_documents(conn, source_type: str, schema_name: str, sql: str, mapper) -> list[KnowledgeSource]:
    try:
        with conn.cursor() as cur:
            cur.execute("select exists(select 1 from information_schema.schemata where schema_name = %s)", (schema_name,))
            if not cur.fetchone()[0]:
                return []
            cur.execute(sql)
            return [mapper(row) for row in cur.fetchall()]
    except Exception as exc:
        conn.rollback()
        logger.warning("Skipping RAG source %s because hospital data query failed: %s", source_type, exc)
        return []


def _vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(str(value) for value in vector) + "]"


def _identifier(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value):
        raise ValueError("Invalid PostgreSQL identifier")
    return value


def _connect(config):
    return psycopg.connect(
        config.rag_database_url,
        connect_timeout=config.rag_connect_timeout_seconds,
    )
