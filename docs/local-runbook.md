# Duel Tower Local Runbook

짧고 재현 가능한 기준 경로만 정리한다.

## 1. 전제조건
- JDK 17
- Node.js 22.x
- npm 11.x
- MariaDB 10.x 이상 또는 호환 DB 1개

## 2. 백엔드(Gradle / Spring Boot)

### 표준 빌드
```bash
./gradlew --offline --no-daemon assemble
```

### 표준 테스트
```bash
./gradlew --offline --no-daemon test
```

### 표준 실행
```bash
./gradlew --offline --no-daemon bootRun
```

기본 포트는 `9009`다. 로컬 `.env` 파일을 쓰려면 프로젝트 루트에 아래 값만 채운다.

```properties
DB_URL=jdbc:mariadb://localhost:3306/duel_tower
DB_USERNAME=duelTowerUser
DB_PASSWORD=change-me
```

## 3. 프론트엔드(Svelte / Vite)

프론트는 `duel-tower-ui/` 디렉터리에서만 작업한다.

### 표준 설치
```bash
cd duel-tower-ui
npm ci
```

### 표준 개발 서버
```bash
cd duel-tower-ui
npm run dev -- --host 127.0.0.1
```

### 표준 빌드
```bash
cd duel-tower-ui
npm run build
```

### 표준 점검
```bash
cd duel-tower-ui
npm run check
```

`npm test` 스크립트는 현재 없다. 프론트엔드의 표준 검증 명령은 `npm run check`다.

## 4. 현재 확인된 실행 결과
- `./gradlew --offline --no-daemon assemble`: 성공
- `./gradlew --offline --no-daemon test`: 실패
  - 오프라인 캐시에 `org.springframework.boot:spring-boot-starter-test:4.0.3` 등 테스트 의존성이 없다.
  - 첫 차단 지점은 `repo.maven.apache.org`(Maven Central)에서 받아야 하는 Gradle 테스트 의존성 캐시 부재다.
- `./gradlew --offline --no-daemon bootRun`: 애플리케이션 초기화 실패
  - 기본값 `jdbc:mariadb://localhost:3306/duel_tower` 로 접속을 시도하며, 로컬 MariaDB가 없으면 `Connection refused`가 발생한다.
- `cd duel-tower-ui && npm ci`: 성공
- `cd duel-tower-ui && npm run build`: 성공
- `cd duel-tower-ui && npm run check`: 성공(경고만 남음)
- `cd duel-tower-ui && npm run dev -- --host 127.0.0.1`: Vite 개발 서버 기동 확인

## 5. 다음 작업자가 바로 알면 좋은 점
- 백엔드 테스트는 **코드 문제보다 캐시 문제** 때문에 현재 오프라인에서 막혀 있다.
- 백엔드 실행은 **DB 미기동** 상태에서는 실패한다.
- 프론트엔드는 로컬 기준으로 설치/개발/빌드/타입체크 경로가 모두 정리되어 있다.
