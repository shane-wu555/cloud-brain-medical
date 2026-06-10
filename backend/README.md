# 后端微服务

后端采用 Spring Boot 3 + Java 17 的 Maven 多模块结构。每个业务服务保留独立包路径、配置目录和数据库迁移目录，后续可按服务边界补充 Controller、Service、Entity、Mapper 与事件处理。

## 模块

- `gateway-service`：统一网关、路由、鉴权入口
- `auth-service`：认证、权限、RBAC
- `patient-service`：患者信息与健康档案
- `doctor-service`：医生信息与科室关联
- `appointment-service`：挂号、排队、号源
- `medical-record-service`：电子病历
- `inspection-service`：检查检验开单
- `pharmacy-service`：处方与药房库存
- `report-service`：检查报告
- `notification-service`：消息通知
- `audit-service`：操作审计与数据访问日志
- `doctor-schedule-service`：医生排班与 AI 排班结果复核

