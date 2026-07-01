-- Login accounts for additional outpatient doctors.
-- Password for all seeded staff accounts: abc12345
insert into user_account (id, username, password, name, role) values
  ('00070002','00070002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','许医生','OUTPATIENT_DOCTOR'),
  ('00010005','00010005','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','赵医生','OUTPATIENT_DOCTOR'),
  ('00010006','00010006','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','周医生','OUTPATIENT_DOCTOR'),
  ('00020004','00020004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','吴医生','OUTPATIENT_DOCTOR'),
  ('00030003','00030003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','郑医生','OUTPATIENT_DOCTOR'),
  ('00030004','00030004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','何医生','OUTPATIENT_DOCTOR'),
  ('00040003','00040003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','高医生','OUTPATIENT_DOCTOR'),
  ('00040004','00040004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','马医生','OUTPATIENT_DOCTOR'),
  ('00050003','00050003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','胡医生','OUTPATIENT_DOCTOR'),
  ('00050004','00050004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','唐医生','OUTPATIENT_DOCTOR'),
  ('00060003','00060003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','罗医生','OUTPATIENT_DOCTOR'),
  ('00060004','00060004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','宋医生','OUTPATIENT_DOCTOR'),
  ('00070003','00070003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','邓医生','OUTPATIENT_DOCTOR'),
  ('00070004','00070004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','秦医生','OUTPATIENT_DOCTOR'),
  ('00080004','00080004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','陆医生','OUTPATIENT_DOCTOR')
on conflict (id) do nothing;
