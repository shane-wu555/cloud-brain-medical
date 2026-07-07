create extension if not exists pg_trgm;

create index if not exists idx_department_name_trgm
    on department using gin (name gin_trgm_ops)
    where active;

create index if not exists idx_department_description_trgm
    on department using gin (description gin_trgm_ops)
    where active and description is not null;

create index if not exists idx_staff_outpatient_name_trgm
    on staff using gin (name gin_trgm_ops)
    where active and role_type = 'OUTPATIENT_DOCTOR';

create index if not exists idx_staff_outpatient_title_trgm
    on staff using gin (title gin_trgm_ops)
    where active and role_type = 'OUTPATIENT_DOCTOR' and title is not null;

create index if not exists idx_staff_outpatient_specialty_trgm
    on staff using gin (specialty gin_trgm_ops)
    where active and role_type = 'OUTPATIENT_DOCTOR' and specialty is not null;
