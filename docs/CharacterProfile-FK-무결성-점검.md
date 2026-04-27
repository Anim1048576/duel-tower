# CharacterProfile FK/무결성 점검

## 목적

CharacterProfile 정규화 6회차에서 DB FK, unique, index, cascade, JPA 관계를 바로 추가하기 전에 현재 무결성 상태와 테스트 의존성을 확인한다.

이번 점검은 기능 로직 변경 없이 다음 회차의 도입 순서를 정하기 위한 문서다. 현재 구조에서는 `CharacterProfile` 본체가 기본 프로필 정보만 가지고, 보유 카드/current skill deck/EX loadout은 별도 테이블과 서비스가 source of truth다.

## 현재 DDL/마이그레이션 전략

| 환경 | 파일 | ddl-auto | 비고 |
| --- | --- | --- | --- |
| 기본/로컬 | `src/main/resources/application.properties` | `update` | MariaDB datasource. Hibernate DDL update에 의존한다. |
| 테스트 | `src/test/resources/application-test.properties` | `create-drop` | H2 in-memory, MariaDB mode. 테스트마다 Hibernate가 스키마를 생성/삭제한다. |

`build.gradle`과 `src/main/resources` 검색 기준으로 Flyway/Liquibase 의존성, `schema.sql`, `data.sql`, `db/migration` 디렉터리는 확인되지 않았다. 따라서 현재는 JPA/Hibernate DDL 생성에 의존하는 것으로 보인다.

정규화 테이블에 FK/unique/index를 추가하려면 먼저 마이그레이션 전략을 정해야 한다. 운영 DB에 이미 `ddl-auto=update`를 쓰고 있으므로, 제약 추가를 Hibernate 자동 변경에 맡기기보다 명시 migration을 도입하는 편이 안전하다.

## 현재 테이블/엔티티 관계 지도

| 관계 | 현재 코드상 타입 | JPA 관계 | DB FK 확인 | 서비스 검증 | orphan 가능성 | FK 난이도 | 추천 방식 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `CharacterProfile.id` -> `CharacterOwnedCard.characterId` | `Long` -> `Long` | 없음 | 확인된 FK 없음 | `characterId` 양수 검증만 있음 | 높음. 존재하지 않는 characterId로 owned card 저장 가능 | 낮음 | `character_owned_cards.character_id` FK 우선. 기존 orphan 정리 후 적용 |
| `CharacterOwnedCard.ownedCardId` -> `CharacterOwnedCardModifier.ownedCardId` | `String` -> `String` | 없음 | 확인된 FK 없음 | 저장 시 owned card와 함께 생성하지만 modifier 단독 저장 검증은 없음 | 중간. repository 직접 저장/삭제 순서 오류 시 발생 | 낮음 | `owned_card_id` FK + delete cascade 검토 |
| `CharacterProfile.id` -> `CharacterCurrentSkillDeckEntry.characterId` | `Long` -> `Long` | 없음 | 확인된 FK 없음 | `characterId` 양수 검증만 있음 | 중간. 없는 characterId에 빈 덱은 저장되지 않지만 clear/read 가능 | 낮음 | `character_current_skill_deck_entries.character_id` FK |
| `CharacterOwnedCard.ownedCardId` -> `CharacterCurrentSkillDeckEntry.ownedCardId` | `String` -> `String` | 없음 | 확인된 FK 없음 | `CharacterLoadoutService.validateOwnedCardsAvailable(...)`가 같은 character의 owned card인지 확인 | 낮음~중간. 서비스 우회 시 가능 | 중간 | 단일 FK로 시작 가능. character 소속 보장은 composite FK 후보 |
| `CharacterProfile.id` -> `CharacterExLoadout.characterId` | `Long` -> `Long` | 없음 | 확인된 FK 없음 | `characterId` 양수 검증, `exCardId` 존재/type 검증 | 중간. 없는 characterId에 EX loadout 저장 가능 | 낮음 | `character_ex_loadouts.character_id` FK. 1:1 PK 유지 |

## 현재 서비스 레이어 검증 현황

