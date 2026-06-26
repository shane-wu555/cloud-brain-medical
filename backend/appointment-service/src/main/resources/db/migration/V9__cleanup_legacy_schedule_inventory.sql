do $$
begin
    if to_regclass('doctor.doctor_schedule_time_slot') is null then
        return;
    end if;

    with appointment_slot_mapping as (
        select
            a.id as appointment_id,
            selected_slot.id as slot_id,
            selected_slot.start_time
        from appointment a
        join doctor.doctor_schedule s on s.id = a.schedule_id
        join lateral (
            select ts.id, ts.start_time
            from doctor.doctor_schedule_time_slot ts
            where ts.schedule_id = s.id
            order by
                case
                    when a.start_time is not null and ts.start_time = a.start_time then 0
                    when a.start_time is null
                         and a.period in ('上午', 'MORNING')
                         and ts.start_time = time '08:00' then 0
                    when a.start_time is null
                         and a.period in ('下午', 'AFTERNOON')
                         and ts.start_time = time '14:00' then 0
                    when a.start_time is null
                         and a.period in ('全天', 'FULL_DAY')
                         and ts.start_time = time '08:00' then 0
                    else 1
                end,
                ts.start_time
            limit 1
        ) selected_slot on true
    )
    update appointment a
    set schedule_id = mapping.slot_id,
        start_time = mapping.start_time
    from appointment_slot_mapping mapping
    where mapping.appointment_id = a.id;

    delete from slot_inventory inv
    where not exists (
        select 1
        from doctor.doctor_schedule_time_slot ts
        where ts.id = inv.schedule_id
    );

    insert into slot_inventory (schedule_id, capacity, locked, booked)
    select
        ts.id,
        ts.capacity,
        least(ts.capacity, count(a.id) filter (where a.status = 'PENDING_PAYMENT'))::int as locked,
        least(ts.capacity, count(a.id) filter (
            where a.status <> 'CANCELLED'
              and a.status <> 'PENDING_PAYMENT'
              and a.payment_status in ('PAID', 'REFUNDED')
        ))::int as booked
    from doctor.doctor_schedule_time_slot ts
    left join appointment a on a.schedule_id = ts.id
    group by ts.id, ts.capacity
    on conflict (schedule_id) do update
    set capacity = excluded.capacity,
        locked = excluded.locked,
        booked = excluded.booked;
end $$;
