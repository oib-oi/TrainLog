alter table exercise drop constraint exercise_name_key;

create unique index exercise_name_lower_key on exercise (lower(name));
