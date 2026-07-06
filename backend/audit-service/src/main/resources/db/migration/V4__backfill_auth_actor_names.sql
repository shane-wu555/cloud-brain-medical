update audit_log log
set actor_name = account.name,
    role = coalesce(log.role, account.role)
from auth.user_account account
where log.service = 'auth-service'
  and log.user_id = account.id
  and (
      log.actor_name is null
      or log.actor_name = ''
      or log.actor_name = log.user_id
      or log.actor_name = coalesce(log.details ->> 'account', '')
      or log.actor_name = coalesce(log.details ->> 'username', '')
  );
