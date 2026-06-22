# 架构说明

平台采用前后端分离、业务微服务、AI 智能服务和医疗数据基础设施分层设计。

## 分层

- 用户交互层：患者端、医生端、管理端、AI 问诊界面、数据看板
- 接入网关层：Gateway / Nginx，负责路由、鉴权、限流和灰度发布
- 业务微服务层：认证、患者、医生与排班、挂号与号源、收费、病历、统一医技医嘱、药房、报告、审计
- AI 智能服务层：问诊、分诊、医生辅助、报告分析、影像识别、RAG、排班
- 数据与基础设施层：PostgreSQL、pgvector、Redis、RabbitMQ、MinIO、Elasticsearch、Prometheus、Grafana、OpenTelemetry、Jaeger
