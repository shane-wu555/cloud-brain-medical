-- 挂号等级（供 BaseCatalogController 使用）
create table if not exists registration_level (
    code   varchar(32)   primary key,
    name   varchar(64)   not null,
    fee    numeric(10,2) not null,
    active boolean       not null default true
);

insert into registration_level (code, name, fee) values
  ('GENERAL',    '普通门诊',  10.00),
  ('SPECIALIST', '专家门诊',  30.00),
  ('EMERGENCY',  '急诊',      20.00)
on conflict (code) do nothing;

-- 结算类别
create table if not exists settlement_category (
    code   varchar(32) primary key,
    name   varchar(64) not null,
    active boolean     not null default true
);

insert into settlement_category (code, name) values
  ('SELF_PAY',          '自费'),
  ('EMPLOYEE_INSURANCE','职工医保'),
  ('RESIDENT_INSURANCE','居民医保')
on conflict (code) do nothing;