| 검증 항목 | 현재 위치 | 상태 |
| --- | --- | --- |
| owned card 저장 시 characterId가 실제 CharacterProfile에 존재하는지 | `CharacterCardCollectionService.requireCharacterId(...)` | 없음. null/양수만 검증한다. |
| owned card modifier 저장 시 ownedCardId가 실제 owned card에 존재하는지 | `CharacterCardCollectionService.replaceOwnedCards(...)` | 부분적. 같은 요청에서 owned card와 modifier를 함께 만들지만 DB/서비스 단독 검증은 없다. |
| currentSkillDeck 저장 시 characterId가 실제 CharacterProfile에 존재하는지 | `CharacterLoadoutService.requireCharacterId(...)` | 없음. null/양수만 검증한다. |
| currentSkillDeck 저장 시 ownedCardId가 해당 character의 ownedCard인지 | `CharacterLoadoutService.validateOwnedCardsAvailable(...)` | 있음. `CharacterCardCollectionService.hasOwnedCard(characterId, ownedCardId)`로 확인한다. |
| exCard 저장 시 characterId가 실제 CharacterProfile에 존재하는지 | `CharacterLoadoutService.requireCharacterId(...)` | 없음. null/양수만 검증한다. |
| exCard 저장 시 exCardId가 존재하는 카드이며 EX 타입인지 | `CharacterLoadoutService.validateExCardId(...)` | 있음. `CardService.asMap()`에서 조회하고 `CardType.EX`를 강제한다. |

가장 큰 공백은 `characterId` 존재 검증이다. 현재 정상 API 경로는 `CharacterProfileService.create/update/delete`가 프로필 저장 후 하위 서비스를 호출하므로 대체로 안전하지만, 서비스나 repository를 직접 호출하면 orphan row를 만들 수 있다.

## 삭제 순서 점검

현재 `CharacterProfileService.delete(...)` 순서:

1. `repository.existsById(id)` 확인
2. `CharacterLoadoutService.deleteLoadout(id)`
3. `CharacterCardCollectionService.deleteOwnedCards(id)`
4. `CharacterProfileRepository.deleteById(id)`

`CharacterLoadoutService.deleteLoadout(...)`:

1. `clearCurrentSkillDeck(characterId)` -> `character_current_skill_deck_entries` 삭제
2. `clearExCard(characterId)` -> `character_ex_loadouts` 삭제

`CharacterCardCollectionService.deleteOwnedCards(...)`:

1. characterId의 owned card id 목록 조회
2. `character_owned_card_modifiers` 삭제
3. `character_owned_cards` 삭제

예상 FK 기준 안전한 삭제 순서:

1. `character_current_skill_deck_entries`
2. `character_ex_loadouts`
3. `character_owned_card_modifiers`
4. `character_owned_cards`
5. `character_profiles`

현재 프로덕션 삭제 순서는 위 순서와 맞는다. 단, `CharacterCardCollectionService.deleteOwnedCards(...)` 단독 호출은 current skill deck entry를 삭제하지 않는다. owned cards를 교체하는 update 경로에서는 `CharacterProfileService.update(...)`가 owned card 변경 시 `loadoutService.clearCurrentSkillDeck(id)`를 호출한다. 다음 회차에서 `current_skill_deck_entries.owned_card_id` FK를 추가하면, owned card 교체 전에 current deck을 먼저 clear하는 순서가 모든 저장 경로에서 유지되는지 추가 확인이 필요하다.

## unique/index 현황과 후보

현재 확인된 제약:

- `CharacterOwnedCard.ownedCardId`: `@Id`, `character_owned_cards.owned_card_id` PK
- `CharacterExLoadout.characterId`: `@Id`, `character_ex_loadouts.character_id` PK
- `CharacterCurrentSkillDeckEntry(character_id, position)`: unique 있음
- `CharacterCurrentSkillDeckEntry(character_id, owned_card_id)`: unique 있음

추가 후보:

| 후보 | 필요성 | 비고 |
| --- | --- | --- |
| `character_owned_cards(character_id)` index | 높음 | 보유 카드 조회/삭제가 characterId 기반이다. FK 추가 시 DB가 보조 index를 자동 생성하는지 DB별 확인 필요 |
| `character_owned_cards(character_id, owned_card_id)` unique | 중간 | `owned_card_id`가 전역 PK라 중복 방지 자체는 이미 된다. composite FK로 같은 캐릭터 소속을 DB 레벨 보장하려면 필요 |
| `character_owned_card_modifiers(owned_card_id)` index | 높음 | modifier 조회/삭제가 ownedCardId 기반이다 |
| `character_owned_card_modifiers(owned_card_id, modifier_id)` unique | 중간 | 같은 owned card에 같은 modifier가 중복 저장되는 것을 막고 싶다면 필요. 현재 중복 modifier 의미가 명확하지 않으므로 정책 결정 필요 |
| `character_current_skill_deck_entries(character_id)` index | 높음 | current deck 조회/삭제가 characterId 기반이다 |
| `character_current_skill_deck_entries(owned_card_id)` index | 중간 | owned card 삭제/교체 시 참조 확인 또는 삭제에 필요 |
| `character_ex_loadouts(character_id)` index | 낮음 | PK가 characterId라 별도 index 불필요 |

