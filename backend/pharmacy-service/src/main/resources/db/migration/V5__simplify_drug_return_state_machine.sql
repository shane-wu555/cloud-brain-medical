do $$
declare
    constraint_name text;
begin
    select c.conname into constraint_name
    from pg_constraint c
    join pg_class t on t.oid = c.conrelid
    join pg_namespace n on n.oid = t.relnamespace
    where n.nspname = current_schema()
      and t.relname = 'prescription'
      and c.contype = 'c'
      and pg_get_constraintdef(c.oid) like '%status%'
      and pg_get_constraintdef(c.oid) like '%WAITING_DISPENSE%';

    if constraint_name is not null then
        execute format('alter table prescription drop constraint %I', constraint_name);
    end if;
end $$;

alter table prescription
    add constraint prescription_status_check
    check (status in (
        'DRAFT','CONFIRMED','PENDING_PAYMENT','PAID','WAITING_DISPENSE',
        'DISPENSED','RETURNED','RETURN_PENDING_REFUND','RETURN_REFUNDED','CANCELLED'
    ));

do $$
declare
    constraint_name text;
begin
    select c.conname into constraint_name
    from pg_constraint c
    join pg_class t on t.oid = c.conrelid
    join pg_namespace n on n.oid = t.relnamespace
    where n.nspname = current_schema()
      and t.relname = 'drug_return_request'
      and c.contype = 'c'
      and pg_get_constraintdef(c.oid) like '%PHARMACY_CONFIRMED%';

    if constraint_name is not null then
        execute format('alter table drug_return_request drop constraint %I', constraint_name);
    end if;
end $$;

update drug_return_request set status = 'RETURN_PENDING_REFUND' where status in ('PENDING_VERIFY','PHARMACY_CONFIRMED');
update drug_return_request set status = 'RETURN_REFUNDED' where status = 'PHARMACY_COMPLETED';
update drug_return_request set status = 'RETURNED' where status = 'REJECTED';

alter table drug_return_request
    alter column status set default 'RETURN_PENDING_REFUND';

alter table drug_return_request
    add constraint drug_return_request_status_check
    check (status in ('RETURNED','RETURN_PENDING_REFUND','RETURN_REFUNDED'));

drop index if exists uk_drug_return_active_prescription;

create unique index if not exists uk_drug_return_prescription
    on drug_return_request(prescription_id);
