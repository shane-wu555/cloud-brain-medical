alter table prescription
    alter column medical_record_id type varchar(64)
    using medical_record_id::text;