이번 회차에서는 unique/index를 추가하지 않는다.

## FK 후보별 평가

### A. `character_owned_cards.character_id` -> `character_profiles.id`

- 우선순위: 높음
- `on delete cascade`: 가능하지만 현재 서비스가 child-first 삭제를 명시하므로 처음에는 cascade 없이도 적용 가능하다.
- 서비스 삭제 순서: 현재 안전하다.
- JPA `@ManyToOne`: 이번 회차에는 미루는 편이 낫다. 서비스가 id 기반으로 단순하게 동작한다.
- 도입 전 필요 작업: 기존 orphan owned card 확인/정리 SQL 준비.

### B. `character_owned_card_modifiers.owned_card_id` -> `character_owned_cards.owned_card_id`

- 우선순위: 높음
- `on delete cascade`: 실수 방지 측면에서 유용하다. 현재 서비스도 modifiers를 먼저 삭제한다.
- ownedCardId string PK 참조: 현재 domain identity가 `ownedCardId`이므로 적절하다.
- 도입 전 필요 작업: orphan modifier 확인/정리. modifier 중복 허용 정책도 별도 검토.

### C. `character_current_skill_deck_entries.character_id` -> `character_profiles.id`

- 우선순위: 높음
- `on delete cascade`: 가능하지만 현재 서비스 삭제 순서가 먼저 적용된다.
- 도입 전 필요 작업: orphan deck entry 확인/정리.

### D. `character_current_skill_deck_entries.owned_card_id` -> `character_owned_cards.owned_card_id`

- 우선순위: 중간~높음
- 단일 FK만으로 owned card 존재는 보장된다.
- 단일 FK만으로는 `character_current_skill_deck_entries.character_id`와 `owned_card_id`가 같은 캐릭터 소속인지 DB가 보장하지 못한다.
- 같은 캐릭터 소속까지 DB 레벨에서 보장하려면 `character_owned_cards(character_id, owned_card_id)` unique와 `character_current_skill_deck_entries(character_id, owned_card_id)` composite FK가 필요하다.
- 현재 서비스는 `hasOwnedCard(characterId, ownedCardId)`로 같은 캐릭터 소속을 검증한다.
- 추천: 1단계에서는 단일 FK로 orphan 방지, 2단계에서 composite FK 여부를 결정한다.

### E. `character_ex_loadouts.character_id` -> `character_profiles.id`

- 우선순위: 높음
- 현재 `character_id` PK인 1:1 구조를 유지하는 것이 단순하다.
- `on delete cascade`: 가능하지만 현재 서비스가 explicit delete를 수행한다.
- `ex_card_id`는 card definition이 코드/콘텐츠 데이터에서 오는 값이라 일반 DB FK 대상이 아니다. `CardService` 검증을 유지한다.

## JPA 관계 도입 여부 판단

추천은 우선 DB FK만 도입하고 JPA `@ManyToOne`, `@OneToMany`, cascade는 뒤로 미루는 것이다.

현재 서비스는 `Long characterId`, `String ownedCardId` 기반으로 명확하게 동작한다. JPA 관계를 즉시 도입하면 다음 위험이 생긴다.

- lazy loading과 트랜잭션 경계 변화
- cascade 설정 실수로 의도하지 않은 하위 row 삭제/보존
- 테스트 fixture 복잡도 증가
- JSON 직렬화 순환 참조 가능성
- 서비스 레이어의 id 기반 단순성 훼손

DB FK는 orphan 방지 효과를 바로 주면서 애플리케이션 객체 그래프를 바꾸지 않는다. 따라서 다음 회차에서는 migration 기반 FK/필요 index부터 도입하고, JPA 관계는 별도 회차에서 검토하는 것이 안전하다.

