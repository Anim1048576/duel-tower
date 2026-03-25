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
./gradlew assemble
```

### 표준 테스트
```bash
./gradlew test
```

### 표준 실행
```bash
./gradlew bootRun
```

기본 포트는 `9009`다. 로컬 `.env` 파일을 쓰려면 프로젝트 루트에 아래 값만 채운다.

```properties
DB_URL=jdbc:mariadb://localhost:3306/duel_tower
DB_USERNAME=duelTowerUser
DB_PASSWORD=change-me
```

처음 실행하는 환경에서는 Gradle이 테스트 및 실행에 필요한 의존성을 다운로드할 수 있다.  
의존성 재해결이 필요하면 아래 명령을 사용할 수 있다.

```bash
./gradlew test --refresh-dependencies
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

## 4. 현재 확인된 상태
- 백엔드 빌드 기준 명령은 `./gradlew assemble` 이다.
- 백엔드 테스트 기준 명령은 `./gradlew test` 이다.
    - 처음 실행하는 환경에서는 Gradle 테스트 의존성 다운로드가 발생할 수 있다.
    - 네트워크가 제한된 환경에서는 의존성 다운로드 단계에서 실패할 수 있다.
- 백엔드 실행 기준 명령은 `./gradlew bootRun` 이다.
    - 기본값 `jdbc:mariadb://localhost:3306/duel_tower` 로 접속을 시도하므로, 로컬 MariaDB가 없으면 `Connection refused` 가 발생할 수 있다.
- `cd duel-tower-ui && npm ci`: 성공
- `cd duel-tower-ui && npm run build`: 성공
- `cd duel-tower-ui && npm run check`: 성공(경고만 남음)
- `cd duel-tower-ui && npm run dev -- --host 127.0.0.1`: Vite 개발 서버 기동 확인

## 5. 다음 작업자가 바로 알면 좋은 점
- 백엔드의 표준 검증 명령은 `./gradlew test` 다.
- 네트워크가 제한된 환경에서는 백엔드 테스트 실행 전에 Gradle 의존성 다운로드 가능 여부를 먼저 확인하는 편이 좋다.
- 백엔드 실행은 DB 미기동 상태에서는 실패한다.
- 프론트엔드는 로컬 기준으로 설치, 개발, 빌드, 타입체크 경로가 정리되어 있다.

## API error schema

Gameplay/API failures are being normalized onto a small shared shape for the main session flows. The backend now prefers this JSON body for HTTP failures, and command rejections can include the same payload in `errorDetails` while preserving the legacy `errors` array during migration.

```json
{
  "code": "DECK_EDIT_INVALID",
  "category": "RULE",
  "userMessage": "현재 덱에서는 최대 2장까지만 교체할 수 있습니다.",
  "debugMessage": "deck edit invalid: at most 2 cards can be changed (requested 3)",
  "details": { "maxChangedCards": 2, "actualChangedCards": 3 },
  "status": 400,
  "path": "/api/sessions/ABCD1234/players/me/deck"
}
```

Current high-value coverage: session deck edits, forgetting restrictions, and session command rejections such as pending-decision/search-pick validation. Legacy string/message fallbacks still remain available in the frontend for older endpoints.
