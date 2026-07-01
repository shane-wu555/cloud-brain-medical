insert into medical_item (code, name, category, price) values
  ('PATH-SKIN', '皮肤病理检查', 'LAB', 180.00),
  ('PATH-BIOPSY', '组织活检病理检查', 'LAB', 220.00),
  ('PATH-SLIDE', '病理切片检查', 'LAB', 160.00)
on conflict (code) do update set
  name = excluded.name,
  category = excluded.category,
  price = excluded.price,
  active = true;