## 테스트 영향

현재 주요 통합 테스트 cleanup은 대체로 child-first 순서로 정리되어 있다.

확인된 안전한 cleanup 패턴:

- `PresetControllerIntegrationTest`
- `ScreenControllerIntegrationTest`
- `CharacterDeckApplyIntegrationTest`
- `SessionAuthIntegrationTest`
- `SessionCommandAuthIntegrationTest`
- `SessionLogControllerIntegrationTest`
- `SessionManagementIntegrationTest`
- `SessionPresetApplyIntegrationTest`

위 테스트들은 `character_current_skill_deck_entries`, `character_ex_loadouts`, `character_owned_card_modifiers`, `character_owned_cards`를 먼저 지운 뒤 `character_profiles` 등을 지운다.

주의할 테스트/fixture:

- `CharacterCardCollectionServiceTest`: mock repository 단위 테스트라 DB FK 영향은 직접 받지 않는다. 다만 서비스가 profile 존재를 검증하지 않는 현재 정책을 보여준다.
- `CharacterLoadoutServiceTest`: mock repository 단위 테스트라 DB FK 영향은 직접 받지 않는다. 마찬가지로 profile 존재 검증이 없다.
- `CharacterDeckApplyIntegrationTest`: 일부 helper가 `CharacterCurrentSkillDeckEntry`를 repository로 직접 저장한다. 현재는 먼저 `CharacterCardCollectionService.replaceOwnedCardsFromJson(...)`로 owned card를 만든 뒤 저장하므로 FK 적용 후에도 유지 가능해 보인다.
- session/screen/preset integration helper들은 서비스 경로로 owned cards/loadout을 만든다. FK 추가 전후로 cleanup 순서 유지가 중요하다.

FK 도입 전에 보강할 테스트 후보:

- 캐릭터 삭제 시 ownedCards/modifiers/currentSkillDeck/exLoadout이 모두 삭제되는 통합 테스트
- ownedCards 변경 시 currentSkillDeck이 clear되는 통합 테스트
- 없는 ownedCardId로 currentSkillDeck 저장 실패
- 없는 exCardId 또는 non-EX exCard 저장 실패
- CharacterProfile 삭제 후 orphan row 없음 확인
- repository 직접 저장이 필요한 테스트가 FK를 위반하지 않는지 fixture 점검

## 권장 6회차 작업 순서

1. 운영/개발 DB에서 현재 orphan row 점검 SQL을 준비한다.
2. migration 도입 방식을 결정한다. Hibernate `ddl-auto=update`에 제약 추가를 맡기지 않는다.
3. `character_owned_cards.character_id` FK와 characterId 조회 index를 먼저 도입한다.
4. `character_owned_card_modifiers.owned_card_id` FK와 ownedCardId 조회 index를 도입한다.
5. `character_ex_loadouts.character_id` FK를 도입한다.
6. `character_current_skill_deck_entries.character_id` FK를 도입한다.
7. `character_current_skill_deck_entries.owned_card_id` 단일 FK를 도입한다.
8. composite FK가 필요한지 별도 판단한다. 필요하면 `character_owned_cards(character_id, owned_card_id)` unique를 먼저 추가한 뒤 `(character_id, owned_card_id)` composite FK를 검토한다.
9. FK 적용 후 주요 통합 테스트와 전체 `./gradlew test`를 실행한다.

## 보류할 항목

- JPA `@ManyToOne`, `@OneToMany`, `@JoinColumn` 도입
- JPA cascade/orphanRemoval 도입
- `CharacterOwnedCardModifier(ownedCardId, modifierId)` unique 적용
- current skill deck의 composite FK 적용
- `ex_card_id`를 DB FK로 연결하는 작업
- `hiddenTraitIds` 정규화
- API 응답 `ownedCards`/`exCard` 문자열 JSON 제거

## 6회차 1차 적용 범위

이번 회차에서는 Flyway를 도입하고, CharacterProfile 정규화 테이블 중 보유 카드 계열의 가장 안전한 FK/index만 migration으로 관리하기 시작한다.

추가한 migration:

- `src/main/resources/db/migration/V1__character_profile_owned_card_fk_indexes.sql`

추가한 FK/index:

- `ix_character_owned_cards_character_id`
  - `character_owned_cards(character_id)`
- `ix_character_owned_card_modifiers_owned_card_id`
  - `character_owned_card_modifiers(owned_card_id)`
