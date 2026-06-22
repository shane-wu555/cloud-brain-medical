create table if not exists medical_record_report_link (
    id uuid primary key, medical_record_id varchar(64) not null references medical_record(id),
    medical_order_id varchar(64) not null, report_id varchar(64) not null,
    report_type varchar(32) not null, conclusion text, confirmed_by varchar(64) not null,
    confirmed_at timestamptz not null, created_at timestamptz not null default now(),
    unique(medical_order_id,report_id)
);
