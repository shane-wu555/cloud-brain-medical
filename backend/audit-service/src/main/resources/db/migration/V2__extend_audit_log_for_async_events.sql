alter table audit_log
    add column if not exists event_id varchar(64);

update audit_log
set event_id = concat('legacy-', id)
where event_id is null;

alter table audit_log
    alter column event_id set not null;

alter table audit_log
    add column if not exists patient_id varchar(64);

alter table audit_log
    add column if not exists business_id varchar(64);

create unique index if not exists uk_audit_event on audit_log(event_id);
create index if not exists idx_audit_patient on audit_log(patient_id, occurred_at desc);
create index if not exists idx_audit_business on audit_log(business_id, occurred_at desc);
