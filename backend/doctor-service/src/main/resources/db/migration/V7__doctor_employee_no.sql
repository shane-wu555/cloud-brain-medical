alter table doctor
    add column if not exists employee_no varchar(32);

with numbered as (
    select
        id,
        case id
            when 'doctor-001' then 'D0001'
            when 'doctor-002' then 'D0002'
            when 'doctor-003' then 'D0003'
            else 'D9' || lpad(row_number() over (order by id)::text, 5, '0')
        end as generated_employee_no
    from doctor
)
update doctor d
set employee_no = numbered.generated_employee_no
from numbered
where d.id = numbered.id
  and (d.employee_no is null or d.employee_no = '');

alter table doctor
    alter column employee_no set not null;

create unique index if not exists uk_doctor_employee_no on doctor(employee_no);
