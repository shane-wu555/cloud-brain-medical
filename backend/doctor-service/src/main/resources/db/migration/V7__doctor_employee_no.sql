-- employee_no 列已在 V1 中创建并赋初值。
-- 本迁移仅为存量数据兜底（V1 之后通过其他途径写入但未设工号的记录）。

-- 对仍缺少工号的医生补充生成（0090xxxx 段，避免与正式工号冲突）
with missing as (
    select id, row_number() over (order by id) as rn
    from doctor
    where employee_no is null
)
update doctor d
set employee_no = lpad((9000000 + m.rn)::text, 8, '0')
from missing m
where d.id = m.id;

-- 工号格式约束与 NOT NULL（初次建库时 V1 的列已有数据，此处安全加约束）
alter table doctor alter column employee_no set not null;

alter table doctor drop constraint if exists chk_employee_no_format;
alter table doctor add constraint chk_employee_no_format
    check (employee_no similar to '[0-9]{8}');

create unique index if not exists uk_doctor_employee_no on doctor(employee_no);
