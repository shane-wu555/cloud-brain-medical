create sequence if not exists appointment_business_no_seq start with 100001;
alter table appointment add column if not exists business_no varchar(32);
update appointment set business_no='REG'||to_char(visit_date,'YYYYMMDD')||lpad(nextval('appointment_business_no_seq')::text,6,'0')
where business_no is null;
alter table appointment alter column business_no set default
    ('REG'||to_char(current_date,'YYYYMMDD')||lpad(nextval('appointment_business_no_seq')::text,6,'0'));
alter table appointment alter column business_no set not null;
create unique index if not exists uk_appointment_business_no on appointment(business_no);
