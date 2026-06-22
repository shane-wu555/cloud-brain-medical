create table if not exists patient_profile (
    user_id varchar(64) primary key,
    phone varchar(32) not null,
    name varchar(64) not null,
    id_card varchar(18) unique,
    gender varchar(8),
    birth_date date,
    real_name_verified boolean not null default false,
    verified_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
