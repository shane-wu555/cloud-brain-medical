alter table if exists medical_executor rename to medical_workspace;

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_name = 'medical_workspace' and column_name = 'order_type'
    ) and not exists (
        select 1 from information_schema.columns
        where table_name = 'medical_workspace' and column_name = 'workspace_type'
    ) then
        alter table medical_workspace rename column order_type to workspace_type;
    end if;
end $$;

alter table medical_workspace
    add column if not exists room_code varchar(64),
    add column if not exists description text;

update medical_workspace
set room_code = id
where room_code is null;

create unique index if not exists uk_medical_workspace_room_code on medical_workspace(room_code);
create index if not exists idx_medical_workspace_type_active on medical_workspace(workspace_type, active);

alter table medical_order
    add column if not exists executor_workspace_id varchar(64),
    add column if not exists executor_workspace_name varchar(64),
    add column if not exists executor_workspace_location varchar(128),
    add column if not exists executing_doctor_id varchar(64),
    add column if not exists executing_doctor_name varchar(64);

update medical_order
set executor_workspace_id = executor_id,
    executor_workspace_name = executor_name,
    executor_workspace_location = execution_location
where executor_workspace_id is null
  and executor_id is not null;

create table if not exists medical_workspace_project (
    workspace_id varchar(64) not null references medical_workspace(id),
    project_code varchar(64) not null,
    project_name varchar(128) not null,
    active boolean not null default true,
    priority integer not null default 100,
    primary key (workspace_id, project_code)
);

insert into medical_workspace_project(workspace_id, project_code, project_name, priority)
select id, trim(item), trim(item), 100
from medical_workspace
cross join lateral regexp_split_to_table(specialties, ',') as item
where trim(item) <> ''
on conflict (workspace_id, project_code) do update
set project_name = excluded.project_name,
    priority = excluded.priority,
    active = true;

insert into medical_workspace_project(workspace_id, project_code, project_name, priority)
values
    ('doctor-check-001', 'CT-HEAD', 'Head CT', 10),
    ('doctor-check-001', 'MRI-BRAIN', 'Brain MRI', 20),
    ('doctor-lab-001', 'CBC', 'Complete blood count', 10),
    ('doctor-lab-001', 'LIVER', 'Liver function', 20),
    ('doctor-disposal-001', 'DISP-INFUSION', 'Infusion disposal', 10)
on conflict (workspace_id, project_code) do update
set project_name = excluded.project_name,
    priority = excluded.priority,
    active = true;

create table if not exists medical_technician (
    id varchar(64) primary key,
    employee_no varchar(32) not null unique,
    name varchar(64) not null,
    role_type varchar(32) not null check (role_type in ('CHECK_DOCTOR', 'LAB_DOCTOR', 'DISPOSAL_DOCTOR')),
    workspace_id varchar(64) not null references medical_workspace(id),
    active boolean not null default true
);

insert into medical_technician(id, employee_no, name, role_type, workspace_id)
values
    ('doctor-check-001', 'D0002', 'Check Doctor', 'CHECK_DOCTOR', 'doctor-check-001'),
    ('doctor-lab-001', 'L0001', 'Lab Doctor', 'LAB_DOCTOR', 'doctor-lab-001'),
    ('doctor-disposal-001', 'T0001', 'Disposal Doctor', 'DISPOSAL_DOCTOR', 'doctor-disposal-001')
on conflict (id) do update
set employee_no = excluded.employee_no,
    name = excluded.name,
    role_type = excluded.role_type,
    workspace_id = excluded.workspace_id,
    active = true;

do $$
begin
    if to_regclass('auth.user_account') is not null
       and not exists (
           select 1
           from medical_technician t
           left join auth.user_account u on u.id = t.id
           where u.id is null
       )
       and not exists (
           select 1
           from pg_constraint
           where conname = 'fk_medical_technician_user_account'
       ) then
        alter table medical_technician
            add constraint fk_medical_technician_user_account
            foreign key (id) references auth.user_account(id);
    end if;
end $$;

create index if not exists idx_medical_order_workspace_queue
    on medical_order(executor_workspace_id, status, urgency, queue_number);

comment on table medical_workspace is 'Medical execution workspace, such as check room, lab room, or disposal room.';
comment on table medical_workspace_project is 'Maps medical projects to workspaces that can execute them.';
comment on table medical_technician is 'Medical technician profile and workspace assignment; id equals auth.user_account.id when auth schema is present.';
