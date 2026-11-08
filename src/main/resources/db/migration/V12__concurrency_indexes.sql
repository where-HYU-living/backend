-- 최종 QA(2026-11): 동시 요청으로 생기던 문제와 빠진 인덱스

-- 양도 글 낙관적 잠금: 글 고치기(PUT)와 양도 완료(PATCH)가 동시에 오면 늦게 저장한 쪽이 앞의 상태를 덮어썼다
alter table transfers add column version bigint not null default 0;

-- 계약 만료 알림: 같은 '며칠 전' 알림은 한 번만 (두 번 눌러 생긴 중복은 먼저 지운다)
delete from home_alerts a using home_alerts b
where a.user_id = b.user_id and a.days_before = b.days_before and a.id > b.id;
create unique index ux_home_alerts_user_days on home_alerts (user_id, days_before);

-- 자주 쓰는 조회에 인덱스: 단지 구독자 찾기(09시 알림), 내 양도 글, 내 후기
create index idx_housing_subscriptions_housing on housing_subscriptions (housing_id);
create index idx_transfers_author on transfers (author_id);
create index idx_reviews_user on reviews (user_id);
