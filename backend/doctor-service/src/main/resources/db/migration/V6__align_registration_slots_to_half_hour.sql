-- Align registration schedule slots with outpatient working hours:
-- morning 08:00-11:30, afternoon 14:00-16:30, every 30 minutes.

delete from schedule_slot
where schedule_id = 'sched-00010001-am'
  and start_time not in ('08:00','08:30','09:00','09:30','10:00','10:30','11:00','11:30');

insert into schedule_slot (id, schedule_id, start_time, capacity) values
  ('slot-am-0800','sched-00010001-am','08:00',3),
  ('slot-am-0830','sched-00010001-am','08:30',3),
  ('slot-am-0900','sched-00010001-am','09:00',3),
  ('slot-am-0930','sched-00010001-am','09:30',3),
  ('slot-am-1000','sched-00010001-am','10:00',2),
  ('slot-am-1030','sched-00010001-am','10:30',2),
  ('slot-am-1100','sched-00010001-am','11:00',2),
  ('slot-am-1130','sched-00010001-am','11:30',2)
on conflict (schedule_id, start_time) do update set
  id = excluded.id,
  capacity = excluded.capacity;

delete from schedule_slot
where schedule_id = 'sched-ct-valid'
  and start_time not in ('14:00','14:30','15:00','15:30','16:00','16:30');

insert into schedule_slot (id, schedule_id, start_time, capacity) values
  ('slot-ct-1400','sched-ct-valid','14:00',5),
  ('slot-ct-1430','sched-ct-valid','14:30',5),
  ('slot-ct-1500','sched-ct-valid','15:00',5),
  ('slot-ct-1530','sched-ct-valid','15:30',5),
  ('slot-ct-1600','sched-ct-valid','16:00',5),
  ('slot-ct-1630','sched-ct-valid','16:30',5)
on conflict (schedule_id, start_time) do update set
  id = excluded.id,
  capacity = excluded.capacity;
