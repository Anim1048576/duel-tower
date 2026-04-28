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

`CharacterProfileService`는 응답을 만들 때 `CharacterProfile`에서 loadout 값을 읽지 않는다. 보유 카드 응답은 `CharacterCardCollectionService`에서, 현재 스킬 덱과 EX 응답은 `CharacterLoadoutService`에서 읽어 조립한다.

## API 호환 필드

`CharacterProfileRequest`와 `CharacterProfileResponse`는 public character API에서 structured loadout 필드만 사용한다.

### Request

Request fields:

- `ownedCardList`: 필수 구조화 보유 카드 입력. 빈 배열은 허용한다. 저장 원본은 `CharacterOwnedCard` / `CharacterOwnedCardModifier`다.
- `exCardId`: 필수 EX 카드 id 입력. 빈 문자열은 EX 장착 해제를 의미한다.

Removed legacy request fields:

- `ownedCards`: 더 이상 `CharacterProfile` create/update 계약이 아니다.
- `exCard`: 더 이상 `CharacterProfile` create/update 계약이 아니다.

### Response

Preferred response fields:

- `ownedCardList`: 구조화된 보유 카드 응답. public character API 응답 전용 DTO인 `CharacterOwnedCardResponse` / `CharacterOwnedCardModifierResponse`를 사용한다. 저장 원본은 `CharacterOwnedCard` / `CharacterOwnedCardModifier`다.
- `exCardId`: preferred structured EX response field다. 현재 장착된 EX 카드 id이며, 장착된 EX가 없으면 `null`이다.
- `currentSkillDeckPreviewCardIds`: UI 표시용 cardId preview. 저장 원본은 `CharacterCurrentSkillDeckEntry`의 ownedCardId 행이다.

Removed legacy response fields:

- `ownedCards`: `CharacterProfileResponse`에서 제거되었다.
- `exCard`: `CharacterProfileResponse`에서 제거되었다.

Response rule:

- 클라이언트는 `ownedCardList`와 `exCardId`를 사용해야 한다.
- legacy response fallback 파싱은 더 이상 public character API 계약이 아니다.

raw `currentSkillDeck`은 `CharacterProfile` create/update 요청으로 직접 쓸 수 없다. 현재 스킬 덱 변경은 `CharacterLoadoutService`를 사용하는 전용 경로로 처리해야 한다. API 응답도 raw `currentSkillDeck`을 노출하지 않고, 표시용 `currentSkillDeckPreviewCardIds`만 제공한다.

## 현재 DB 무결성 상태

CharacterProfile 정규화 테이블의 FK는 Flyway migration으로 단계적으로 도입했다.

- V1: `character_owned_cards.character_id -> character_profiles.id`, `character_owned_card_modifiers.owned_card_id -> character_owned_cards.owned_card_id`
- V2: `character_ex_loadouts.character_id -> character_profiles.id`, `character_current_skill_deck_entries.character_id -> character_profiles.id`
- V3: `character_current_skill_deck_entries.owned_card_id -> character_owned_cards.owned_card_id`
- V4: `character_current_skill_deck_entries(character_id, owned_card_id) -> character_owned_cards(character_id, owned_card_id)` composite FK

V4 이후에는 current skill deck entry가 존재하지 않는 owned card를 참조할 수 없고, 다른 캐릭터의 owned card를 참조하는 소속 불일치도 DB 레벨에서 차단된다.

## 현재 frontend transition 상태

현재 character detail UI는 응답을 폼 상태로 옮길 때 구조화 응답만 사용한다.

- `ownedCardList`가 있으면 이를 JSON 문자열로 표시한다.
- `ownedCardList`가 없으면 빈 배열 문자열 `[]`을 표시한다.
- `exCardId`가 있으면 `{ "id": exCardId }` 형태로 표시한다.
- `exCardId`가 없으면 빈 EX 객체 문자열 `{}`을 표시한다.

저장 payload는 `ownedCardList`와 `exCardId`를 사용한다. 따라서 character detail UI의 정상 저장 경로는 legacy request field에 의존하지 않는다.

