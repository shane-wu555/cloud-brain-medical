alter table payment_order alter column payment_method drop not null;
alter table payment_order alter column operator_id drop not null;
alter table payment_order add column if not exists failure_reason varchar(256);
create index if not exists idx_payment_pending_created on payment_order(status, created_at);
