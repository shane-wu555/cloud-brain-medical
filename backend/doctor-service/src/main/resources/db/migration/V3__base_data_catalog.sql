create table if not exists registration_level (
    code varchar(32) primary key, name varchar(64) not null, fee numeric(10,2) not null, active boolean not null default true
);
create table if not exists settlement_category (
    code varchar(32) primary key, name varchar(64) not null, active boolean not null default true
);
create table if not exists medical_item_catalog (
    code varchar(32) primary key, name varchar(128) not null, category varchar(32) not null,
    price numeric(10,2) not null, active boolean not null default true
);
insert into registration_level(code,name,fee) values
 ('GENERAL','普通门诊',10.00),('SPECIALIST','专家门诊',30.00) on conflict do nothing;
insert into settlement_category(code,name) values
 ('SELF_PAY','自费'),('EMPLOYEE_INSURANCE','职工医保'),('RESIDENT_INSURANCE','居民医保') on conflict do nothing;
insert into medical_item_catalog(code,name,category,price) values
 ('CT-HEAD','头部 CT 平扫','CHECK',260.00),('MRI-BRAIN','颅脑 MRI','CHECK',680.00),
 ('CBC','血常规','LAB',35.00),('LIVER','肝功能','LAB',75.00),
 ('DISP-INFUSION','静脉输液处置','DISPOSAL',25.00),
 ('DRUG-ASPIRIN','阿司匹林肠溶片 100mg','DRUG',18.50),
 ('DRUG-ATORVASTATIN','阿托伐他汀钙片 20mg','DRUG',32.00) on conflict do nothing;
