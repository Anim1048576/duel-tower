# CharacterProfile 정규화 리팩토링

## 현재 책임

`CharacterProfile`은 캐릭터의 기본 프로필 루트다. 이름, 성별, 나이, 소원, 성향, 소개 문구, 이야기, 생활 능력치, 특성, `hiddenTraitIds`만 본체에 둔다.

보유 카드, 현재 장착 스킬 덱, EX 장착 상태는 `CharacterProfile`의 컬럼으로 저장하지 않는다. 이 값들은 별도 테이블과 서비스가 저장 원본이다.

## 저장 원본

- `CharacterOwnedCard`: 캐릭터가 실제 보유한 카드 사본. 같은 `cardId`를 여러 장 가질 수 있으므로 `ownedCardId`가 사본 식별자다.
- `CharacterOwnedCardModifier`: 보유 카드 사본에 붙은 modifier 행.
- `CharacterCurrentSkillDeckEntry`: 현재 장착한 스킬 덱. `ownedCardId` 기반이며 `position`으로 순서를 가진다.
- `CharacterExLoadout`: 현재 장착한 EX 카드.
- `Deck` / `DeckCard`: 저장 가능한 덱 템플릿. `cardId`와 count 기반이며, 캐릭터의 현재 장착 덱과는 별도 개념이다.

## 서비스 경계

- `CharacterCardCollectionService`: 캐릭터 보유 카드 사본 컬렉션의 저장 원본을 관리한다.
- `CharacterLoadoutService`: 현재 스킬 덱과 EX 장착 상태의 저장 원본을 관리한다.
- `CharacterProfileService`: 프로필 기본 정보 저장과 API 호환 응답 조립을 담당한다.

`CharacterProfileService`는 응답을 만들 때 `CharacterProfile`에서 loadout 값을 읽지 않는다. `ownedCards` 응답은 `CharacterCardCollectionService`에서 JSON 문자열로 조립하고, `currentSkillDeckPreviewCardIds`와 `exCard` 응답은 `CharacterLoadoutService`에서 읽어 조립한다.

## API 호환 필드

`CharacterProfileRequest`와 `CharacterProfileResponse`에는 기존 클라이언트 호환을 위해 다음 필드가 남아 있다.

- `ownedCards`: 문자열 JSON. 저장 원본은 `CharacterOwnedCard` / `CharacterOwnedCardModifier`다.
- `exCard`: 문자열 JSON. 저장 원본은 `CharacterExLoadout`이다.
- `currentSkillDeckPreviewCardIds`: UI 표시용 cardId preview. 저장 원본은 `CharacterCurrentSkillDeckEntry`의 ownedCardId 행이다.

raw `currentSkillDeck`은 `CharacterProfile` create/update 요청으로 직접 쓸 수 없다. 현재 스킬 덱 변경은 `CharacterLoadoutService`를 사용하는 전용 경로로 처리해야 한다. API 응답도 raw `currentSkillDeck`을 노출하지 않고, 표시용 `currentSkillDeckPreviewCardIds`만 제공한다.

## 아직 남은 비정규화 항목

`hiddenTraitIds`는 아직 `CharacterProfile`의 JSON TEXT 컬럼으로 남아 있다. 이번 정규화 범위의 저장 원본 분리 대상은 보유 카드, 현재 스킬 덱, EX loadout이며, `hiddenTraitIds`는 완전 정규화 대상이 아니다.

## 리스크와 후속 과제

현재 일부 정규화 테이블은 JPA 관계, FK, cascade가 강하게 모델링되어 있지 않고 서비스 레이어 검증과 테스트 cleanup에 의존한다. 다음 회차에서 DB migration, FK 정책, 삭제 순서, cascade 적용 여부를 별도로 검토해야 한다.

## Create/Update request transition

CharacterProfile create/update request is in a transition period.

- Preferred request fields: `ownedCardList`, `exCardId`
- Deprecated legacy request fields: `ownedCards`, `exCard`
- Priority rule: `ownedCardList` wins over `ownedCards`, and `exCardId` wins over `exCard`.
- Legacy request fields are still accepted for compatibility, but server logs a deprecation warning when those legacy paths are actually used.
- If both new and legacy fields are sent, the new field is used and the legacy field is ignored.

The response shape is not migrated yet. `CharacterProfileResponse.ownedCards` and `CharacterProfileResponse.exCard` remain string JSON compatibility fields.

Before removing the legacy request fields, verify that frontend save paths use only `ownedCardList` and `exCardId`, and that external clients/tests no longer depend on `ownedCards` or `exCard` request input.
