-- Appointment period is persisted as the concrete visit half-day. "Full day"
-- is now only a patient-facing aggregation label.

update appointment
set period = case when start_time >= time '12:00' then '下午' else '上午' end
where period = '全天';

alter table appointment drop constraint if exists appointment_period_check;
alter table appointment add constraint appointment_period_check check (period in ('上午','下午'));
