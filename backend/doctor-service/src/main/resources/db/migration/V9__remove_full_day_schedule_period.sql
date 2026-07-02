-- Split legacy full-day schedules into morning/afternoon rows, then disallow
-- full-day as a persisted schedule period.

insert into schedule (id, staff_id, department_id, work_date, period, capacity, status, suspension_reason)
select left(id, 61) || '-pm', staff_id, department_id, work_date, '下午', capacity, status, suspension_reason
from schedule full_day
where period = '全天'
  and not exists (
    select 1
    from schedule existing
    where existing.staff_id = full_day.staff_id
      and existing.work_date = full_day.work_date
      and existing.period = '下午'
  )
on conflict do nothing;

update schedule_slot slot
set schedule_id = afternoon.id
from schedule full_day
join schedule afternoon
  on afternoon.staff_id = full_day.staff_id
 and afternoon.work_date = full_day.work_date
 and afternoon.period = '下午'
where full_day.period = '全天'
  and slot.schedule_id = full_day.id
  and slot.start_time >= time '12:00'
  and not exists (
    select 1
    from schedule_slot existing
    where existing.schedule_id = afternoon.id
      and existing.start_time = slot.start_time
  );

update schedule full_day
set period = '上午',
    capacity = greatest(1, (
      select coalesce(sum(slot.capacity), full_day.capacity)
      from schedule_slot slot
      where slot.schedule_id = full_day.id
        and slot.start_time < time '12:00'
    ))
where period = '全天'
  and not exists (
    select 1
    from schedule existing
    where existing.staff_id = full_day.staff_id
      and existing.work_date = full_day.work_date
      and existing.period = '上午'
      and existing.id <> full_day.id
  );

update schedule_slot slot
set schedule_id = morning.id
from schedule full_day
join schedule morning
  on morning.staff_id = full_day.staff_id
 and morning.work_date = full_day.work_date
 and morning.period = '上午'
 and morning.id <> full_day.id
where full_day.period = '全天'
  and slot.schedule_id = full_day.id
  and slot.start_time < time '12:00'
  and not exists (
    select 1
    from schedule_slot existing
    where existing.schedule_id = morning.id
      and existing.start_time = slot.start_time
  );

delete from schedule_slot slot
using schedule full_day
where full_day.period = '全天'
  and slot.schedule_id = full_day.id;

delete from schedule full_day
where period = '全天'
  and exists (
    select 1
    from schedule existing
    where existing.staff_id = full_day.staff_id
      and existing.work_date = full_day.work_date
      and existing.period = '上午'
      and existing.id <> full_day.id
  );

alter table schedule drop constraint if exists schedule_period_check;
alter table schedule add constraint schedule_period_check check (period in ('上午','下午'));
