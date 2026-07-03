alter table drug
    add column if not exists dosage_form varchar(32) not null default '片剂',
    add column if not exists storage_condition varchar(64) not null default '常温';

create index if not exists idx_drug_dosage_form on drug(dosage_form, drug_name);
create index if not exists idx_drug_storage_condition on drug(storage_condition);

with metadata(code, dosage_form, storage_condition) as (
    values
        ('DRUG-ASPIRIN', '肠溶片', '常温'),
        ('DRUG-CLOPIDOGR', '片剂', '常温'),
        ('DRUG-WARFARIN', '片剂', '常温'),
        ('DRUG-LEVETIRAC', '片剂', '常温'),
        ('DRUG-VALPROATE', '缓释片', '常温'),
        ('DRUG-MANNITOL', '注射液', '常温'),
        ('DRUG-EDARAVONE', '注射液', '避光常温'),
        ('DRUG-ATORVAST', '片剂', '常温'),
        ('DRUG-METOPROLOL', '缓释片', '常温'),
        ('DRUG-AMLODIPINE', '片剂', '常温'),
        ('DRUG-LISINOPRIL', '片剂', '常温'),
        ('DRUG-IVABRADINE', '片剂', '常温'),
        ('DRUG-PANTOPRAZ', '肠溶胶囊', '常温'),
        ('DRUG-OMEPRAZOLE', '肠溶胶囊', '常温'),
        ('DRUG-DOMPERIDON', '片剂', '常温'),
        ('DRUG-BISMUTH', '颗粒剂', '常温'),
        ('DRUG-LACTULOSE', '口服溶液', '常温'),
        ('DRUG-INSULIN', '注射液', '冷藏2-8℃'),
        ('DRUG-METFORMIN', '缓释片', '常温'),
        ('DRUG-GLIPIZIDE', '缓释片', '常温'),
        ('DRUG-SITAGLIPTIN', '片剂', '常温'),
        ('DRUG-SALBUTAMOL', '气雾剂', '常温'),
        ('DRUG-TIOTROPIUM', '吸入粉雾剂', '阴凉干燥'),
        ('DRUG-BUDESONIDE', '吸入混悬液', '避光常温'),
        ('DRUG-AZITHRO', '胶囊', '常温'),
        ('DRUG-CELECOXIB', '胶囊', '常温'),
        ('DRUG-CALCIUM', '片剂', '常温'),
        ('DRUG-ALENDRONATE', '片剂', '常温'),
        ('DRUG-DEXAMETH', '注射液', '避光常温'),
        ('DRUG-DIAZEPAM', '注射液', '避光常温'),
        ('DRUG-VITAMIN-B12', '片剂', '常温'),
        ('DRUG-FOLIC-ACID', '片剂', '常温'),
        ('DRUG-VIT-C', '泡腾片', '阴凉干燥')
)
update drug d
set dosage_form = metadata.dosage_form,
    storage_condition = metadata.storage_condition
from metadata
where d.code = metadata.code;

