-- Keep development registration data aligned with doctor-service schedule slots.
-- Valid outpatient starts are 08:00, 08:30 ... 11:30 and 14:00, 14:30 ... 16:30.

update appointment set slot_id = 'slot-am-0800', start_time = '08:00' where id = '00000000-0000-4000-8000-000000000001'::uuid;
update appointment set slot_id = 'slot-am-0800', start_time = '08:00' where id = '00000000-0000-4000-8000-000000000002'::uuid;
update appointment set slot_id = 'slot-am-0830', start_time = '08:30' where id = '00000000-0000-4000-8000-000000000003'::uuid;
update appointment set slot_id = 'slot-am-0830', start_time = '08:30' where id = '00000000-0000-4000-8000-000000000004'::uuid;
update appointment set slot_id = 'slot-am-0900', start_time = '09:00' where id = '00000000-0000-4000-8000-000000000005'::uuid;
update appointment set slot_id = 'slot-am-0900', start_time = '09:00' where id = '00000000-0000-4000-8000-000000000006'::uuid;
update appointment set slot_id = 'slot-am-0930', start_time = '09:30' where id = '00000000-0000-4000-8000-000000000007'::uuid;
update appointment set slot_id = 'slot-am-0930', start_time = '09:30' where id = '00000000-0000-4000-8000-000000000008'::uuid;
update appointment set slot_id = 'slot-am-1000', start_time = '10:00' where id = '00000000-0000-4000-8000-000000000009'::uuid;
update appointment set slot_id = 'slot-am-1000', start_time = '10:00' where id = '00000000-0000-4000-8000-000000000010'::uuid;
update appointment set slot_id = 'slot-am-1030', start_time = '10:30' where id = '00000000-0000-4000-8000-000000000011'::uuid;
update appointment set slot_id = 'slot-am-1030', start_time = '10:30' where id = '00000000-0000-4000-8000-000000000012'::uuid;
update appointment set slot_id = 'slot-am-1100', start_time = '11:00' where id = '00000000-0000-4000-8000-000000000013'::uuid;
update appointment set slot_id = 'slot-am-1100', start_time = '11:00' where id = '00000000-0000-4000-8000-000000000014'::uuid;
update appointment set slot_id = 'slot-am-1130', start_time = '11:30' where id = '00000000-0000-4000-8000-000000000015'::uuid;

update appointment set slot_id = 'slot-ct-1400', start_time = '14:00' where id = '00000000-0000-4000-8000-000000000016'::uuid;
update appointment set slot_id = 'slot-ct-1400', start_time = '14:00' where id = '00000000-0000-4000-8000-000000000017'::uuid;
update appointment set slot_id = 'slot-ct-1400', start_time = '14:00' where id = '00000000-0000-4000-8000-000000000018'::uuid;
update appointment set slot_id = 'slot-ct-1430', start_time = '14:30' where id = '00000000-0000-4000-8000-000000000019'::uuid;
update appointment set slot_id = 'slot-ct-1430', start_time = '14:30' where id = '00000000-0000-4000-8000-000000000020'::uuid;
update appointment set slot_id = 'slot-ct-1430', start_time = '14:30' where id = '00000000-0000-4000-8000-000000000021'::uuid;
update appointment set slot_id = 'slot-ct-1500', start_time = '15:00' where id = '00000000-0000-4000-8000-000000000022'::uuid;
update appointment set slot_id = 'slot-ct-1500', start_time = '15:00' where id = '00000000-0000-4000-8000-000000000023'::uuid;
update appointment set slot_id = 'slot-ct-1500', start_time = '15:00' where id = '00000000-0000-4000-8000-000000000024'::uuid;
update appointment set slot_id = 'slot-ct-1530', start_time = '15:30' where id = '00000000-0000-4000-8000-000000000025'::uuid;
update appointment set slot_id = 'slot-ct-1530', start_time = '15:30' where id = '00000000-0000-4000-8000-000000000026'::uuid;
update appointment set slot_id = 'slot-ct-1600', start_time = '16:00' where id = '00000000-0000-4000-8000-000000000027'::uuid;
update appointment set slot_id = 'slot-ct-1600', start_time = '16:00' where id = '00000000-0000-4000-8000-000000000028'::uuid;
update appointment set slot_id = 'slot-ct-1630', start_time = '16:30' where id = '00000000-0000-4000-8000-000000000029'::uuid;
update appointment set slot_id = 'slot-ct-1630', start_time = '16:30' where id = '00000000-0000-4000-8000-000000000030'::uuid;

delete from slot_inventory
where slot_id in (
  'slot-am-0815','slot-am-0845','slot-am-0915','slot-am-0945','slot-am-1015','slot-am-1045','slot-am-1115',
  'slot-ct-1300','slot-ct-1310','slot-ct-1320','slot-ct-1330','slot-ct-1340','slot-ct-1410','slot-ct-1420',
  'slot-ct-1440','slot-ct-1510','slot-ct-1520','slot-ct-1540'
);

insert into slot_inventory (slot_id, capacity, locked, booked) values
  ('slot-am-0800',3,0,2),
  ('slot-am-0830',3,0,2),
  ('slot-am-0900',3,0,2),
  ('slot-am-0930',3,0,2),
  ('slot-am-1000',2,0,2),
  ('slot-am-1030',2,0,2),
  ('slot-am-1100',2,0,2),
  ('slot-am-1130',2,0,1),
  ('slot-ct-1400',5,0,3),
  ('slot-ct-1430',5,0,3),
  ('slot-ct-1500',5,0,3),
  ('slot-ct-1530',5,0,2),
  ('slot-ct-1600',5,0,2),
  ('slot-ct-1630',5,0,2)
on conflict (slot_id) do update set
  capacity = excluded.capacity,
  locked = excluded.locked,
  booked = excluded.booked;
