# 脚本目录

用于放置数据库初始化、迁移、种子数据和运维辅助脚本。

## 患者端联调测试数据

- `seed-patient-app-smoke-data.sql`
  作用：生成患者端登录注册、多就诊人绑定、AI 问诊后线上挂号、缴费、报告、处方等本地联调测试数据。
- `cleanup-patient-app-smoke-data.sql`
  作用：清理上面脚本写入的测试数据，并兼容清理旧版 smoke-data 留下的患者档案。

默认测试账号：

- 已绑定就诊人账号：`13800000011 / abc12345`
  - 默认绑定就诊人：`patient-profile-test-self-001`
  - 额外可切换就诊人：`patient-profile-test-family-001`
- 未添加就诊人账号：`13800000012 / abc12345`

说明：

- 这类数据只用于一次性或本地联调，不建议写入正式 Flyway migration。
- 业务表里的 `patient_id` 现在表示就诊人 ID，不再表示登录账号 ID。
- 如果已经执行过旧版 smoke-data，建议先运行 cleanup，再运行新版 seed。