## 아직 남은 비정규화 항목

`hiddenTraitIds`는 아직 `CharacterProfile`의 JSON TEXT 컬럼으로 남아 있다. 현재 정규화 범위의 저장 원본 분리 대상은 보유 카드, 현재 스킬 덱, EX loadout이며, `hiddenTraitIds`는 완전 정규화 대상이 아니다.

## 리스크와 후속 과제

### 1. Legacy response/request 제거 완료

`ownedCards`와 `exCard`는 `CharacterProfileResponse`에서 제거되었다.

- frontend read path는 `ownedCardList`와 `exCardId`를 사용한다.
- backend service/controller tests는 structured response field 중심으로 검증한다.
- `CharacterProfileRequest`에서도 legacy `ownedCards` / `exCard` 입력을 제거했다.

### 2. DTO 경계 정리

완료됨: `CharacterProfileResponse.ownedCardList`는 더 이상 `session.dto.OwnedCardDto`를 직접 노출하지 않는다. public character API 응답에는 `CharacterOwnedCardResponse` / `CharacterOwnedCardModifierResponse`를 사용한다.

현재 상태:

- `CharacterProfileRequest.ownedCardList`는 request 입력용으로 `OwnedCardDto` 계열을 계속 사용할 수 있다.
- `CharacterProfileResponse.ownedCardList`는 response 조회용으로 boolean 필드를 non-null로 제공한다.
- frontend `OwnedCardRequest` / `OwnedCardResponse`도 request와 response nullable 정책을 분리한다.

public API DTO 경계와 legacy JSON compatibility helper 정리는 완료되었다.

### 3. Legacy owned cards JSON helper 제거 완료

`CharacterProfileRequest`에서는 `ownedCards`와 `exCard`를 제거했고, `CharacterCardCollectionService`의 ownedCards JSON compatibility helper도 제거했다.

- `CharacterCardCollectionService.toOwnedCardsJson(...)`는 제거되었다.
- `CharacterCardCollectionService.replaceOwnedCardsFromJson(...)`는 제거되었다.
- 남은 `ownedCards` 표현은 session/runtime/card collection domain 용어이며, public CharacterProfile API JSON compatibility layer가 아니다.

### 4. 테스트 프로필의 FK 검증 전략

테스트 프로필은 H2 `create-drop`와 Flyway disabled 조합을 사용한다. Flyway migration으로 추가한 FK가 테스트 DB에 그대로 적용되지 않을 수 있다.

후속 후보:

- FK/migration 전용 통합 테스트 프로필을 추가할지 판단한다.
- 또는 H2 테스트 스키마에도 핵심 FK가 반영되도록 테스트 설정을 분리할지 판단한다.

### 5. `hiddenTraitIds` 정규화

`hiddenTraitIds`를 검색, 통계, 조건식, 권한 판정에서 자주 쓰게 되면 별도 테이블로 분리할지 판단한다.

## Legacy request 제거 전 체크리스트

- [x] `CharacterProfileResponse`에서 legacy `ownedCards`/`exCard` response field를 제거했다.
- [x] character detail UI가 `ownedCardList`/`exCardId` read를 사용한다.
- [x] character detail UI가 저장 시 `ownedCardList`/`exCardId`를 사용한다.
- [x] backend service/controller tests가 신규 필드 존재를 검증한다.
- [x] frontend type check가 통과한다.
- [x] 테스트 fixture의 기본 request가 structured-only를 사용한다.
- [x] `CharacterProfileRequest`에서 legacy `ownedCards`/`exCard` request field를 제거했다.
- [x] `CharacterCardCollectionService`의 ownedCards JSON compatibility helper를 제거했다.

## Related checks

- [CharacterProfile FK/Integrity Check](./CharacterProfile-FK-무결성-점검.md)
- [CharacterProfile 정규화 10회차: current skill deck composite FK](./CharacterProfile-정규화-10회차-composite-fk.md)
