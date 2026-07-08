-- Backfill visit numbers for legacy/dev appointments created before business_no
-- was generated consistently by the application.
select setval(
    'appt_business_no_seq',
    greatest(
        (select coalesce(max(substring(business_no from 11 for 6)::bigint), 0)
         from appointment
         where business_no ~ '^AP[0-9]{14}$'),
        (select last_value from appt_business_no_seq)
    ),
    true
);

update appointment
set business_no = 'AP' || to_char(visit_date, 'YYYYMMDD') || lpad(nextval('appt_business_no_seq')::text, 6, '0')
where nullif(trim(coalesce(business_no, '')), '') is null;

select setval(
    'appt_business_no_seq',
    greatest(
        (select coalesce(max(substring(business_no from 11 for 6)::bigint), 0)
         from appointment
         where business_no ~ '^AP[0-9]{14}$'),
        (select last_value from appt_business_no_seq)
    ),
    true
);
