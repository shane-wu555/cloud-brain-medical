-- 病历与检查报告关联（medical-record-service V2 新增）
create table if not exists medical_record_report_link (
    id                uuid        primary key default gen_random_uuid(),
    medical_record_id varchar(64) not null,
    medical_order_id  uuid        not null,
    report_id         uuid        not null,
    report_type       varchar(32) not null,
    conclusion        text,
    confirmed_by      varchar(64),
    confirmed_at      timestamptz,
    unique (medical_order_id, report_id)
);
create index if not exists idx_rrl_record on medical_record_report_link(medical_record_id);
