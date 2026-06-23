# AI 智能服务

AI 服务采用 FastAPI 作为统一入口，按文档预留智能问诊、分诊、医生辅助、报告分析、影像识别、RAG 和排班等模块。

## 模块

- `app/consultation`：患者端 AI 智能问诊
- `app/triage`：AI 分诊与检查检验推荐
- `app/clinical_assistance`：医生端病历、检查、处方辅助
- `app/report_analysis`：报告摘要与解读
- `app/image_analysis`：CT/MRI 影像识别
- `app/rag`：医学知识库检索增强
- `app/schedule_suggestions`：AI 医生排班建议

## 当前接口

- `POST /api/ai/consultations`：患者症状摘要、风险分级、科室和医生推荐。
- `POST /api/ai/consultations/{id}/messages`：患者多轮问诊追问和阶段性摘要。
- `POST /api/ai/doctor-recommendations`：结合症状、风险、医生擅长、可用排班和剩余号源推荐医生。
- `POST /api/ai/clinical-assistance`：医生端病情摘要、诊断方向、检查建议和风险提醒。
- `POST /api/ai/triage`：医技分诊建议。
- `POST /api/ai/ct-analysis`：CT 影像异步分析任务，包含进度、RAG 风险来源和 AI 报告草稿。
- `POST /api/ai/tasks/{id}/retry`：重试失败或已完成的 AI 长任务。
- `POST /api/ai/schedule-suggestions`：结合请假、历史量、科室需求和 RAG 规则生成待管理员确认的排班建议。
- `POST /api/ai/report-drafts`：根据医技执行数据生成报告草稿，需人工确认后才能发布。
- `POST /api/ai/prescription-suggestions`：根据诊断、主诉、过敏史和本院药品目录生成用药建议草稿。
- `GET /api/ai/knowledge/search?q=头痛`：检索本院 RAG 知识来源。
- `POST /api/ai/knowledge/reindex`：从业务库重建 pgvector 知识库索引。
- `GET /health`：服务健康检查。

## 大模型接入

默认使用 `AI_PROVIDER=mock`，无需外部密钥即可演示。接入 OpenAI 兼容接口时使用：

```bash
AI_PROVIDER=openai_compatible
AI_OPENAI_BASE_URL=https://api.openai.com/v1
AI_OPENAI_API_KEY=sk-...
AI_OPENAI_MODEL=gpt-4o-mini
AI_TIMEOUT_SECONDS=20
AI_ALLOW_FALLBACK=true
```

`AI_OPENAI_BASE_URL` 可替换为 DeepSeek、通义千问、智谱或本地网关的 OpenAI-compatible 地址。模型输出必须是 JSON，AI 服务会校验后再返回给业务端；失败时默认降级到 Mock，保证主诊疗流程不断。

## pgvector RAG

配置 `AI_RAG_DATABASE_URL` 后，AI 服务会使用 PostgreSQL + pgvector 持久化知识库：

```bash
AI_RAG_DATABASE_URL=postgresql://cloudbrain:cloudbrain@localhost:5432/cloud_brain_medical
AI_RAG_SCHEMA=ai
AI_RAG_EMBEDDING_DIM=64
```

调用 `POST /api/ai/knowledge/reindex` 会读取业务库中的科室、医生擅长、医技项目、药品目录，并合并院内 AI 使用规则，写入 `ai.knowledge_document`。检索失败或未配置数据库时会自动回退到内置规则，便于离线演示。