with new_drugs(code, drug_name, specification, unit, unit_price, dosage_form, storage_condition, quantity, warning_threshold) as (
    values
        ('DRUG-ACYCLOVIR-TAB', '阿昔洛韦片', '0.1g×24片', '盒', 16.80, '片剂', '常温', 280, 45),
        ('DRUG-ACYCLOVIR-CREAM', '阿昔洛韦乳膏', '10g:0.3g', '支', 8.60, '乳膏剂', '阴凉干燥', 180, 25),
        ('DRUG-ALUMINUM-MG', '铝碳酸镁咀嚼片', '0.5g×20片', '盒', 19.50, '咀嚼片', '常温', 260, 40),
        ('DRUG-AMOXICILLIN', '阿莫西林胶囊', '0.25g×24粒', '盒', 14.80, '胶囊', '常温', 420, 70),
        ('DRUG-AMOX-CLAV', '阿莫西林克拉维酸钾片', '0.375g×12片', '盒', 42.00, '片剂', '常温', 240, 42),
        ('DRUG-AMBROXOL-TAB', '盐酸氨溴索片', '30mg×20片', '盒', 12.00, '片剂', '常温', 360, 60),
        ('DRUG-AMBROXOL-INJ', '盐酸氨溴索注射液', '15mg/2ml', '支', 6.50, '注射液', '避光常温', 260, 45),
        ('DRUG-BETAHISTINE', '甲磺酸倍他司汀片', '6mg×30片', '盒', 18.00, '片剂', '常温', 260, 38),
        ('DRUG-BISOPROLOL', '富马酸比索洛尔片', '5mg×10片', '盒', 22.00, '片剂', '常温', 220, 35),
        ('DRUG-CEFACLOR', '头孢克洛胶囊', '0.25g×12粒', '盒', 28.00, '胶囊', '常温', 260, 45),
        ('DRUG-CEFTRIAXONE', '注射用头孢曲松钠', '1.0g/瓶', '瓶', 9.80, '粉针剂', '阴凉干燥', 360, 60),
        ('DRUG-CEFUROXIME', '头孢呋辛酯片', '0.25g×12片', '盒', 35.00, '片剂', '常温', 280, 48),
        ('DRUG-CETIRIZINE', '盐酸西替利嗪片', '10mg×12片', '盒', 9.90, '片剂', '常温', 300, 45),
        ('DRUG-CHLORHEXIDINE', '复方氯己定含漱液', '200ml/瓶', '瓶', 18.50, '外用溶液', '阴凉干燥', 160, 25),
        ('DRUG-CINNARIZINE', '桂利嗪片', '25mg×100片', '瓶', 8.00, '片剂', '常温', 240, 32),
        ('DRUG-CLARITHRO', '克拉霉素片', '0.25g×6片', '盒', 32.00, '片剂', '常温', 220, 38),
        ('DRUG-DICLOFENAC-GEL', '双氯芬酸二乙胺乳胶剂', '20g/支', '支', 25.00, '凝胶剂', '阴凉干燥', 210, 32),
        ('DRUG-DICLOFENAC-SR', '双氯芬酸钠缓释片', '75mg×10片', '盒', 18.00, '缓释片', '常温', 260, 40),
        ('DRUG-DIPHENHYDRAMINE', '盐酸苯海拉明片', '25mg×24片', '盒', 6.50, '片剂', '常温', 220, 32),
        ('DRUG-EMPAGLIFLOZIN', '恩格列净片', '10mg×10片', '盒', 54.00, '片剂', '常温', 150, 24),
        ('DRUG-ESOMEPRAZOLE', '艾司奥美拉唑镁肠溶片', '20mg×7片', '盒', 38.00, '肠溶片', '常温', 240, 40),
        ('DRUG-FAMOTIDINE', '法莫替丁片', '20mg×24片', '盒', 10.00, '片剂', '常温', 260, 38),
        ('DRUG-FEXOFENADINE', '盐酸非索非那定片', '60mg×12片', '盒', 26.00, '片剂', '常温', 210, 32),
        ('DRUG-FUROSEMIDE-TAB', '呋塞米片', '20mg×100片', '瓶', 12.00, '片剂', '常温', 240, 35),
        ('DRUG-FUROSEMIDE-INJ', '呋塞米注射液', '20mg/2ml', '支', 2.20, '注射液', '避光常温', 300, 55),
        ('DRUG-GABAPENTIN', '加巴喷丁胶囊', '0.3g×24粒', '盒', 39.00, '胶囊', '常温', 200, 30),
        ('DRUG-GINKGO', '银杏叶片', '19.2mg×24片', '盒', 24.00, '片剂', '阴凉干燥', 230, 34),
        ('DRUG-GLYCEROL-FRUCTOSE', '甘油果糖氯化钠注射液', '250ml/瓶', '瓶', 16.00, '注射液', '常温', 180, 36),
        ('DRUG-HEPARIN', '肝素钠注射液', '12500U/2ml', '支', 11.00, '注射液', '冷藏2-8℃', 180, 35),
        ('DRUG-HYDROCORTISONE', '氢化可的松注射液', '10mg/2ml', '支', 4.80, '注射液', '避光常温', 240, 42),
        ('DRUG-IBUPROFEN-SUSP', '布洛芬混悬液', '100ml/瓶', '瓶', 18.80, '口服混悬液', '阴凉干燥', 220, 40),
        ('DRUG-IBUPROFEN-CAP', '布洛芬缓释胶囊', '0.3g×20粒', '盒', 16.00, '缓释胶囊', '常温', 320, 55),
        ('DRUG-IPRATROPIUM', '异丙托溴铵吸入溶液', '2ml:0.5mg×10支', '盒', 68.00, '吸入溶液', '避光常温', 150, 28),
        ('DRUG-ISOSORBIDE', '单硝酸异山梨酯缓释片', '40mg×24片', '盒', 31.00, '缓释片', '常温', 220, 34),
        ('DRUG-KCL-SR', '氯化钾缓释片', '0.5g×24片', '盒', 12.00, '缓释片', '常温', 260, 42),
        ('DRUG-LACTOBACILLUS', '双歧杆菌三联活菌胶囊', '0.21g×24粒', '盒', 34.00, '胶囊', '冷藏2-8℃', 160, 30),
        ('DRUG-LANREOTIDE', '注射用醋酸兰瑞肽', '40mg/瓶', '瓶', 680.00, '冻干粉针剂', '冷藏2-8℃', 40, 12),
        ('DRUG-LEVOFLOXACIN', '左氧氟沙星片', '0.5g×4片', '盒', 18.00, '片剂', '避光常温', 260, 45),
        ('DRUG-LIDOCAINE', '盐酸利多卡因注射液', '0.1g/5ml', '支', 3.00, '注射液', '避光常温', 300, 50),
        ('DRUG-LORATADINE', '氯雷他定片', '10mg×6片', '盒', 8.50, '片剂', '常温', 320, 50),
        ('DRUG-MAGNESIUM-SULFATE', '硫酸镁注射液', '2.5g/10ml', '支', 4.50, '注射液', '常温', 220, 35),
        ('DRUG-MECOBALAMIN', '甲钴胺片', '0.5mg×20片', '盒', 19.00, '片剂', '避光常温', 280, 42),
        ('DRUG-MONTELUKAST', '孟鲁司特钠片', '10mg×5片', '盒', 35.00, '片剂', '常温', 220, 34),
        ('DRUG-MOXIFLOXACIN', '盐酸莫西沙星片', '0.4g×3片', '盒', 52.00, '片剂', '避光常温', 150, 25),
        ('DRUG-NIFEDIPINE-GITS', '硝苯地平控释片', '30mg×7片', '盒', 28.00, '控释片', '常温', 260, 40),
        ('DRUG-NITROGLYCERIN', '硝酸甘油片', '0.5mg×100片', '瓶', 15.00, '片剂', '避光阴凉', 180, 35),
        ('DRUG-ONDANSETRON', '盐酸昂丹司琼注射液', '4mg/2ml', '支', 12.00, '注射液', '避光常温', 200, 36),
        ('DRUG-ORLISTAT', '奥利司他胶囊', '0.12g×21粒', '盒', 56.00, '胶囊', '常温', 120, 18),
        ('DRUG-OSMOLYTE', '复方电解质注射液', '500ml/袋', '袋', 22.00, '注射液', '常温', 160, 35),
        ('DRUG-PARACETAMOL', '对乙酰氨基酚片', '0.5g×12片', '盒', 6.80, '片剂', '常温', 420, 70),
        ('DRUG-PIOGLITAZONE', '盐酸吡格列酮片', '15mg×14片', '盒', 26.00, '片剂', '常温', 180, 28),
        ('DRUG-POTASSIUM-CITRATE', '枸橼酸钾颗粒', '2g×12袋', '盒', 28.00, '颗粒剂', '阴凉干燥', 180, 30),
        ('DRUG-PREDNISONE', '醋酸泼尼松片', '5mg×100片', '瓶', 8.00, '片剂', '常温', 260, 38),
        ('DRUG-PREGABALIN', '普瑞巴林胶囊', '75mg×14粒', '盒', 48.00, '胶囊', '常温', 180, 28),
        ('DRUG-PROBIOTIC-POWDER', '枯草杆菌二联活菌颗粒', '1g×15袋', '盒', 32.00, '颗粒剂', '冷藏2-8℃', 160, 30),
        ('DRUG-PROPAFENONE', '盐酸普罗帕酮片', '50mg×50片', '瓶', 18.00, '片剂', '常温', 180, 26),
        ('DRUG-RABEPRAZOLE', '雷贝拉唑钠肠溶片', '10mg×7片', '盒', 28.00, '肠溶片', '常温', 260, 42),
        ('DRUG-ROSUVASTATIN', '瑞舒伐他汀钙片', '10mg×7片', '盒', 30.00, '片剂', '常温', 280, 45),
        ('DRUG-SACUBITRIL', '沙库巴曲缬沙坦钠片', '50mg×14片', '盒', 78.00, '片剂', '常温', 140, 24),
        ('DRUG-SENNOSIDE', '番泻叶颗粒', '10g×6袋', '盒', 12.00, '颗粒剂', '阴凉干燥', 200, 32),
        ('DRUG-SODIUM-BICARB', '碳酸氢钠片', '0.5g×100片', '瓶', 8.50, '片剂', '常温', 260, 40),
        ('DRUG-SPIRONOLACTONE', '螺内酯片', '20mg×100片', '瓶', 16.00, '片剂', '常温', 220, 34),
        ('DRUG-TAMSULOSIN', '盐酸坦索罗辛缓释胶囊', '0.2mg×10粒', '盒', 38.00, '缓释胶囊', '常温', 180, 28),
        ('DRUG-TERBUTALINE', '硫酸特布他林雾化液', '2ml:5mg×20支', '盒', 46.00, '吸入溶液', '避光常温', 150, 28),
        ('DRUG-TRANEXAMIC', '氨甲环酸注射液', '0.5g/5ml', '支', 5.80, '注射液', '避光常温', 220, 36),
        ('DRUG-URSODEOXYCHOLIC', '熊去氧胆酸胶囊', '250mg×25粒', '盒', 58.00, '胶囊', '常温', 140, 22),
        ('DRUG-VALSARTAN', '缬沙坦胶囊', '80mg×7粒', '盒', 22.00, '胶囊', '常温', 280, 44),
        ('DRUG-VANCOMYCIN', '注射用盐酸万古霉素', '0.5g/瓶', '瓶', 68.00, '粉针剂', '阴凉干燥', 120, 24),
        ('DRUG-VITAMIN-D', '维生素D滴剂', '400IU×30粒', '盒', 29.00, '滴剂', '阴凉干燥', 200, 32),
        ('DRUG-ZOLPIDEM', '酒石酸唑吡坦片', '10mg×7片', '盒', 24.00, '片剂', '避光常温', 120, 18)
),
inserted as (
    insert into drug (code, drug_name, specification, unit, unit_price, dosage_form, storage_condition)
    select code, drug_name, specification, unit, unit_price, dosage_form, storage_condition
    from new_drugs
    on conflict (code) do update
    set drug_name = excluded.drug_name,
        specification = excluded.specification,
        unit = excluded.unit,
        unit_price = excluded.unit_price,
        dosage_form = excluded.dosage_form,
        storage_condition = excluded.storage_condition,
        active = true
    returning id, code
)
insert into drug_stock (drug_id, quantity, warning_threshold)
select i.id, n.quantity, n.warning_threshold
from inserted i
join new_drugs n on n.code = i.code
on conflict (drug_id) do update
set warning_threshold = excluded.warning_threshold;
