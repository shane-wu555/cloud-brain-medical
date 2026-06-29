-- ══════════════════════════════════════════════════════════════════
-- 科室表：code 前4位即员工工号的科室编码
--   0001 神经内科  0002 全科医学  0003 影像/检查科
--   0004 检验科    0005 处置科    0006 药房
--   0007 收费处    0099 系统管理
-- ══════════════════════════════════════════════════════════════════
create table if not exists department (
    id          varchar(32)  primary key,
    name        varchar(64)  not null unique,
    code        char(4)      not null unique check (code similar to '[0-9]{4}'),
    description text,
    active      boolean      not null default true
);

-- ══════════════════════════════════════════════════════════════════
-- 员工表：id = auth.user_account.id = employee_no
--   工号规则：DDDDSSSS（科室4位 + 科室内序号4位）
-- ══════════════════════════════════════════════════════════════════
create table if not exists staff (
    id            varchar(64)  primary key,
    name          varchar(64)  not null,
    employee_no   char(8)      not null unique check (employee_no similar to '[0-9]{8}'),
    department_id varchar(32)  not null references department(id),
    role_type     varchar(24)  not null check (role_type in (
                      'OUTPATIENT_DOCTOR','CHECK_DOCTOR','LAB_DOCTOR',
                      'DISPOSAL_DOCTOR','PHARMACY_STAFF','CASHIER','ADMIN')),
    title         varchar(64),
    specialty     text,
    active        boolean      not null default true,
    created_at    timestamptz  not null default now()
);
create index idx_staff_dept on staff(department_id, active);
create index idx_staff_role on staff(role_type,     active);

-- ══════════════════════════════════════════════════════════════════
-- 排班（每医生每日每班次唯一）
-- ══════════════════════════════════════════════════════════════════
create table if not exists schedule (
    id                varchar(64)  primary key,
    staff_id          varchar(64)  not null references staff(id),
    department_id     varchar(32)  not null references department(id),
    work_date         date         not null,
    period            varchar(8)   not null check (period in ('上午','下午','全天')),
    capacity          integer      not null check (capacity > 0),
    status            varchar(16)  not null default 'PUBLISHED'
                                   check (status in ('PUBLISHED','SUSPENDED','COMPLETED')),
    suspension_reason text,
    unique (staff_id, work_date, period)
);

-- 时间槽（排班细分）
create table if not exists schedule_slot (
    id          varchar(64) primary key,
    schedule_id varchar(64) not null references schedule(id),
    start_time  time        not null,
    capacity    integer     not null check (capacity > 0),
    unique (schedule_id, start_time)
);

-- ══════════════════════════════════════════════════════════════════
-- 门诊诊室（仅 OUTPATIENT_DOCTOR 使用）
-- ══════════════════════════════════════════════════════════════════
create table if not exists outpatient_room (
    id            varchar(32)  primary key,
    department_id varchar(32)  not null references department(id),
    name          varchar(64)  not null,
    location      varchar(128),
    active        boolean      not null default true,
    unique (department_id, name)
);

create table if not exists outpatient_doctor (
    staff_id varchar(64) primary key references staff(id),
    room_id  varchar(32) references outpatient_room(id)
);

-- ══════════════════════════════════════════════════════════════════
-- 医疗项目目录（检查/检验/处置/药品）
-- ══════════════════════════════════════════════════════════════════
create table if not exists medical_item (
    code     varchar(32)   primary key,
    name     varchar(128)  not null,
    category varchar(16)   not null check (category in ('CHECK','LAB','DISPOSAL','DRUG')),
    price    numeric(10,2) not null check (price >= 0),
    active   boolean       not null default true
);
