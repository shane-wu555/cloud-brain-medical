create table if not exists patient (
    id                 uuid        primary key default gen_random_uuid(),
    name               varchar(64) not null,
    gender             varchar(8)  not null default 'UNKNOWN'
                                   check (gender in ('MALE','FEMALE','UNKNOWN')),
    birth_date         date,
    id_type            varchar(24) check (id_type in ('ID_CARD','PASSPORT','HK_MACAO_TAIWAN','OTHER')),
    id_number          varchar(64),
    phone              varchar(16),
    real_name_verified boolean     not null default false,
    verified_at        timestamptz,
    created_source     varchar(8)  not null default 'ONLINE'
                                   check (created_source in ('ONLINE','OFFLINE')),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);
create index idx_patient_id_card on patient(id_type, id_number) where id_number is not null;
create index idx_patient_phone   on patient(phone)               where phone    is not null;

-- 一个 App 账号可以绑定多个患者档案（本人 + 家属）
create table if not exists account_binding (
    account_id varchar(64) not null,   -- auth.user_account.id
    patient_id uuid        not null,   -- patient.id
    is_default boolean     not null default false,
    created_at timestamptz not null default now(),
    primary key (account_id, patient_id)
);
create index idx_binding_account on account_binding(account_id);
create index idx_binding_patient on account_binding(patient_id);
