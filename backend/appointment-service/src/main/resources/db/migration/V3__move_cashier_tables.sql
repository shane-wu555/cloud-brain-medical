create schema if not exists cashier;

create table if not exists cashier.payment_order (
    id varchar(64) primary key, business_type varchar(32) not null, business_id varchar(64) not null,
    patient_id varchar(64) not null, amount numeric(12,2) not null, payment_method varchar(32) not null,
    status varchar(32) not null, operator_id varchar(64) not null, created_at timestamp not null default now(),
    paid_at timestamp, unique (business_type, business_id)
);
create table if not exists cashier.refund_order (
    id varchar(64) primary key, business_type varchar(32) not null, business_id varchar(64) not null,
    patient_id varchar(64) not null, amount numeric(12,2) not null, reason varchar(255) not null,
    status varchar(32) not null, operator_id varchar(64) not null, created_at timestamp not null default now(),
    refunded_at timestamp, unique (business_type, business_id)
);

insert into cashier.payment_order
select * from payment_order
on conflict (business_type, business_id) do nothing;
insert into cashier.refund_order
select * from refund_order
on conflict (business_type, business_id) do nothing;

drop table refund_order;
drop table payment_order;
