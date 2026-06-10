# 微服务模块约定

每个 Spring Boot 微服务建议保持以下包结构：

- `config`：服务配置、安全配置、消息配置
- `controller`：HTTP API 入口
- `service`：业务服务
- `entity`：领域实体或持久化实体
- `mapper`：数据库访问层
- `util`：服务内工具类
- `src/main/resources/db/migration`：Flyway 数据库迁移脚本