- `fk_character_owned_cards_character`
  - `character_owned_cards.character_id -> character_profiles.id`
- `fk_character_owned_card_modifiers_owned_card`
  - `character_owned_card_modifiers.owned_card_id -> character_owned_cards.owned_card_id`

이번 회차에서 보류한 FK:

- `character_ex_loadouts.character_id -> character_profiles.id`
- `character_current_skill_deck_entries.character_id -> character_profiles.id`
- `character_current_skill_deck_entries.owned_card_id -> character_owned_cards.owned_card_id`
- current skill deck의 composite FK

### Flyway 설정 메모

기본/로컬 설정은 Flyway를 활성화하고 `ddl-auto=update`는 유지한다. 기존 Hibernate 생성 스키마에 migration을 적용하는 이행기이므로 `spring.flyway.baseline-on-migrate=true`, `spring.flyway.baseline-version=0`을 둔다.

테스트 프로필은 `ddl-auto=create-drop`를 유지하고 Flyway를 비활성화한다. 이번 V1 migration은 Hibernate가 이미 만든 테이블에 `ALTER TABLE`로 FK/index를 추가하는 형태라, JPA보다 먼저 실행되는 Flyway와 테스트 create-drop 순서가 충돌할 수 있기 때문이다.

새 DB를 처음 구성하는 경우에는 이번 migration만으로 전체 스키마를 만들 수 없다. 완전한 schema migration을 도입하기 전까지는 기존 Hibernate schema 생성 이력 위에 제약을 추가하는 전환 단계로 취급한다.

### 적용 전 orphan 점검 SQL

운영 DB에 migration을 적용하기 전에 아래 조회 결과가 비어 있는지 확인한다.

없는 character profile을 참조하는 owned card:

```sql
SELECT oc.*
FROM character_owned_cards oc
LEFT JOIN character_profiles cp ON cp.id = oc.character_id
WHERE cp.id IS NULL;
```

없는 owned card를 참조하는 modifier:

```sql
SELECT m.*
FROM character_owned_card_modifiers m
LEFT JOIN character_owned_cards oc ON oc.owned_card_id = m.owned_card_id
WHERE oc.owned_card_id IS NULL;
```

orphan row가 발견되면 삭제 전에 해당 row가 실제로 복구 가능한 데이터인지 먼저 검토한다. 삭제 SQL은 운영 데이터 검토 후 별도 작업으로 작성한다.

### 삭제 순서 호환성

이번에 추가한 FK 기준으로 현재 삭제 순서는 호환된다.

- `CharacterCardCollectionService.deleteOwnedCards(...)`
  - `character_owned_card_modifiers`를 먼저 삭제한다.
  - 이후 `character_owned_cards`를 삭제한다.
- `CharacterProfileService.delete(...)`
  - loadout을 먼저 삭제한다.
  - owned cards/modifiers를 삭제한다.
  - 마지막에 `character_profiles`를 삭제한다.

### 다음 회차 후보

- `character_ex_loadouts.character_id` FK
- `character_current_skill_deck_entries.character_id` FK
- `character_current_skill_deck_entries.owned_card_id` FK
- `character_owned_cards(character_id, owned_card_id)` unique와 current skill deck composite FK 검토

## 7회차 1차 loadout character FK 적용 범위

이번 회차에서는 loadout 계열 테이블 중 `character_id`가 없는 `CharacterProfile`을 참조하지 못하도록 FK를 추가한다. JPA 관계, cascade, current skill deck의 `owned_card_id` FK, composite FK는 계속 보류한다.

추가한 migration:

- `src/main/resources/db/migration/V2__character_profile_loadout_character_fks.sql`

추가한 FK/index:

- `ix_character_current_skill_deck_entries_character_id`
  - `character_current_skill_deck_entries(character_id)`
- `fk_character_ex_loadouts_character`
  - `character_ex_loadouts.character_id -> character_profiles.id`
- `fk_character_current_skill_deck_entries_character`
  - `character_current_skill_deck_entries.character_id -> character_profiles.id`

`character_ex_loadouts.character_id`는 PK이므로 별도 조회 index를 추가하지 않는다. `character_current_skill_deck_entries`에는 `(character_id, position)`, `(character_id, owned_card_id)` unique가 있지만, characterId 기반 조회/삭제 의도를 명시하기 위해 단일 index를 추가한다.

