# Backend Test Runbook

## 1. 목적
백엔드 테스트를 동일한 전제조건과 절차로 재현 가능하게 한다.

## 2. 표준 실행 명령
* 표준 검증 명령: `./gradlew --offline --no-daemon test`
* Windows PowerShell/CMD 대응: `.\gradlew.bat --offline --no-daemon test`

## 2.1 사전 캐시 준비 명령
* 목적: 오프라인 테스트에 필요한 Gradle 의존성을 최초 1회 캐시에 채운다.
* 실행 위치: Codex/CI의 setup 또는 prebuild 단계
* 권장 명령:
    * Git Bash / Linux / macOS: `./gradlew --no-daemon test --refresh-dependencies || true`
    * Windows PowerShell/CMD: `.\gradlew.bat --no-daemon test --refresh-dependencies`
* 주의:
    * 이 단계는 표준 검증 명령을 대체하지 않는다.
    * 캐시 준비가 끝난 뒤 실제 검증은 반드시 `--offline`으로 다시 수행한다.

## 3. 전제조건
* JDK: 17
* Gradle 실행 방식: Wrapper 사용
* 필수 Wrapper 파일:
    * `gradlew`
    * `gradlew.bat`
    * `gradle/wrapper/gradle-wrapper.jar`
    * `gradle/wrapper/gradle-wrapper.properties`
* DB 필요 여부: 별도 설치 불필요
* 테스트 DB: H2 사용
* 환경변수: 별도 설정 불필요
* Spring Profile: 수동 지정 없음
* 설정 파일 주의사항:
    * `application-test.properties`는 `test` 프로필이 활성화된 경우에만 적용된다.
    * 별도 프로필 활성화가 없다면 실제 테스트 시 적용되는 설정 파일 위치를 사전에 확인해야 한다.
* 오프라인 실행 조건:
    * `--offline` 옵션 사용 시 필요한 Gradle 의존성이 로컬 캐시에 이미 존재해야 한다.
    * 최초 1회 이상 온라인 환경에서 의존성 다운로드가 완료된 상태여야 한다.
* OS/쉘 주의사항:
    * 현재 표준 명령은 `./gradlew --offline --no-daemon test`
    * Windows PowerShell/CMD에서는 `.\gradlew.bat --offline --no-daemon test` 사용 가능

## 4. 실행 절차
1. 프로젝트 루트로 이동한다.
2. Java 버전을 확인한다.
3. Gradle Wrapper 파일 존재 여부를 확인한다.
4. Codex/CI 환경에서 setup 또는 prebuild 단계가 있다면, 먼저 의존성 캐시를 준비한다.
5. 표준 검증 명령 `./gradlew --offline --no-daemon test` 를 실행한다.
6. 결과 로그를 저장한다.

## 5. 로그 저장 예시
* Git Bash:
    * `./gradlew --offline --no-daemon test | tee test-run.log`
* PowerShell:
    * `.\gradlew.bat --offline --no-daemon test *>&1 | Tee-Object test-run.log`

## 6. 실패 원인 분류
* Toolchain: JDK, Gradle, Wrapper 문제
* Dependency: 의존성 누락, 오프라인 캐시 미존재 문제
* Environment: profile, 설정 파일, 경로 문제
* Infrastructure: DB, 포트, 외부 서비스 문제
* Test/Data: fixture, seed, 테스트 순서 의존 문제

## 7. 점검 포인트
* `java -version` 결과가 17인지 확인
* 프로젝트 루트에 Wrapper 파일이 모두 존재하는지 확인
* 최근 온라인 빌드 이력이 있어 의존성 캐시가 채워져 있는지 확인
* 테스트 설정 파일이 실제로 어떤 방식으로 적용되는지 확인
* 로그 파일이 저장되었는지 확인

## 8. 최근 실행 결과
* 날짜: `2026-03-18`
* 명령: `./gradlew --offline --no-daemon test`
* 성공/실패: 실패
* 첫 차단 요인:
    * `org.springframework.boot:spring-boot-starter-test:4.0.3`
    * `org.springframework.boot:spring-boot-starter-data-jpa-test:4.0.3`
    * `org.springframework.boot:spring-boot-starter-security-test:4.0.3`
    * `org.springframework.boot:spring-boot-starter-thymeleaf-test:4.0.3`
    * `org.springframework.boot:spring-boot-starter-validation-test:4.0.3`
    * `org.springframework.boot:spring-boot-starter-webmvc-test:4.0.3`
* 분류: Dependency / offline cache miss
* 우회안:
    * 정상적인 해결은 Maven Central(`repo.maven.apache.org`)에서 위 테스트 의존성을 1회 캐시한 뒤 다시 `--offline`으로 검증하는 것이다.
    * 일반 검증 경로는 계속 `./gradlew --offline --no-daemon test`를 유지한다.
