-- Move patient ownership out of patient_profile and into the many-to-many binding table.

alter table account_patient_binding
    drop constraint if exists account_patient_binding_pkey;

insert into account_patient_binding (account_id, patient_id, bound_at)
select p.account_id, p.id, coalesce(p.updated_at, p.created_at, now())
from patient_profile p
where p.account_id is not null
  and not exists (
      select 1
      from account_patient_binding b
      where b.account_id = p.account_id
        and b.patient_id = p.id
  );

delete from account_patient_binding a
using account_patient_binding b
where a.ctid < b.ctid
  and a.account_id = b.account_id
  and a.patient_id = b.patient_id;

alter table account_patient_binding
    add constraint account_patient_binding_pkey primary key (account_id, patient_id);

create index if not exists idx_account_patient_binding_account_current
    on account_patient_binding (account_id, bound_at desc);

create index if not exists idx_account_patient_binding_patient
    on account_patient_binding (patient_id);

drop index if exists uq_patient_profile_account_certificate;
drop index if exists idx_patient_profile_account;

create index if not exists idx_patient_profile_identity
    on patient_profile (name, gender, id_type, id_number);

alter table patient_profile
    drop constraint if exists patient_profile_id_card_key;

alter table patient_profile
    drop column if exists user_id,
    drop column if exists account_id,
    drop column if exists id_card;
