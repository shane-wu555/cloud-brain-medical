do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = current_schema()
          and table_name = 'audit_log'
          and column_name = 'username'
    ) and not exists (
        select 1
        from information_schema.columns
        where table_schema = current_schema()
          and table_name = 'audit_log'
          and column_name = 'actor_name'
    ) then
        alter table audit_log rename column username to actor_name;
    end if;
end $$;
