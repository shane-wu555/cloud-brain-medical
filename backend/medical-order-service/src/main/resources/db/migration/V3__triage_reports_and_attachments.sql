alter table medical_order
    add column if not exists urgency varchar(16) not null default 'ROUTINE',
    add column if not exists executor_name varchar(64),
    add column if not exists execution_location varchar(128),
    add column if not exists equipment_id varchar(64),
    add column if not exists queue_number integer,
    add column if not exists triage_created_by_type varchar(16),
    add column if not exists triage_reasons text,
    add column if not exists missed_count integer not null default 0;

create table if not exists medical_executor (
    id varchar(64) primary key, name varchar(64) not null, order_type varchar(32) not null,
    specialties text not null, location varchar(128) not null, equipment_ids text,
    capacity integer not null default 20, active boolean not null default true
);
insert into medical_executor(id,name,order_type,specialties,location,equipment_ids,capacity) values
 ('doctor-check-001','检查医生','CHECK','头部CT,颅脑MRI,神经影像','医技楼2层CT室','CT-01,MRI-01',20),
 ('doctor-lab-001','检验医生','LAB','血常规,生化检验,神经免疫','医技楼1层检验科','LAB-01',40),
 ('doctor-disposal-001','处置医生','DISPOSAL','静脉输液,换药,神经康复','门诊楼3层处置室',null,30)
on conflict(id) do nothing;

create table if not exists medical_attachment (
    id varchar(64) primary key, medical_order_id varchar(64) not null references medical_order(id),
    object_key varchar(512) not null unique, original_name varchar(256) not null,
    content_type varchar(128), size_bytes bigint not null, storage_bucket varchar(128) not null,
    uploaded_by varchar(64) not null, created_at timestamptz not null default now()
);

create table if not exists ai_medical_task (
    id varchar(64) primary key, medical_order_id varchar(64) not null references medical_order(id),
    external_task_id varchar(128) not null unique, task_type varchar(32) not null,
    status varchar(32) not null, model_version varchar(128), raw_output jsonb,
    error_message text, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table if not exists medical_report (
    id varchar(64) primary key, medical_order_id varchar(64) not null unique references medical_order(id),
    report_type varchar(32) not null, status varchar(32) not null default 'DRAFT',
    findings text, conclusion text, advice text,
    created_by_type varchar(16) not null default 'HUMAN', ai_task_id varchar(64),
    ai_original_findings text, ai_original_conclusion text,
    modified_from_ai boolean not null default false,
    confirmed_by varchar(64), confirmed_at timestamptz,
    rejected_by varchar(64), rejected_at timestamptz, rejection_reason varchar(256),
    created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index if not exists idx_order_executor_queue on medical_order(executor_id,status,urgency,queue_number);
