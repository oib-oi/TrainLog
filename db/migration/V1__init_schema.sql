create table exercise (
    id           bigint generated always as identity primary key,
    name         varchar(100) not null unique,
    muscle_group varchar(20)  not null,
    description  varchar(500)
);

create table workout (
    id               bigint generated always as identity primary key,
    performed_on     date         not null,
    title            varchar(100) not null,
    notes            varchar(500),
    duration_minutes integer check (duration_minutes > 0)
);

create table workout_set (
    id          bigint generated always as identity primary key,
    workout_id  bigint  not null references workout (id) on delete cascade,
    exercise_id bigint  not null references exercise (id) on delete restrict,
    reps        integer not null check (reps > 0),
    weight_kg   numeric(6, 2) check (weight_kg >= 0)
);

create index workout_set_workout_id_idx on workout_set (workout_id);
create index workout_set_exercise_id_idx on workout_set (exercise_id);