### 적용 전 orphan 점검 SQL

운영 DB에 V2 migration을 적용하기 전에 아래 조회 결과가 비어 있는지 확인한다.

없는 character profile을 참조하는 EX loadout:

```sql
SELECT ex.*
FROM character_ex_loadouts ex
LEFT JOIN character_profiles cp ON cp.id = ex.character_id
WHERE cp.id IS NULL;
```

없는 character profile을 참조하는 current skill deck entry:

```sql
SELECT e.*
FROM character_current_skill_deck_entries e
LEFT JOIN character_profiles cp ON cp.id = e.character_id
WHERE cp.id IS NULL;
```

orphan row가 발견되면 migration 적용 전에 복구 또는 삭제 여부를 검토한다. 삭제 SQL은 운영 데이터 확인 후 별도 작업으로 작성한다.

### 삭제 순서 호환성

이번 V2 FK 기준으로 현재 삭제 순서는 호환된다.

- `CharacterProfileService.delete(...)`
  - `CharacterLoadoutService.deleteLoadout(id)`를 먼저 호출한다.
  - 이후 owned cards/modifiers를 삭제한다.
  - 마지막에 `character_profiles`를 삭제한다.
- `CharacterLoadoutService.deleteLoadout(...)`
  - `clearCurrentSkillDeck(characterId)`로 `character_current_skill_deck_entries`를 character_id 기준 삭제한다.
  - `clearExCard(characterId)`로 `character_ex_loadouts`를 character_id 기준 삭제한다.

### 7회차 보류 항목

- `character_current_skill_deck_entries.owned_card_id -> character_owned_cards.owned_card_id`
- `character_owned_cards(character_id, owned_card_id)` unique
- current skill deck composite FK
- JPA `@ManyToOne`, `@OneToMany`, `@JoinColumn`
- JPA cascade/orphanRemoval

### 다음 회차 후보

- current skill deck `owned_card_id` 단일 FK
- current skill deck composite FK 설계 전 orphan/소속 불일치 점검
- `CharacterOwnedCardModifier(ownedCardId, modifierId)` unique 정책 판단

## 8회차 사전 보정: current skill deck child-first 순서

이번 회차에서는 아직 `character_current_skill_deck_entries.owned_card_id -> character_owned_cards.owned_card_id` FK를 추가하지 않는다.
V3 migration, `character_owned_cards(character_id, owned_card_id)` unique, composite FK, JPA 연관관계, cascade/orphanRemoval도 도입하지 않는다.

대신 FK 도입 전에 `CharacterProfileService.update(...)`의 owned cards 교체 순서를 보정했다.

변경 후 update 순서:

1. `ownedCardsChanged`를 기존 owned cards 기준으로 먼저 계산한다.
2. owned cards가 변경된 경우 `loadoutService.clearCurrentSkillDeck(id)`를 먼저 호출한다.
3. `CharacterCardCollectionService.replaceOwnedCards(...)` 또는 `replaceOwnedCardsFromJson(...)`로 owned cards를 교체한다.
4. EX card를 기존 정책대로 교체한다.

이 보정이 필요한 이유는 나중에 `character_current_skill_deck_entries.owned_card_id` FK를 추가하면, 기존 current deck entry가 아직 삭제되지 않은 상태에서 참조 중인 owned card를 먼저 삭제할 수 없기 때문이다. 따라서 owned cards 변경 경로에서는 current skill deck entry를 먼저 삭제하고, 그 다음 owned card modifiers와 owned cards를 삭제/재저장하는 child-first 순서를 유지해야 한다.

`CharacterCardCollectionService.deleteOwnedCards(...)`에는 `CharacterLoadoutService` 의존성을 추가하지 않았다. current skill deck clear 책임은 owned card 저장소 서비스가 아니라 `CharacterProfileService`의 update orchestration에 둔다.

### 8회차 다음 후보

- current skill deck `owned_card_id` 단일 FK 도입
- composite FK 도입 여부 판단
- `character_owned_cards(character_id, owned_card_id)` unique 필요성 검토

## 9회차 적용 범위: current skill deck owned_card_id 단일 FK

이번 회차에서는 `character_current_skill_deck_entries.owned_card_id -> character_owned_cards.owned_card_id` 단일 FK를 도입한다. current skill deck entry가 존재하지 않는 owned card를 참조하지 못하게 하는 것이 목적이다.

