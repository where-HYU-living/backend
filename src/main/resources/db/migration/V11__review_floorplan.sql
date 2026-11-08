-- 실내 핀을 찍은 평면도(방) 번호 (계약 v1.8). 한 단지·기숙사에 평면도가 여러 개라 다른 방에 핀이 섞이지 않게 한다.
-- floorplans 처럼 FK 없이 id 만 가리킨다. 핀 없는 후기·예전 핀은 null.
alter table reviews add column floorplan_id bigint;
