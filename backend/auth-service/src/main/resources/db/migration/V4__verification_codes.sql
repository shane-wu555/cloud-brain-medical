create table if not exists verification_code (
    id uuid primary key,
    phone varchar(32) not null,
    purpose varchar(32) not null,
    code_hash varchar(128) not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);
create index if not exists idx_verification_code_lookup
    on verification_code (phone, purpose, created_at desc);
