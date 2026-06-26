alter table patient_profile
    add column if not exists id varchar(64),
    add column if not exists account_id varchar(64),
    add column if not exists id_type varchar(32),
    add column if not exists id_number varchar(64);

update patient_profile
set id = user_id,
    account_id = case when user_id like 'patient-offline-%' then null else user_id end,
    id_type = case when id_card is null or id_card = '' then null else 'ID_CARD' end,
    id_number = id_card
where id is null;

alter table patient_profile
    alter column id set not null;

alter table patient_profile
    drop constraint if exists patient_profile_pkey;

alter table patient_profile
    add constraint patient_profile_pkey primary key (id);

create unique index if not exists uq_patient_profile_account_certificate
    on patient_profile (account_id, id_type, id_number)
    where account_id is not null and id_type is not null and id_number is not null;

create index if not exists idx_patient_profile_account
    on patient_profile (account_id, created_at desc);

create table if not exists account_patient_binding (
    account_id varchar(64) primary key,
    patient_id varchar(64) not null references patient_profile(id),
    bound_at timestamptz not null default now()
);

insert into account_patient_binding (account_id, patient_id)
select account_id, min(id)
from patient_profile
where account_id is not null
group by account_id
having count(*) = 1
on conflict (account_id) do nothing;
