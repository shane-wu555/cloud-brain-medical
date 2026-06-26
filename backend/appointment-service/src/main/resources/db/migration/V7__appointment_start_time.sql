alter table appointment
    add column if not exists start_time time;

update appointment
set start_time = case
    when period in ('上午', 'MORNING') then time '08:00'
    when period in ('下午', 'AFTERNOON') then time '14:00'
    when period in ('全天', 'FULL_DAY') then time '08:00'
    else time '08:00'
end
where start_time is null;
