-- 医生签注字段（后续签名功能使用）
alter table medical_record
    add column if not exists doctor_revision_note text;
