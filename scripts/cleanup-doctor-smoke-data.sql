begin;

-- Clean only the large doctor/schedule smoke dataset.

delete from appointment.slot_inventory
where schedule_id like 'sched-smoke-%';

delete from doctor.doctor_schedule_time_slot
where schedule_id like 'sched-smoke-%'
   or id like 'sched-smoke-%';

delete from doctor.doctor_schedule
where id like 'sched-smoke-%';

delete from doctor.doctor
where id like 'doc-smoke-%';

delete from auth.user_account
where id like 'doc-smoke-%'
   or username like 'doctor-card-%'
   or username like 'doctor-resp-%'
   or username like 'doctor-endo-%'
   or username like 'doctor-dige-%'
   or username like 'doctor-orth-%'
   or username like 'doctor-derm-%'
   or username like 'doctor-pedi-%'
   or username like 'doctor-ent-%';

delete from doctor.department
where id like 'dept-smoke-%';

commit;
