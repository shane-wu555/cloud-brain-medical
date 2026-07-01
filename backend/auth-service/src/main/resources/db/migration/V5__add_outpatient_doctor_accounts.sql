-- 为新增门诊医生补充登录账号（密码：abc12345）
insert into user_account (id, username, password, name, role) values
  ('00010004','00010004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','赵医生','OUTPATIENT_DOCTOR'),
  ('00020003','00020003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','周医生','OUTPATIENT_DOCTOR'),
  ('00080003','00080003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','彭医生','OUTPATIENT_DOCTOR')
on conflict (id) do nothing;
