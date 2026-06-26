create table if not exists doctor_schedule_time_slot (
    id varchar(96) primary key,
    schedule_id varchar(64) not null references doctor_schedule(id),
    start_time time not null,
    capacity integer not null check (capacity > 0),
    unique (schedule_id, start_time)
);

with templates(period, start_time) as (
    values
        ('上午', time '08:00'), ('上午', time '08:30'), ('上午', time '09:00'), ('上午', time '09:30'),
        ('MORNING', time '08:00'), ('MORNING', time '08:30'), ('MORNING', time '09:00'), ('MORNING', time '09:30'),
        ('下午', time '14:00'), ('下午', time '14:30'), ('下午', time '15:00'), ('下午', time '15:30'),
        ('AFTERNOON', time '14:00'), ('AFTERNOON', time '14:30'), ('AFTERNOON', time '15:00'), ('AFTERNOON', time '15:30'),
        ('全天', time '08:00'), ('全天', time '08:30'), ('全天', time '09:00'), ('全天', time '09:30'),
        ('全天', time '14:00'), ('全天', time '14:30'), ('全天', time '15:00'), ('全天', time '15:30'),
        ('FULL_DAY', time '08:00'), ('FULL_DAY', time '08:30'), ('FULL_DAY', time '09:00'), ('FULL_DAY', time '09:30'),
        ('FULL_DAY', time '14:00'), ('FULL_DAY', time '14:30'), ('FULL_DAY', time '15:00'), ('FULL_DAY', time '15:30')
),
expanded as (
    select
        s.id as schedule_id,
        t.start_time,
        s.capacity,
        count(*) over (partition by s.id) as slot_count,
        row_number() over (partition by s.id order by t.start_time) as slot_index
    from doctor_schedule s
    join templates t on t.period = s.period
),
allocated as (
    select
        schedule_id,
        start_time,
        capacity / slot_count
            + case when slot_index <= capacity % slot_count then 1 else 0 end as slot_capacity
    from expanded
)
insert into doctor_schedule_time_slot (id, schedule_id, start_time, capacity)
select
    schedule_id || '-' || to_char(start_time, 'HH24MI'),
    schedule_id,
    start_time,
    greatest(1, slot_capacity)
from allocated
on conflict (schedule_id, start_time) do nothing;
