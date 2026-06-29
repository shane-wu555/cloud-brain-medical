-- ══════════════════════════════════════════════════════════════════
-- 药品目录（每类 ≥ 5 种，初始库存 300 份，预警阈值 30）
-- ══════════════════════════════════════════════════════════════════
insert into drug (code, drug_name, specification, unit, unit_price) values
  -- 神经科用药
  ('DRUG-ASPIRIN',    '阿司匹林肠溶片',       '100mg×100片',  '盒', 18.50),
  ('DRUG-CLOPIDOGR',  '氯吡格雷片',           '75mg×28片',    '盒', 45.00),
  ('DRUG-WARFARIN',   '华法林钠片',           '3mg×60片',     '盒',  8.00),
  ('DRUG-LEVETIRAC',  '左乙拉西坦片',         '500mg×60片',   '盒', 68.00),
  ('DRUG-VALPROATE',  '丙戊酸钠缓释片',       '500mg×30片',   '盒', 52.00),
  ('DRUG-MANNITOL',   '甘露醇注射液',         '250ml/瓶',     '瓶', 12.00),
  ('DRUG-EDARAVONE',  '依达拉奉注射液',       '30mg/30ml',    '支', 88.00),
  -- 心血管用药
  ('DRUG-ATORVAST',   '阿托伐他汀钙片',       '20mg×7片',     '盒', 32.00),
  ('DRUG-METOPROLOL', '美托洛尔缓释片',       '47.5mg×14片',  '盒', 28.00),
  ('DRUG-AMLODIPINE', '氨氯地平片',           '5mg×14片',     '盒', 15.00),
  ('DRUG-LISINOPRIL', '赖诺普利片',           '10mg×28片',    '盒', 14.00),
  ('DRUG-IVABRADINE', '伊伐布雷定片',         '5mg×28片',     '盒', 85.00),
  -- 消化科用药
  ('DRUG-PANTOPRAZ',  '泮托拉唑肠溶胶囊',     '40mg×14粒',    '盒', 22.00),
  ('DRUG-OMEPRAZOLE', '奥美拉唑肠溶胶囊',     '20mg×14粒',    '盒', 12.50),
  ('DRUG-DOMPERIDON', '多潘立酮片',           '10mg×30片',    '盒',  8.00),
  ('DRUG-BISMUTH',    '枸橼酸铋钾颗粒',       '0.11g×12袋',   '盒', 18.00),
  ('DRUG-LACTULOSE',  '乳果糖口服液',         '100ml/瓶',     '瓶', 28.00),
  -- 内分泌科用药
  ('DRUG-INSULIN',    '胰岛素注射液',         '300U/3ml',     '支', 35.00),
  ('DRUG-METFORMIN',  '二甲双胍缓释片',       '0.5g×30片',    '盒',  9.50),
  ('DRUG-GLIPIZIDE',  '格列齐特缓释片',       '30mg×15片',    '盒', 25.00),
  ('DRUG-SITAGLIPTIN','西格列汀片',           '100mg×14片',   '盒', 72.00),
  -- 呼吸科用药
  ('DRUG-SALBUTAMOL', '沙丁胺醇气雾剂',       '100mcg×200揿', '支', 15.00),
  ('DRUG-TIOTROPIUM', '噻托溴铵粉吸入剂',     '18mcg×30粒',   '盒', 88.00),
  ('DRUG-BUDESONIDE', '布地奈德吸入悬液',     '2mg/2ml×5支',  '盒', 35.00),
  ('DRUG-AZITHRO',    '阿奇霉素胶囊',         '0.25g×6粒',    '盒', 18.00),
  -- 骨科用药
  ('DRUG-CELECOXIB',  '塞来昔布胶囊',         '200mg×6粒',    '盒', 38.00),
  ('DRUG-CALCIUM',    '碳酸钙D3片',           '300mg×60片',   '盒', 25.00),
  ('DRUG-ALENDRONATE','阿仑膦酸钠片',         '70mg×4片',     '盒', 42.00),
  -- 其他
  ('DRUG-DEXAMETH',   '地塞米松磷酸钠注射液', '5mg/1ml',      '支',  3.50),
  ('DRUG-DIAZEPAM',   '地西泮注射液',         '10mg/2ml',     '支',  2.80),
  ('DRUG-VITAMIN-B12','甲钴胺片',             '0.5mg×20片',   '盒', 15.00),
  ('DRUG-FOLIC-ACID', '叶酸片',               '5mg×100片',    '瓶',  3.50),
  ('DRUG-VIT-C',      '维生素C泡腾片',        '1g×10片',      '盒',  8.00)
on conflict (code) do nothing;

-- 库存初始化
insert into drug_stock (drug_id, quantity, warning_threshold)
select id, 300, 30 from drug
on conflict (drug_id) do nothing;
