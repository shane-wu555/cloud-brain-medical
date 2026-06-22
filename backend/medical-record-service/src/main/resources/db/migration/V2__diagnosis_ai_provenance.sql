alter table medical_record
    add column if not exists diagnosis_created_by_type varchar(16) not null default 'HUMAN',
    add column if not exists diagnosis_ai_record_id varchar(64),
    add column if not exists diagnosis_confirmed_by varchar(64),
    add column if not exists diagnosis_confirmed_at timestamp;

alter table medical_record drop constraint if exists chk_medical_record_diagnosis_source;
alter table medical_record add constraint chk_medical_record_diagnosis_source
    check (diagnosis_created_by_type in ('HUMAN', 'AI'));
alter table medical_record drop constraint if exists chk_medical_record_ai_diagnosis_trace;
alter table medical_record add constraint chk_medical_record_ai_diagnosis_trace
    check (diagnosis_created_by_type = 'HUMAN' or diagnosis_ai_record_id is not null);
