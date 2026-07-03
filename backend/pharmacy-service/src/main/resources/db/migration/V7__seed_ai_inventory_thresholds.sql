-- Static AI-ready inventory thresholds for the current catalog.
with thresholds(code, warning_threshold) as (
    values
        ('DRUG-ASPIRIN', 55),
        ('DRUG-CLOPIDOGR', 48),
        ('DRUG-WARFARIN', 35),
        ('DRUG-LEVETIRAC', 42),
        ('DRUG-VALPROATE', 40),
        ('DRUG-MANNITOL', 60),
        ('DRUG-EDARAVONE', 38),
        ('DRUG-ATORVAST', 50),
        ('DRUG-METOPROLOL', 52),
        ('DRUG-AMLODIPINE', 55),
        ('DRUG-LISINOPRIL', 48),
        ('DRUG-IVABRADINE', 32),
        ('DRUG-PANTOPRAZ', 50),
        ('DRUG-OMEPRAZOLE', 55),
        ('DRUG-DOMPERIDON', 45),
        ('DRUG-BISMUTH', 40),
        ('DRUG-LACTULOSE', 36),
        ('DRUG-INSULIN', 75),
        ('DRUG-METFORMIN', 60),
        ('DRUG-GLIPIZIDE', 42),
        ('DRUG-SITAGLIPTIN', 32),
        ('DRUG-SALBUTAMOL', 70),
        ('DRUG-TIOTROPIUM', 28),
        ('DRUG-BUDESONIDE', 55),
        ('DRUG-AZITHRO', 65),
        ('DRUG-CELECOXIB', 42),
        ('DRUG-CALCIUM', 38),
        ('DRUG-ALENDRONATE', 30),
        ('DRUG-DEXAMETH', 65),
        ('DRUG-DIAZEPAM', 35),
        ('DRUG-VITAMIN-B12', 40),
        ('DRUG-FOLIC-ACID', 35),
        ('DRUG-VIT-C', 60)
)
update drug_stock stock
set warning_threshold = thresholds.warning_threshold
from drug
join thresholds on thresholds.code = drug.code
where stock.drug_id = drug.id;

with thresholds(code) as (
    values
        ('DRUG-ASPIRIN'),
        ('DRUG-CLOPIDOGR'),
        ('DRUG-WARFARIN'),
        ('DRUG-LEVETIRAC'),
        ('DRUG-VALPROATE'),
        ('DRUG-MANNITOL'),
        ('DRUG-EDARAVONE'),
        ('DRUG-ATORVAST'),
        ('DRUG-METOPROLOL'),
        ('DRUG-AMLODIPINE'),
        ('DRUG-LISINOPRIL'),
        ('DRUG-IVABRADINE'),
        ('DRUG-PANTOPRAZ'),
        ('DRUG-OMEPRAZOLE'),
        ('DRUG-DOMPERIDON'),
        ('DRUG-BISMUTH'),
        ('DRUG-LACTULOSE'),
        ('DRUG-INSULIN'),
        ('DRUG-METFORMIN'),
        ('DRUG-GLIPIZIDE'),
        ('DRUG-SITAGLIPTIN'),
        ('DRUG-SALBUTAMOL'),
        ('DRUG-TIOTROPIUM'),
        ('DRUG-BUDESONIDE'),
        ('DRUG-AZITHRO'),
        ('DRUG-CELECOXIB'),
        ('DRUG-CALCIUM'),
        ('DRUG-ALENDRONATE'),
        ('DRUG-DEXAMETH'),
        ('DRUG-DIAZEPAM'),
        ('DRUG-VITAMIN-B12'),
        ('DRUG-FOLIC-ACID'),
        ('DRUG-VIT-C')
)
update drug_stock stock
set warning_threshold = case
    when drug.unit_price >= 60 then 35
    when drug.unit_price >= 20 then 40
    else 45
end
from drug
where stock.drug_id = drug.id
  and not exists (select 1 from thresholds where thresholds.code = drug.code);
