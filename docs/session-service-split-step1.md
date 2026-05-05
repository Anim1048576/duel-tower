# SessionService 분해 1단계 메모

목표: `SessionService`의 현재 책임을 분류하고, 다음 단계에서 조회 전용 서비스를 먼저 분리하기 위한 기준을 남긴다.

## 1. 현재 책임 분류

### 1) 세션 생명주기
- `createSession`
- `get`
- `withSessionLock`
- `deleteSession`
- `cleanupExpiredSessions`
- `evictExpiredSessions`
- `isExpired`
- 관련 내부 헬퍼: `generateCode`, `generateGmToken`

### 2) 로비 / 참가자 관리
- `join`
- `issuePlayerToken`
- `resolvePlayerIdByToken`
- `leaveSession`
- `setPlayerReady`
- `kickPlayer`
- `resetSession`
- 관련 내부 헬퍼:
  `removePlayerFromSession`, `resetSessionState`, `resetPlayerState`, `removePlayerRuntimeState`

### 3) 로드아웃 / 덱 / 프리셋
- `updateDeck`
- `updateLoadout`
- `forgetOwnedCard`
- 관련 내부 헬퍼:
  `applyLoadoutToPlayer`, `applyLoadout`, `validateDeckBuild`, `validateDeckEditableState`,
  `persistCharacterDeck`, `resolveJoinDeckOwnedCardIds`, `resolveRequestedDeckOwnedCardIds`,
  `resolveLoadoutOwnedCards`, `resolveLoadoutPassiveIds`, `resolveLoadoutDeckOwnedCardIds`,
  `resolveLoadoutExCardId`, `validateExCardId`, `loadDeck`, `shuffleDeck`

### 4) 조회 전용
- 현재 `SessionService` 내부의 명시적 조회 메서드는 사실상 `get`, `withSessionLock` 뿐이다.
- 실제 조회 응답 조립은 `SessionController`의 아래 엔드포인트에 흩어져 있다.
  - `GET /api/sessions/{code}`
  - `GET /api/sessions/{code}/state`
  - `GET /api/sessions/{code}/run`
  - `GET /api/sessions/{code}/inventory`
  - `GET /api/sessions/{code}/results`
  - `GET /api/sessions/{code}/recent-results`
  - `GET /api/sessions/{code}/choices`
- 공통 흐름:
  `sessionService.get(code)` -> `SessionAccessResolver.requireSessionReadable(...)` -> `rt.withLock(...)` -> `StateMapper`

### 5) 명령 실행
- 현재 command 처리 책임은 `SessionService`가 아니라 `SessionController#command`에 있다.
- 포함 책임:
  - `CommandRequest` 입력 검증
  - `expectedVersion` 존재 검증
  - `SessionCommandAuthorization` 위임
  - `SessionCommandType` 변환
  - `rt.apply(cmd)` 엔진 위임
  - 이벤트 / 상태 DTO 후처리

## 2. 신규 서비스 분리 후보

작은 단계로 나누면 아래 이름이 자연스럽다.

- `SessionLifecycleService`
  - 세션 생성, 조회 진입, 삭제, 만료 정리
- `SessionLobbyService`
  - join, leave, ready, kick, reset, token 발급/해석
- `SessionLoadoutService`
  - deck, loadout, forget
- `SessionQueryService`
  - run, inventory, results, recent-results, choices, state
- `SessionCommandService`
  - command 처리, expectedVersion 검사, 엔진 위임, command 후속 처리

## 3. 다음 단계 우선 분리 대상: 조회 전용 서비스

우선 분리 메서드/경로 후보:

- `SessionController#state`
- `SessionController#run`
- `SessionController#inventory`
- `SessionController#results`
- `SessionController#recentResults`
- `SessionController#choices`
- 공통 read 헬퍼:
  - `SessionController#requireSessionReadableAndLog`
  - `SessionService#get`
  - `SessionService#withSessionLock`

추천 진행 순서:

1. `SessionQueryService`를 신설한다.
2. `requireSessionReadableAndLog` 성격의 공통 read 진입점을 `SessionQueryService`로 이동한다.
3. 위 조회 엔드포인트들이 `SessionQueryService`만 호출하도록 바꾼다.
4. `StateMapper` 사용 위치를 service 쪽으로 모아 controller 를 얇게 만든다.

## 4. 이번 단계 원칙

- 게임 로직 변경 금지
- public API 시그니처 변경 최소화
- 대규모 파일 이동 보류
- 먼저 경계와 이동 순서를 코드/문서에 드러낸다
