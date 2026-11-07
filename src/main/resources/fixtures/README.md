# 운영 시드 데이터 (앱이 처음 뜰 때 테이블이 비어 있으면 읽는다)

- `buildings.json` — 한양대 서울캠퍼스 건물(3D 월드에 좌표가 있는 66곳). id 는 건물 번호(101, 212 …) 이고, 번호가 숫자가 아닌 건물(L01·N08 …)은 1001부터. 프론트 `campusPlaces.json` 의 id 와 같다.
- `departments.json` — 학부 학과 61개와 그 학과의 **행정팀(사무실) 건물**(`defaultBuildingIds`). 수업·연구실 건물과 다를 수 있다.
- 두 파일은 손으로 고치지 말고 프론트 저장소에서 다시 만든다: `BACKEND_DIR=<이 저장소> node scripts/build-departments.mjs` (원본은 campus-data `campus/departments.json`, 출처·확신도 포함).
- 시드는 테이블이 비어 있을 때만 들어간다. 이미 시드된 DB 에서 목록을 바꾸려면 `departments`, `department_buildings`, `buildings` 를 비우고 다시 띄우면 된다 (사용자 프로필이 참조 중이면 먼저 정리).
- `listings.json` — **데모용 샘플 매물 20건 (실제 매물 아님)**. 주소·좌표는 카카오 주소 검색으로 찾은 실제 지번(사근동 9·행당동 8·마장동 3), 금액은 2026-11 기준 실거래 적정가에 맞춰 등급이 적정 10·조금 비쌈 4·비쌈 3·범인 2·판정 보류 1 이 되도록 정했다. 1번이 스펙 데모의 "한양대 도보 1분 11㎡" 범인 매물, 9번(사근동 8㎡)은 표본이 6건뿐이라 판정 보류. 실거래를 다시 수집하면 적정가가 바뀌어 등급이 달라질 수 있다. 이 중 7건(4·6·10·17·18·19·20번)은 면적·방 구조가 비슷한 청년안심주택 평면도(`floorplanId` 101~107, `floorplans/README.md` 대응표 참고)가 연결돼 있어 "실내 보기"가 된다. 나머지는 도면이 없다(null).
- `transfers.json` — **데모용 샘플 양도 글 3건 (실제 글 아님)**. 작성자는 `authorNickname` 으로 데모 사용자를 만들어 연결한다. 판정은 서버가 계산(적정·비쌈·조금 비쌈이 되도록 금액을 정함). 사진은 없음
- `reviews.json` — **데모용 샘플 후기 10건 (실제 후기 아님)**. 매물 1·2·6·10·13번에 2건씩, 작성자는 `authorNickname` 으로 데모 사용자를 만든다. 핀 없음
- `dorms.json` — 학부 재학생 기숙사와 호실 유형. id 는 그 기숙사 건물의 건물 번호(801 …). 좌표는 `buildings` 에서 가져온다. **학기마다 손으로 갱신**: 학생생활관 홈페이지(dormitory.hanyang.ac.kr) → 학부 재학생 모집요강(한양이북 PDF) 의 "모집일반" 표. 현재 값은 2026학년도 2학기 모집요강 4·16쪽(학기 116일 → `semesterMonths` 4, 식비 미포함)
- `floorplan-labels.json` — 청년안심주택 평면도 이미지(soco 단지 상세) 지문(sha256) → 주택형 이름·전용면적. 서버가 이미지를 수집할 때 이 표에 없는 지문은 경고 로그로 알려 준다(새 평면도·바뀐 평면도 → 이미지를 보고 이 표에 추가)
- `floorplans/{id}.json` — 실내 3D 평면도(파일 하나 = `FloorPlan` 하나, 형식은 프론트 `src/api/types.ts`). 다른 시드와 달리 **서버가 뜰 때마다 id 기준으로 덮어쓰고**, 파일을 지우면 테이블에서도 지운다. 잘못된 파일이 있으면 파일명과 이유를 알려 주며 서버가 뜨지 않는다. 이미지↔id 대응표와 id 구간(1~99 청년안심주택 / 101~199 매물 / 201~299 기숙사)은 `floorplans/README.md`. `GET /api/v1/floorplans/{id}` 로 내려간다.
- 청년안심주택 평면도 연결: `floorplan-labels.json` 의 `floorplanId` 를 수집 때 주택형(`housing_unit_types.floorplan_id`)에 붙인다. `"use": "listing"` 인 줄은 매물로 넘긴 평면도라 붙이지 않는다. 그 id 의 `floorplans/{id}.json` 이 아직 없으면 경고만 남기고 건너뛴다.
- 청년안심주택 단지·주택형·공고는 파일이 아니라 서버가 soco 에서 매일 수집한다 (`HOUSING_INGEST_ENABLED=true`)
- 통합 테스트는 `classpath:test-fixtures/` 를 쓰므로 이 파일들과 무관하다.
- 매물(`listings.json`)·기숙사(`dorms.json`)의 `floorplanId` 는 시드가 **테이블이 비어 있을 때만** 들어가는 탓에 이미 시드된 DB 에는 반영되지 않는다. 그래서 서버가 뜰 때마다 `FloorPlanLinker` 가 `floorplan_id` 가 비어 있고 그 평면도가 실제로 있는 행만 채운다 (이미 값이 있는 행은 건드리지 않음, 몇 번을 돌려도 같다).
