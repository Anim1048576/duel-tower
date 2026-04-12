# SessionService Split Final

Session 관련 책임은 역할별 서비스로 분리됐다.

- `SessionLifecycleService`
  - `createSession`
  - `get`
  - `withSessionLock`
  - `deleteSession`
  - `cleanupExpiredSessions`
  - runtime / in-memory registry 관리
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
- `SessionLoadoutSupport`
  - lobby / loadout이 함께 쓰는 덱, owned card, preset, character loadout 보조 로직
  - parsing / validation / deck runtime 반영 / character deck persistence
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
  - `expectedVersion` 검사
  - command 권한 위임
  - 엔진 호출
  - command 결과 DTO 조립

## Controller rule

`SessionController`는 권한 확인과 HTTP 입출력 연결만 직접 가진다.

- lifecycle API는 `SessionLifecycleService`
- lobby API는 `SessionLobbyService`
- self loadout API는 `SessionLoadoutService`
- query API는 `SessionQueryService`
- command API는 `SessionCommandService`

## SessionService final state

`SessionService`는 더 이상 세션 운영 구현을 담는 서비스가 아니다.

- 현재 남은 public surface는 `withSessionLock` 호환 브리지뿐이다.
- legacy parsing 테스트를 위해 owned card parsing helper만 private compatibility method로 유지한다.
- 실제 session business logic은 lifecycle / lobby / loadout / query / command 서비스에 있다.

즉, `SessionService`는 거대한 잡탕 서비스가 아니라 과거 호출 지점과 테스트를 부드럽게 유지하기 위한 얇은 호환 레이어다.
