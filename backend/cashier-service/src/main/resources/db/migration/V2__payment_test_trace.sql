alter table payment_order
    add column if not exists payment_scene varchar(32) not null default 'BUSINESS',
    add column if not exists channel_trade_no varchar(128),
    add column if not exists callback_received_at timestamp;
create unique index if not exists uk_payment_channel_trade_no
    on payment_order(channel_trade_no) where channel_trade_no is not null;
