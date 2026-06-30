create table if not exists doctor_event (
    id          varchar(64)  primary key,
    staff_id    varchar(64)  not null references staff(id),
    event_type  varchar(16)  not null check (event_type in ('LEAVE','SURGERY')),
    note        text,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create table if not exists doctor_event_slot (
    id          varchar(96)  primary key,
    event_id    varchar(64)  not null references doctor_event(id) on delete cascade,
    event_date  date         not null,
    period      varchar(8)   not null check (period in ('上午','下午')),
    unique (event_id, event_date, period)
);

create index if not exists idx_doctor_event_staff on doctor_event(staff_id);
create index if not exists idx_doctor_event_slot_date on doctor_event_slot(event_date, period);
