# 운영 시드 데이터 (앱이 처음 뜰 때 테이블이 비어 있으면 읽는다)

- `buildings.json` — 한양대 서울캠퍼스 건물(3D 월드에 좌표가 있는 66곳). id 는 건물 번호(101, 212 …) 이고, 번호가 숫자가 아닌 건물(L01·N08 …)은 1001부터. 프론트 `campusPlaces.json` 의 id 와 같다.
- `departments.json` — 학부 학과 61개와 그 학과의 **행정팀(사무실) 건물**(`defaultBuildingIds`). 수업·연구실 건물과 다를 수 있다.
- 두 파일은 손으로 고치지 말고 프론트 저장소에서 다시 만든다: `BACKEND_DIR=<이 저장소> node scripts/build-departments.mjs` (원본은 campus-data `campus/departments.json`, 출처·확신도 포함).
- 시드는 테이블이 비어 있을 때만 들어간다. 이미 시드된 DB 에서 목록을 바꾸려면 `departments`, `department_buildings`, `buildings` 를 비우고 다시 띄우면 된다 (사용자 프로필이 참조 중이면 먼저 정리).
- `dorms.json` — 학부 재학생 기숙사와 호실 유형. id 는 그 기숙사 건물의 건물 번호(801 …). 좌표는 `buildings` 에서 가져온다. **학기마다 손으로 갱신**: 학생생활관 홈페이지(dormitory.hanyang.ac.kr) → 학부 재학생 모집요강(한양이북 PDF) 의 "모집일반" 표. 현재 값은 2026학년도 2학기 모집요강 4·16쪽(학기 116일 → `semesterMonths` 4, 식비 미포함)
- `floorplan-labels.json` — 청년안심주택 평면도 이미지(soco 단지 상세) 지문(sha256) → 주택형 이름·전용면적. 서버가 이미지를 수집할 때 이 표에 없는 지문은 경고 로그로 알려 준다(새 평면도·바뀐 평면도 → 이미지를 보고 이 표에 추가)
- `floorplans/{id}.json` — 실내 3D 평면도(파일 하나 = `FloorPlan` 하나, 형식은 프론트 `src/api/types.ts`). 다른 시드와 달리 **서버가 뜰 때마다 id 기준으로 덮어쓰고**, 파일을 지우면 테이블에서도 지운다. 잘못된 파일이 있으면 파일명과 이유를 알려 주며 서버가 뜨지 않는다. 이미지↔id 대응표와 id 구간(1~99 청년안심주택 / 101~199 매물 / 201~299 기숙사)은 `floorplans/README.md`. `GET /api/v1/floorplans/{id}` 로 내려간다.
- `listings.json` — 데모 매물 7건. 주소·금액은 **가상 값**이고 평면도(`floorplanId` 101~107)만 청년안심주택 공개 평면도다. 테이블이 비어 있을 때만 들어간다.
- 청년안심주택 평면도 연결: `floorplan-labels.json` 의 `floorplanId` 를 수집 때 주택형(`housing_unit_types.floorplan_id`)에 붙인다. `"use": "listing"` 인 줄은 매물로 넘긴 평면도라 붙이지 않는다. 그 id 의 `floorplans/{id}.json` 이 아직 없으면 경고만 남기고 건너뛴다.
- 청년안심주택 단지·주택형·공고는 파일이 아니라 서버가 soco 에서 매일 수집한다 (`HOUSING_INGEST_ENABLED=true`)
- 통합 테스트는 `classpath:test-fixtures/` 를 쓰므로 이 파일들과 무관하다.
