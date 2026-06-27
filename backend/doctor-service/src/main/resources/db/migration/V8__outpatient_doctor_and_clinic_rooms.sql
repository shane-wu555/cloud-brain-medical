alter table if exists doctor rename to outpatient_doctor;

do $$
begin
    if to_regclass('idx_doctor_department') is not null
       and to_regclass('idx_outpatient_doctor_department') is null then
        alter index idx_doctor_department rename to idx_outpatient_doctor_department;
    end if;
    if to_regclass('uk_doctor_employee_no') is not null
       and to_regclass('uk_outpatient_doctor_employee_no') is null then
        alter index uk_doctor_employee_no rename to uk_outpatient_doctor_employee_no;
    end if;
end $$;

create table if not exists outpatient_clinic_room (
    id varchar(64) primary key,
    department_id varchar(64) not null references department(id),
    name varchar(128) not null,
    location varchar(128),
    active boolean not null default true,
    unique (department_id, name)
);

alter table outpatient_doctor
    add column if not exists clinic_room_id varchar(64) references outpatient_clinic_room(id);

delete from outpatient_doctor
where role_type <> 'OUTPATIENT_DOCTOR';

insert into outpatient_clinic_room(id, department_id, name, location)
select 'room-' || id, id, id || '-clinic-room-1', 'outpatient-building'
from department
on conflict (id) do update
set department_id = excluded.department_id,
    name = excluded.name,
    location = excluded.location;

update outpatient_doctor d
set clinic_room_id = 'room-' || d.department_id
where d.clinic_room_id is null
  and exists (
      select 1 from outpatient_clinic_room r where r.id = 'room-' || d.department_id
  );

alter table outpatient_doctor drop constraint if exists chk_outpatient_doctor_role_type;
alter table outpatient_doctor
    add constraint chk_outpatient_doctor_role_type check (role_type = 'OUTPATIENT_DOCTOR');

do $$
begin
    if to_regclass('auth.user_account') is not null
       and not exists (
           select 1
           from outpatient_doctor d
           left join auth.user_account u on u.id = d.id
           where u.id is null
       )
       and not exists (
           select 1
           from pg_constraint
           where conname = 'fk_outpatient_doctor_user_account'
       ) then
        alter table outpatient_doctor
            add constraint fk_outpatient_doctor_user_account
            foreign key (id) references auth.user_account(id);
    end if;
end $$;

comment on table outpatient_doctor is 'Outpatient doctor profile; id equals auth.user_account.id when auth schema is present.';
comment on column outpatient_doctor.employee_no is 'Employee number. Staff login uses auth.user_account.username.';
comment on column outpatient_doctor.clinic_room_id is 'Default outpatient clinic room.';
