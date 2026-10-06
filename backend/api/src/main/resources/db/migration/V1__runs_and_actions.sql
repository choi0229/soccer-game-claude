-- 판(run): 시드만 저장한다. 상태는 시드 + 행동 기록을 재생해서 만든다.
create table runs (
    id         uuid primary key,
    seed       bigint      not null,
    created_at timestamptz not null default now()
);

-- 플레이어의 선택 하나하나. 추가 전용: 판마다 1부터 1씩 늘어나는 순번을 붙인다.
create table run_actions (
    run_id      uuid        not null references runs (id),
    seq         integer     not null check (seq >= 1),
    action_type text        not null,
    payload     jsonb       not null,
    created_at  timestamptz not null default now(),
    primary key (run_id, seq)
);

create function run_actions_append_only() returns trigger as
$$
begin
    raise exception 'run_actions 는 추가 전용입니다 (%)', tg_op;
end;
$$ language plpgsql;

create trigger run_actions_no_update_delete
    before update or delete on run_actions
    for each row execute function run_actions_append_only();

create trigger run_actions_no_truncate
    before truncate on run_actions
    for each statement execute function run_actions_append_only();
