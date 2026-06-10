# AI 智能服务

AI 服务采用 FastAPI 作为统一入口，按文档预留智能问诊、分诊、医生辅助、报告分析、影像识别、RAG 和排班等模块。

## 模块

- `app/consultation`：患者端 AI 智能问诊
- `app/triage`：AI 分诊与检查检验推荐
- `app/doctor_assistant`：医生端病历、检查、处方辅助
- `app/report_analysis`：报告摘要与解读
- `app/image_analysis`：CT/MRI 影像识别
- `app/rag`：医学知识库检索增强
- `app/scheduling`：AI 医生排班

