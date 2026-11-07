# 운영 시드 데이터 (앱이 처음 뜰 때 테이블이 비어 있으면 읽는다)

- `buildings.json` — 한양대 서울캠퍼스 건물(3D 월드에 좌표가 있는 66곳). id 는 건물 번호(101, 212 …) 이고, 번호가 숫자가 아닌 건물(L01·N08 …)은 1001부터. 프론트 `campusPlaces.json` 의 id 와 같다.
- `departments.json` — 학부 학과 61개와 그 학과의 **행정팀(사무실) 건물**(`defaultBuildingIds`). 수업·연구실 건물과 다를 수 있다.
- 두 파일은 손으로 고치지 말고 프론트 저장소에서 다시 만든다: `BACKEND_DIR=<이 저장소> node scripts/build-departments.mjs` (원본은 campus-data `campus/departments.json`, 출처·확신도 포함).
- 시드는 테이블이 비어 있을 때만 들어간다. 이미 시드된 DB 에서 목록을 바꾸려면 `departments`, `department_buildings`, `buildings` 를 비우고 다시 띄우면 된다 (사용자 프로필이 참조 중이면 먼저 정리).
- 통합 테스트는 `classpath:test-fixtures/` 를 쓰므로 이 파일들과 무관하다.
