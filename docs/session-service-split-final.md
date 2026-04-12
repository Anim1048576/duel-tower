# SessionService Split Final

Session 관련 책임은 아래 서비스들로 분리한다.

- `SessionLifecycleService`
  - `createSession`
  - `get`
  - `withSessionLock`
  - `deleteSession`
  - `cleanupExpiredSessions`
  - runtime / registry 접근 진입점

- `SessionLobbyService`
  - `join`
  - `issuePlayerToken`
  - `resolvePlayerIdByToken`
  - `leaveSession`
  - `setPlayerReady`
  - `kickPlayer`
  - `resetSession`

- `SessionLoadoutService`
  - `updateDeck`
  - `updateLoadout`
  - `applyPresetToLoadout`
  - `forgetOwnedCard`

- `SessionQueryService`
  - `getPublicState`
  - `getRun`
  - `getInventory`
  - `getResults`
  - `getRecentResults`
  - `getChoices`
  - read 권한 확인 + 조회 DTO 조립

- `SessionCommandService`
  - `handleCommand`
  - request 기본 검증
  - `expectedVersion` 검사 진입
  - command 권한 위임
  - 엔진 호출
  - command 결과 DTO 조립

## Controller rule

`SessionController`는 권한 확인과 HTTP 입출력 연결에 집중한다.

- lifecycle API는 `SessionLifecycleService`
- lobby API는 `SessionLobbyService`
- self loadout API는 `SessionLoadoutService`
- query API는 `SessionQueryService`
- command API는 `SessionCommandService`

## SessionService final state

`SessionService`는 더 이상 세션 API의 단일 진입점이 아니다.
현재는 실제 세션 runtime/registry와 기존 도메인 조작 구현을 담는 내부 backing service 역할을 한다.
후속 리팩터링에서는 각 역할 서비스가 내부 helper까지 점진적으로 흡수할 수 있다.
