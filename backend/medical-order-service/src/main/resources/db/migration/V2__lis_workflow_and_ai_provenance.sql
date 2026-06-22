alter table medical_order
    add column if not exists result_created_by_type varchar(16) not null default 'HUMAN',
    add column if not exists result_ai_record_id varchar(64),
    add column if not exists result_confirmed_by varchar(64),
    add column if not exists result_confirmed_at timestamp;

alter table medical_order drop constraint if exists chk_medical_order_result_source;
alter table medical_order add constraint chk_medical_order_result_source
    check (result_created_by_type in ('HUMAN', 'AI'));

create table if not exists specimen (
    id varchar(64) primary key,
    medical_order_id varchar(64) not null references medical_order(id),
    specimen_type varchar(64) not null,
    barcode varchar(128) not null unique,
    status varchar(32) not null default 'REQUESTED',
    collector_id varchar(64),
    collected_at timestamp,
    received_at timestamp,
    analyzing_at timestamp,
    reviewed_at timestamp,
    completed_at timestamp,
    discarded_at timestamp,
    discard_reason varchar(255),
    created_at timestamp not null default now(),
    constraint chk_specimen_status check (status in (
        'REQUESTED', 'COLLECTED', 'RECEIVED', 'ANALYZING',
        'REVIEWED', 'EXHAUSTED', 'DISCARDED'
    ))
);
create index if not exists idx_specimen_order on specimen (medical_order_id, created_at);
create index if not exists idx_specimen_status on specimen (status, created_at);

create table if not exists laboratory_result_item (
    id varchar(64) primary key,
    medical_order_id varchar(64) not null references medical_order(id),
    specimen_id varchar(64) not null references specimen(id),
    item_code varchar(64) not null,
    item_name varchar(128) not null,
    result_value varchar(255) not null,
    unit varchar(32),
    reference_range varchar(128),
    abnormal_flag varchar(16),
    created_by_type varchar(16) not null default 'HUMAN',
    ai_record_id varchar(64),
    confirmed_by varchar(64) not null,
    confirmed_at timestamp not null default now(),
    created_at timestamp not null default now(),
    unique (specimen_id, item_code),
    constraint chk_lab_result_source check (created_by_type in ('HUMAN', 'AI')),
    constraint chk_lab_abnormal_flag check (
        abnormal_flag is null or abnormal_flag in ('NORMAL', 'HIGH', 'LOW', 'CRITICAL')
    ),
    constraint chk_ai_lab_result_trace check (
        created_by_type = 'HUMAN' or ai_record_id is not null
    )
);
create index if not exists idx_lab_result_order on laboratory_result_item (medical_order_id, item_code);