추가한 migration:

- `src/main/resources/db/migration/V3__current_skill_deck_owned_card_fk.sql`

추가한 index/FK:

- `ix_character_current_skill_deck_entries_owned_card_id`
  - `character_current_skill_deck_entries(owned_card_id)`
- `fk_character_current_skill_deck_entries_owned_card`
  - `character_current_skill_deck_entries.owned_card_id -> character_owned_cards.owned_card_id`

이번 회차에서는 `ON DELETE CASCADE`, composite FK, `character_owned_cards(character_id, owned_card_id)` unique, JPA 관계, cascade/orphanRemoval을 추가하지 않는다.

### 9회차 적용 전 orphan 점검 SQL

아래 조회 결과는 V3 단일 FK 적용 전에 반드시 비어 있어야 한다. 결과가 있으면 current skill deck entry가 존재하지 않는 owned card를 참조하므로 FK 생성이 실패한다.

```sql
SELECT e.*
FROM character_current_skill_deck_entries e
LEFT JOIN character_owned_cards oc ON oc.owned_card_id = e.owned_card_id
WHERE oc.owned_card_id IS NULL;
```

### 9회차 소속 불일치 점검 SQL

아래 조회는 `owned_card_id` 자체는 존재하지만 current skill deck entry의 character와 owned card의 character가 다른 경우를 찾는다.

```sql
SELECT e.*, oc.character_id AS owned_card_character_id
FROM character_current_skill_deck_entries e
JOIN character_owned_cards oc ON oc.owned_card_id = e.owned_card_id
WHERE e.character_id <> oc.character_id;
```

이 소속 불일치는 이번 단일 FK로는 막지 못한다. 현재 애플리케이션 저장 경로는 `CharacterLoadoutService.validateOwnedCardsAvailable(...)`에서 `CharacterCardCollectionService.hasOwnedCard(characterId, ownedCardId)`를 호출해 같은 character의 owned card인지 검증한다. B 유형까지 DB 레벨에서 막으려면 별도 회차에서 `character_owned_cards(character_id, owned_card_id)` unique와 `(character_id, owned_card_id)` composite FK를 검토해야 한다.

### 9회차 삭제/교체 순서 호환성

8회차에서 `CharacterProfileService.update(...)` 순서를 보정했기 때문에 owned cards 변경 시 `currentSkillDeck`이 owned cards 삭제보다 먼저 clear된다. 따라서 정상 update 경로는 이번 `owned_card_id` FK와 호환된다.

`CharacterProfileService.delete(...)`도 `loadoutService.deleteLoadout(id)`를 먼저 호출한 뒤 `cardCollectionService.deleteOwnedCards(id)`를 호출하므로, current skill deck entries 삭제 후 owned cards를 삭제하는 순서를 유지한다.

주의할 점은 `CharacterCardCollectionService.deleteOwnedCards(...)` 단독 호출이다. 이 메서드는 owned card 저장소 책임만 가지며 current skill deck을 clear하지 않는다. FK 도입 후 current skill deck entry가 남아 있는 character에 대해 이 메서드를 직접 호출하면 DB FK 위반이 발생할 수 있다. 정상 경로에서는 `CharacterProfileService.update/delete` orchestration을 사용해야 한다.

### 9회차 보장 범위

이번 단일 FK가 보장하는 것:

- current skill deck entry가 존재하지 않는 `owned_card_id`를 참조하지 못한다.
- owned card 삭제 전에 current skill deck entry를 먼저 삭제해야 하는 DB 제약이 생긴다.

이번 단일 FK가 보장하지 않는 것:

- current skill deck entry의 `character_id`와 referenced owned card의 `character_id`가 같은지 여부
- owned card의 character 소속까지 포함한 DB 레벨 무결성
- modifier 중복 정책

### 9회차 보류 항목

- current skill deck composite FK
- `character_owned_cards(character_id, owned_card_id)` unique
- JPA `@ManyToOne`, `@OneToMany`, `@JoinColumn`
- JPA cascade/orphanRemoval
- `ON DELETE CASCADE`

### 9회차 다음 후보

- composite FK 필요성 판단
- `character_owned_cards(character_id, owned_card_id)` unique 검토
- `CharacterOwnedCardModifier(ownedCardId, modifierId)` unique 정책 판단
