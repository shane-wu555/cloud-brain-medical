insert into room_item_capability (room_id, item_code, item_name, priority) values
  ('rm-lab-05', 'PATH-SKIN', '皮肤病理检查', 5),
  ('rm-lab-05', 'PATH-BIOPSY', '组织活检病理检查', 5),
  ('rm-lab-05', 'PATH-SLIDE', '病理切片检查', 5),
  ('rm-lab-01', 'PATH-SKIN', '皮肤病理检查', 30),
  ('rm-lab-01', 'PATH-BIOPSY', '组织活检病理检查', 30),
  ('rm-lab-01', 'PATH-SLIDE', '病理切片检查', 30)
on conflict (room_id, item_code) do update set
  item_name = excluded.item_name,
  priority = excluded.priority;
