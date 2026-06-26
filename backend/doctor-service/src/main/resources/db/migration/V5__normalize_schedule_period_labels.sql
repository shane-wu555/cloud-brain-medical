update doctor_schedule
set period = case period
    when 'MORNING' then '上午'
    when 'AFTERNOON' then '下午'
    when 'FULL_DAY' then '全天'
    else period
end
where period in ('MORNING', 'AFTERNOON', 'FULL_DAY');
