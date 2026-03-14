# Backend Test Runbook

## 1. 목적
백엔드 테스트를 동일 절차로 1회 이상 재현 가능하게 한다.

## 2. 표준 실행 명령
- 기본: `./gradlew --offline --no-daemon test`

## 3. 전제조건
- JDK: 17
- Gradle 실행 방식: Wrapper (`./gradlew`)
- DB 필요 여부: 별도 설치 불필요 (테스트 시 H2 사용)
- 환경변수: X
- Spring Profile: 수동 지정 없음 (`application-test.properties` 사용)
- OS/쉘 주의사항: Windows 환경에서 작업. 셸에 따라 Wrapper 실행 형식이 다를 수 있으나, 현재 표준 테스트 명령은 `./gradlew --offline --no-daemon test`

## 4. 실행 절차
1. 프로젝트 루트로 이동
2. Java 버전 확인
3. 환경변수 설정
4. 테스트 명령 실행
5. 결과 로그 저장

## 5. 실패 원인 분류
- Toolchain: JDK, Gradle, Wrapper 문제
- Dependency: 의존성/캐시 문제
- Environment: 환경변수, profile, 경로 문제
- Infrastructure: DB, 포트, 외부 서비스 문제
- Test/Data: fixture, seed, 테스트 순서 의존 문제

## 6. 최근 실행 결과
- 날짜: `2026-03-14`
- 명령: `./gradlew --offline --no-daemon test`
- 성공/실패: 성공
- 첫 차단 요인: 없음
- 분류: 해당 없음
- 우회안: 해당 없음