alter table doctor_schedule add column if not exists suspension_reason varchar(256);
alter table doctor_schedule add column if not exists updated_at timestamptz not null default now();
