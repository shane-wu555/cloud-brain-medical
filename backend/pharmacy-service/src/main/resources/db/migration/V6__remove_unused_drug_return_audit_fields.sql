drop table if exists drug_return_item;

alter table drug_return_request
    drop column if exists pharmacist_id,
    drop column if exists pharmacist_opinion,
    drop column if exists verified_at;
