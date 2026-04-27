# CharacterProfile 정규화 10회차: current skill deck composite FK

## 적용 목적

9회차 V3 migration은 `character_current_skill_deck_entries.owned_card_id`가 실제 `character_owned_cards.owned_card_id`를 참조하는지만 보장한다.

하지만 단일 FK만으로는 current skill deck entry의 `character_id`와 참조된 owned card의 `character_id`가 같은지 보장할 수 없다. 즉, A 캐릭터의 current skill deck entry가 B 캐릭터의 owned card를 참조하는 소속 불일치 row는 DB 단일 FK만으로 막히지 않는다.

10회차에서는 이 소속 불일치를 DB 레벨에서 차단하기 위해 composite FK를 추가한다.

## 추가한 migration

- `src/main/resources/db/migration/V4__current_skill_deck_composite_owned_card_fk.sql`

## 추가한 제약

```sql
CREATE UNIQUE INDEX uk_character_owned_cards_character_owned_card
    ON character_owned_cards (character_id, owned_card_id);

ALTER TABLE character_current_skill_deck_entries
    ADD CONSTRAINT fk_character_current_skill_deck_entries_character_owned_card
    FOREIGN KEY (character_id, owned_card_id)
    REFERENCES character_owned_cards (character_id, owned_card_id);
```

## 보장 범위

이번 V4가 보장하는 것:

- current skill deck entry의 `owned_card_id`가 존재하는 owned card여야 한다.
- current skill deck entry의 `character_id`와 referenced owned card의 `character_id`가 같아야 한다.
- 다른 캐릭터의 owned card를 current skill deck에 끼워 넣는 repository 우회 저장을 DB가 거부한다.

이번 V4가 보장하지 않는 것:

- owned card modifier 중복 정책
- JPA 연관관계/cascade/orphanRemoval
- `hiddenTraitIds` 정규화
- `ownedCards`/`exCard` compatibility response 제거

## 적용 전 점검 SQL

V4 적용 전에 아래 조회 결과가 비어 있어야 한다.

### 1. 존재하지 않는 owned card 참조

V3에서 이미 막히는 유형이지만, V4 적용 전에도 다시 확인한다.

```sql
SELECT e.*
FROM character_current_skill_deck_entries e
LEFT JOIN character_owned_cards oc ON oc.owned_card_id = e.owned_card_id
WHERE oc.owned_card_id IS NULL;
```

### 2. owned card 소속 불일치

```sql
SELECT e.*, oc.character_id AS owned_card_character_id
FROM character_current_skill_deck_entries e
JOIN character_owned_cards oc ON oc.owned_card_id = e.owned_card_id
WHERE e.character_id <> oc.character_id;
```

결과가 있으면 current skill deck entry가 다른 캐릭터의 owned card를 참조하고 있다는 뜻이다. migration 적용 전에 해당 entry를 삭제하거나 올바른 owned card로 재지정해야 한다.

## 삭제/교체 순서 영향

현재 정상 경로는 V4와 호환된다.

- `CharacterProfileService.update(...)`
  - owned cards가 바뀌면 먼저 `loadoutService.clearCurrentSkillDeck(id)`를 호출한다.
  - 그 다음 `CharacterCardCollectionService.replaceOwnedCards(...)` 또는 `replaceOwnedCardsFromJson(...)`로 owned cards를 교체한다.
- `CharacterProfileService.delete(...)`
  - `loadoutService.deleteLoadout(id)`를 먼저 호출한다.
  - 그 다음 `cardCollectionService.deleteOwnedCards(id)`를 호출한다.
  - 마지막에 profile을 삭제한다.

주의할 점은 `CharacterCardCollectionService.deleteOwnedCards(...)`를 직접 호출하는 경로다. 이 메서드는 current skill deck clear 책임을 갖지 않으므로, current skill deck entry가 남아 있는 상태에서 owned cards를 삭제하려 하면 FK 위반이 발생한다. owned cards 교체/삭제는 `CharacterProfileService`의 orchestration 경로를 사용해야 한다.

## 다음 후보

1. 테스트 프로필에서도 migration/FK를 검증할 별도 전략 수립
2. `ownedCards` / `exCard` compatibility response를 구조화 응답으로 전환
3. legacy request field 제거 시점 결정
4. `CharacterOwnedCardModifier(ownedCardId, modifierId)` unique 정책 판단
5. `hiddenTraitIds` 정규화 여부 판단
