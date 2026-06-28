-- 补充药品目录与初始库存
insert into drug_catalog (id, drug_code, drug_name, specification, unit, unit_price, enabled)
values
  ('drug-ibuprofen',        'DRUG-010', '布洛芬缓释胶囊',       '0.3g×12粒',       '盒',   8.50,  true),
  ('drug-acetaminophen',    'DRUG-011', '对乙酰氨基酚片',       '500mg×20片',      '盒',   5.80,  true),
  ('drug-flunarizine',      'DRUG-012', '氟桂利嗪胶囊',         '5mg×20粒',        '盒',  12.00,  true),
  ('drug-nimodipine',       'DRUG-013', '尼莫地平片',           '30mg×30片',       '盒',  28.50,  true),
  ('drug-betahistine',      'DRUG-014', '甲磺酸倍他司汀片',     '6mg×30片',        '盒',  15.00,  true),
  ('drug-diazepam',         'DRUG-015', '地西泮片',             '2.5mg×20片',      '盒',   6.00,  true),
  ('drug-carbamazepine',    'DRUG-016', '卡马西平片',           '200mg×30片',      '盒',  18.00,  true),
  ('drug-amlodipine',       'DRUG-020', '苯磺酸氨氯地平片',     '5mg×7片',         '盒',  22.00,  true),
  ('drug-metoprolol',       'DRUG-021', '酒石酸美托洛尔片',     '25mg×20片',       '盒',  12.80,  true),
  ('drug-clopidogrel',      'DRUG-022', '硫酸氢氯吡格雷片',     '75mg×7片',        '盒',  48.00,  true),
  ('drug-isosorbide',       'DRUG-023', '单硝酸异山梨酯缓释片', '40mg×30片',       '盒',  32.00,  true),
  ('drug-omeprazole',       'DRUG-030', '奥美拉唑肠溶胶囊',     '20mg×14粒',       '盒',  22.00,  true),
  ('drug-mosapride',        'DRUG-031', '枸橼酸莫沙必利片',     '5mg×12片',        '盒',  14.50,  true),
  ('drug-domperidone',      'DRUG-032', '多潘立酮片',           '10mg×30片',       '盒',   9.80,  true),
  ('drug-amoxicillin',      'DRUG-040', '阿莫西林胶囊',         '0.5g×24粒',       '盒',  12.00,  true),
  ('drug-azithromycin',     'DRUG-041', '阿奇霉素片',           '0.25g×6片',       '盒',  28.00,  true),
  ('drug-levofloxacin',     'DRUG-042', '盐酸左氧氟沙星片',     '0.5g×10片',       '盒',  24.00,  true),
  ('drug-cefuroxime',       'DRUG-043', '头孢呋辛酯片',         '0.25g×10片',      '盒',  36.00,  true),
  ('drug-metformin',        'DRUG-050', '盐酸二甲双胍缓释片',   '0.5g×30片',       '盒',  18.00,  true),
  ('drug-glimepiride',      'DRUG-051', '格列美脲片',           '2mg×15片',        '盒',  25.00,  true),
  ('drug-insulin-glargine', 'DRUG-052', '甘精胰岛素注射液',     '300U/3ml×1支',    '支', 168.00,  true),
  ('drug-levothyroxine',    'DRUG-053', '左甲状腺素钠片',       '50μg×100片',      '瓶',  42.00,  true),
  ('drug-salbutamol',       'DRUG-060', '硫酸沙丁胺醇气雾剂',   '100μg×200揿',     '支',  38.00,  true),
  ('drug-budesonide',       'DRUG-061', '布地奈德鼻喷雾剂',     '64μg/喷×120喷',   '支',  48.00,  true),
  ('drug-acetylcysteine',   'DRUG-062', '乙酰半胱氨酸颗粒',     '0.1g×10袋',       '盒',  18.00,  true),
  ('drug-saline-250',       'DRUG-070', '氯化钠注射液',         '250ml:2.25g',     '袋',   3.50,  true),
  ('drug-glucose-250',      'DRUG-071', '葡萄糖注射液',         '250ml:12.5g',     '袋',   3.50,  true),
  ('drug-vit-b1',           'DRUG-080', '维生素B₁片',           '10mg×100片',      '瓶',   4.50,  true),
  ('drug-vit-b12',          'DRUG-081', '甲钴胺片',             '0.5mg×20片',      '盒',  28.60,  true),
  ('drug-vit-c',            'DRUG-082', '维生素C片',            '0.1g×100片',      '瓶',   5.00,  true),
  ('drug-calcium-d3',       'DRUG-083', '碳酸钙D₃片',           '600mg×60片',      '盒',  22.00,  true)
on conflict (drug_code) do update
  set drug_name     = excluded.drug_name,
      specification = excluded.specification,
      unit          = excluded.unit,
      unit_price    = excluded.unit_price,
      enabled       = excluded.enabled;

insert into drug_inventory (drug_id, quantity, warning_threshold)
select id, 300, 30 from drug_catalog
where id in (
  'drug-ibuprofen','drug-acetaminophen','drug-flunarizine','drug-nimodipine',
  'drug-betahistine','drug-diazepam','drug-carbamazepine',
  'drug-amlodipine','drug-metoprolol','drug-clopidogrel','drug-isosorbide',
  'drug-omeprazole','drug-mosapride','drug-domperidone',
  'drug-amoxicillin','drug-azithromycin','drug-levofloxacin','drug-cefuroxime',
  'drug-metformin','drug-glimepiride','drug-insulin-glargine','drug-levothyroxine',
  'drug-salbutamol','drug-budesonide','drug-acetylcysteine',
  'drug-saline-250','drug-glucose-250',
  'drug-vit-b1','drug-vit-b12','drug-vit-c','drug-calcium-d3'
)
on conflict (drug_id) do nothing;
