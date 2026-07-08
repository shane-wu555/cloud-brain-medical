create extension if not exists pg_trgm;

create index if not exists idx_prescription_status_created_desc
    on prescription(status, created_at desc);

create index if not exists idx_prescription_patient_status_created_desc
    on prescription(patient_id, status, created_at desc);

do $$
declare
    trgm_schema text;
begin
    select n.nspname
    into trgm_schema
    from pg_opclass c
    join pg_am a on a.oid = c.opcmethod
    join pg_namespace n on n.oid = c.opcnamespace
    where c.opcname = 'gin_trgm_ops'
      and a.amname = 'gin'
    limit 1;

    if trgm_schema is not null then
        execute format('create index if not exists idx_prescription_patient_name_trgm on prescription using gin (patient_name %I.gin_trgm_ops)', trgm_schema);
        execute format('create index if not exists idx_prescription_no_trgm on prescription using gin (prescription_no %I.gin_trgm_ops)', trgm_schema);
        execute format('create index if not exists idx_drug_return_patient_name_trgm on drug_return_request using gin (patient_name %I.gin_trgm_ops)', trgm_schema);
        execute format('create index if not exists idx_drug_return_no_trgm on drug_return_request using gin (return_no %I.gin_trgm_ops)', trgm_schema);
    else
        raise notice 'pg_trgm gin_trgm_ops not found; skipping pharmacy trigram indexes';
    end if;
end $$;

create index if not exists idx_drug_return_status_created_desc
    on drug_return_request(status, created_at desc);

create index if not exists idx_drug_return_patient_status_created_desc
    on drug_return_request(patient_id, status, created_at desc);
