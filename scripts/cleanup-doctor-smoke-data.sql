begin;

-- Clean only the large doctor/schedule smoke dataset.

delete from appointment.slot_inventory
where schedule_id like 'sched-smoke-%';

delete from doctor.doctor_schedule_time_slot
where schedule_id like 'sched-smoke-%'
   or id like 'sched-smoke-%';

delete from doctor.doctor_schedule
where id like 'sched-smoke-%';

-- outpatient_doctor 是扩展表，用 doctor_id 关联
delete from doctor.outpatient_doctor
where doctor_id like 'doc-smoke-%';

-- doctor.doctor 是基础档案表
delete from doctor.doctor
where id like 'doc-smoke-%';

delete from auth.user_account
where id like 'doc-smoke-%';

do $$
begin
    if to_regclass('doctor.outpatient_clinic_room') is not null then
        delete from doctor.outpatient_clinic_room
        where department_id like 'dept-smoke-%';
    end if;
end $$;

delete from doctor.department
where id like 'dept-smoke-%';

commit;
