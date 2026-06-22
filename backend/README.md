# 后端微服务

后端采用 Spring Boot 3 + Java 17 的 Maven 多模块结构。每个业务服务保留独立包路径、配置目录和数据库迁移目录，后续可按服务边界补充 Controller、Service、Entity、Mapper 与事件处理。

## 模块

- `gateway-service`：统一网关、路由、鉴权入口
- `auth-service`：认证、权限、RBAC
- `patient-service`：患者信息与健康档案
- `doctor-service`：科室、医生、排班与号源展示
- `appointment-service`：挂号、排队、号源库存与可靠业务事件
- `cashier-service`：支付、退款与窗口费用查询
- `medical-record-service`：电子病历
- `medical-order-service`：检查、检验、处置统一医嘱
- `pharmacy-service`：处方与药房库存
- `report-service`：检查报告
- `audit-service`：操作审计与数据访问日志
