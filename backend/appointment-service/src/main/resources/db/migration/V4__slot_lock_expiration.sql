alter table appointment add column if not exists lock_expires_at timestamptz;
update appointment set lock_expires_at = created_at + interval '15 minutes'
where status = 'PENDING_PAYMENT' and lock_expires_at is null;
create index if not exists idx_appointment_expired_lock
    on appointment(status, lock_expires_at) where status='PENDING_PAYMENT';
