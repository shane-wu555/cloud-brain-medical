create table if not exists department (
    id varchar(64) primary key,
    name varchar(128) not null unique,
    description text,
    active boolean not null default true
);
create table if not exists doctor (
    id varchar(64) primary key,
    name varchar(64) not null,
    title varchar(64),
    department_id varchar(64) not null references department(id),
    role_type varchar(32) not null,
    specialty text,
    active boolean not null default true
);
create index if not exists idx_doctor_department on doctor (department_id, active);
create table if not exists doctor_schedule (
    id varchar(64) primary key,
    doctor_id varchar(64) not null references doctor(id),
    department_id varchar(64) not null references department(id),
    work_date date not null,
    period varchar(32) not null,
    capacity integer not null check (capacity > 0),
    status varchar(32) not null default 'PUBLISHED',
    unique (doctor_id, work_date, period)
);
insert into department(id,name,description) values
 ('dept-neuro','神经内科','头痛、眩晕、脑血管疾病'),('dept-imaging','检查科','B超、CT/MRI 等检查'),('dept-general','全科医学','常见病与慢病复诊') on conflict do nothing;
insert into doctor(id,name,title,department_id,role_type,specialty) values
 ('doctor-001','张医生','主任医师','dept-neuro','OUTPATIENT_DOCTOR','头痛与脑血管疾病'),
 ('doctor-002','李医生','副主任医师','dept-imaging','CHECK_DOCTOR','头部 CT/MRI 影像检查'),
 ('doctor-003','陈医生','主治医师','dept-general','OUTPATIENT_DOCTOR','慢病管理') on conflict do nothing;
insert into doctor_schedule(id,doctor_id,department_id,work_date,period,capacity) values
 ('schedule-001','doctor-001','dept-neuro',current_date,'上午',20),
 ('schedule-002','doctor-001','dept-neuro',current_date+1,'下午',18),
 ('schedule-004','doctor-003','dept-general',current_date,'全天',30) on conflict do nothing;
