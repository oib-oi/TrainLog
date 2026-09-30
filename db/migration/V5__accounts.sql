create table account (
    id            bigint generated always as identity primary key,
    email         varchar(100) not null unique,
    password_hash varchar(100) not null
);

alter table workout add column account_id bigint references account (id) on delete cascade;
alter table exercise add column account_id bigint references account (id) on delete cascade;

drop index workout_performed_on_idx;
create index workout_account_performed_on_idx on workout (account_id, performed_on desc, id desc);

drop index exercise_name_lower_key;
create unique index exercise_account_name_lower_key on exercise (account_id, lower(name)) nulls not distinct;
