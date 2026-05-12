# Duel Tower 적 콘텐츠 JSON 도입 PR 단위별 Codex 프롬프트

## 전제

이 문서는 Duel Tower 프로젝트에 “JSON 기반 EnemyDefinition 콘텐츠 레이어”를 단계적으로 도입하기 위한 Codex용 프롬프트 모음이다.

이번 전체 구현 방향은 다음과 같다.

- YAML은 사용하지 않는다.
- 적 정의는 `src/main/resources/balance/enemies.json`에서 관리한다.
- 기존 `src/main/resources/balance/encounters.json`은 적 수치를 직접 들고 있던 구조에서 `enemyDefId` 참조 구조로 바꾼다.
- 실제 전투 런타임은 기존 `EnemyState`를 계속 사용한다.
- `EnemyDefinition`은 정적 콘텐츠 데이터이고, `EnemyState`는 전투 중 런타임 상태다.
- `deck`, `startStatuses`, `passives`는 JSON에서 참조 ID로 들고 있어도 된다.
- 다만 이번 범위에서는 적 AI, 적 자동 행동, 적 카드 사용 로직, 인텐트 UI는 구현하지 않는다.
- 적이 “움직이는 것”이 아니라, 적이 “콘텐츠 정의를 통해 생성되는 것”까지가 목표다.

## 공통 금지 사항

모든 PR에서 다음은 구현하지 않는다.

- `EnemyAiPolicy`
- `EnemyTurnCommand`
- 적 자동 행동 실행
- 적 카드 드로우/셔플/사용
- 적 인텐트 DTO/UI
- 보스 페이즈 처리
- 타겟 우선순위 로직
- 체력 50% 이하 조건부 행동
- 복잡한 조건식 파서
- YAML 로더
- DB 저장 방식의 EnemyDefinition

## 공통 구현 원칙

- 기존 `EnemyState`를 가능한 유지하고, 필요한 필드만 최소 추가한다.
- `EnemyDefinition`은 JSON에서 읽은 정적 데이터로 둔다.
- `EnemyState` 생성은 별도 Factory에서 담당한다.
- `cdb`, `sdb`, `pdb` 참조는 실행하지 않더라도 존재 검증은 한다.
- 테스트 우선으로 작은 단위 PR을 만든다.
- 기존 테스트가 실패하면 새 구조에 맞게 최소 수정하되, 불필요한 대규모 리팩터링은 하지 않는다.
- 마지막에는 반드시 `./gradlew test`를 실행하고 결과를 요약한다.

---

# PR 1. JSON 기반 EnemyDefinition 콘텐츠 레이어 추가

## Codex 프롬프트

Duel Tower 프로젝트에 JSON 기반 EnemyDefinition 콘텐츠 레이어를 추가해줘.

이번 PR은 전투 흐름을 바꾸지 않고, 적 정적 정의를 JSON에서 로드하고 검증하는 기반만 만든다.

### 구현 목표

- `src/main/resources/balance/enemies.json` 추가
- `content/enemy/model` 패키지 추가
- `content/enemy/service` 패키지 추가
- `EnemyContentLoader`가 `balance/enemies.json`을 로드하도록 구현
- `EnemyService`가 로드된 적 정의를 검증하고 ID 기반 조회를 제공하도록 구현
- 카드/상태/패시브 참조 무결성을 검증
- 아직 `StartCombatCommand`, `EncounterTableConfig`, `EnemyState`는 건드리지 않는다
- AI 관련 코드는 만들지 않는다

### 새로 만들 파일

- `src/main/resources/balance/enemies.json`
- `src/main/java/com/example/dueltower/content/enemy/model/EnemyDefinition.java`
- `src/main/java/com/example/dueltower/content/enemy/model/EnemyStatsDefinition.java`
- `src/main/java/com/example/dueltower/content/enemy/model/EnemyRole.java`
- `src/main/java/com/example/dueltower/content/enemy/model/EnemyStatusRef.java`
- `src/main/java/com/example/dueltower/content/enemy/model/EnemyPassiveRef.java`
- `src/main/java/com/example/dueltower/content/enemy/model/EnemyContentRaw.java`
- `src/main/java/com/example/dueltower/content/enemy/service/EnemyContentLoader.java`
- `src/main/java/com/example/dueltower/content/enemy/service/EnemyService.java`

### `enemies.json` 요구사항

`src/main/resources/balance/enemies.json`은 다음 구조를 사용한다.

- 최상위 필드: `enemies`
- `enemies`는 배열
- 각 적은 다음 필드를 가진다.
  - `id`
  - `name`
  - `role`
  - `description`
  - `stats`
  - `deck`
  - `startStatuses`
  - `passives`

예시 데이터는 다음 내용을 포함한다.

- `E001_TRAINING_DUMMY`
  - 이름: `훈련용 허수아비`
  - role: `NORMAL`
  - maxHp: 30
  - maxActionPoint: 0
  - attackPower: 0
  - healPower: 0
  - deck: 빈 배열
  - startStatuses: 빈 배열
  - passives: 빈 배열

- `E002_TOWER_RAT`
  - 이름: `탑쥐`
  - role: `NORMAL`
  - maxHp: 18
  - maxActionPoint: 3
  - attackPower: 4
  - healPower: 0
  - deck: 빈 배열
  - startStatuses: 빈 배열
  - passives: 빈 배열

- `E003_GLASS_KNIGHT`
  - 이름: `유리 기사`
  - role: `ELITE`
  - maxHp: 42
  - maxActionPoint: 3
  - attackPower: 6
  - healPower: 0
  - deck: 빈 배열
  - startStatuses: 가능하면 기존 상태 ID 중 하나를 사용하되, 불확실하면 빈 배열로 둔다
  - passives: 빈 배열

상태 ID는 현재 프로젝트에서 사용하는 실제 ID 체계와 맞아야 한다. 만약 상태 ID가 한글명 기반인지, `S001_SHIELD` 같은 문자열 기반인지 확실하지 않으면 `startStatuses`는 빈 배열로 두고 검증 구조만 만든다.

### 모델 설계

`EnemyDefinition`은 정규화된 도메인 모델이다.

필드:

- `String id`
- `String name`
- `EnemyRole role`
- `String description`
- `EnemyStatsDefinition stats`
- `List<CardDefId> deck`
- `List<EnemyStatusRef> startStatuses`
- `List<EnemyPassiveRef> passives`

`EnemyStatsDefinition` 필드:

- `int maxHp`
- `int maxActionPoint`
- `int attackPower`
- `int healPower`

`EnemyRole` enum:

- `NORMAL`
- `ELITE`
- `BOSS`
- `EVENT`

`EnemyStatusRef` 필드:

- `String statusId`
- `int stacks`

`EnemyPassiveRef` 필드:

- `String passiveId`

`EnemyContentRaw`는 JSON 로딩용 raw 모델로 둔다.

`EnemyContentRaw` 요구사항:

- 최상위 record는 `List<EnemyRaw> enemies`를 가진다.
- `EnemyRaw`는 JSON 원본 필드를 문자열/nullable wrapper 타입으로 받는다.
- `EnemyStatsRaw`는 `Integer maxHp`, `Integer maxActionPoint`, `Integer attackPower`, `Integer healPower`를 사용한다.
- raw 모델에서 도메인 모델로 변환할 때 필수값 누락과 잘못된 값을 명확한 예외 메시지로 처리한다.

### EnemyContentLoader 요구사항

`EnemyContentLoader`는 Spring `@Component`로 만든다.

생성자에서 다음 리소스를 주입받는다.

- `${duel.balance.enemies:classpath:balance/enemies.json}`

기존 `EncounterTables`의 JSON 로딩 방식과 비슷하게 `tools.jackson.databind.ObjectMapper`를 사용한다.

`loadAll()` 메서드를 제공한다.

- 반환 타입: `List<EnemyDefinition>`
- 리소스가 없거나 읽기 실패 시 `IllegalStateException` 발생
- JSON 구조가 비어 있으면 `IllegalStateException` 발생
- role 문자열은 `EnemyRole.valueOf(...)`로 파싱하되, 잘못된 role이면 어떤 enemyId에서 실패했는지 메시지에 포함한다
- `deck`이 null이면 빈 리스트
- `startStatuses`가 null이면 빈 리스트
- `passives`가 null이면 빈 리스트
- `description`이 null이면 빈 문자열

### EnemyService 요구사항

`EnemyService`는 Spring `@Service`로 만든다.

생성자 주입:

- `EnemyContentLoader`
- `CardService`
- `StatusService`
- `PassiveService`

제공 메서드:

- `List<EnemyDefinition> list()`
- `EnemyDefinition get(String id)`
- `boolean exists(String id)`
- `Map<String, EnemyDefinition> defsMap()`

검증 규칙:

- enemy id는 null/blank 불가
- enemy name은 null/blank 불가
- enemy role은 null 불가
- enemy stats는 null 불가
- `maxHp > 0`
- `maxActionPoint >= 0`
- `attackPower >= 0`
- `healPower >= 0`
- enemy id 중복 금지
- `deck`의 모든 `CardDefId`는 `CardService`에 존재해야 한다
- `startStatuses`의 모든 `statusId`는 `StatusService`에 존재해야 한다
- `startStatuses.stacks > 0`
- `passives`의 모든 `passiveId`는 `PassiveService`에 존재해야 한다

참조 검증을 위해 필요하다면 `CardService`에 다음 메서드를 추가한다.

- `public boolean exists(CardDefId id)`

가능하면 `StatusService`, `PassiveService`에도 이미 있는 조회 메서드를 사용한다. 없으면 `exists` 계열 메서드를 추가해도 된다.

### 테스트 추가

다음 테스트를 추가한다.

- `src/test/java/com/example/dueltower/content/enemy/service/EnemyContentLoaderTest.java`
- `src/test/java/com/example/dueltower/content/enemy/service/EnemyServiceTest.java`

테스트 항목:

1. 기본 `balance/enemies.json`을 로드할 수 있다.
2. 로드 결과에 `E001_TRAINING_DUMMY`와 `E002_TOWER_RAT`이 포함된다.
3. enemy id 중복이면 실패한다.
4. `maxHp <= 0`이면 실패한다.
5. `maxActionPoint < 0`이면 실패한다.
6. `attackPower < 0`이면 실패한다.
7. `healPower < 0`이면 실패한다.
8. 존재하지 않는 카드 ID를 deck에 넣으면 실패한다.
9. 존재하지 않는 상태 ID를 startStatuses에 넣으면 실패한다.
10. 존재하지 않는 패시브 ID를 passives에 넣으면 실패한다.

테스트 방식은 프로젝트 기존 테스트 스타일을 따른다. 복잡한 Spring Context 테스트가 부담되면 단위 테스트로 시작하되, 최소 하나는 실제 `balance/enemies.json` 로딩을 검증한다.

### 이번 PR에서 건드리지 말 것

- `EncounterTableConfig`
- `EncounterTables`
- `StartCombatCommand`
- `EnemyState`
- `EngineContext`
- `SessionLifecycleService`
- AI 관련 클래스
- 적 턴 처리
- 적 카드 사용 처리

### 완료 조건

- `EnemyService`가 Spring Bean으로 정상 생성된다.
- `balance/enemies.json` 로딩이 가능하다.
- 참조 검증이 동작한다.
- 기존 테스트가 깨지지 않는다.
- `./gradlew test`가 통과한다.
- 테스트 결과를 요약한다.

---

# PR 2. Encounter JSON을 EnemyDefinition 참조 구조로 변경

## Codex 프롬프트

Duel Tower 프로젝트의 `balance/encounters.json`과 `EncounterTableConfig`를 EnemyDefinition 참조 구조로 변경해줘.

이번 PR은 인카운터 데이터가 적 수치를 직접 들고 있던 구조를 제거하고, `enemyDefId`를 통해 PR 1에서 만든 `EnemyDefinition`을 참조하도록 바꾸는 작업이다.

단, 이번 PR에서도 아직 전투 시작 흐름은 완전히 연결하지 않는다. `StartCombatCommand` 연결은 다음 PR에서 처리한다.

### 구현 목표

- `src/main/resources/balance/encounters.json` 구조 변경
- `EncounterTableConfig.EnemyTemplateRaw` 구조 변경
- `EncounterTableConfig.EnemyTemplate` 구조 변경
- 인카운터가 `enemyDefId`와 `instanceId`를 갖도록 변경
- `maxHp`, `attackPower`, `healingPower` 같은 직접 수치는 encounters에서 제거
- floor scaling 값은 encounter 쪽에 남긴다
- `instantiateEncounterEnemies(...)`는 제거하거나 deprecated 처리하고 더 이상 사용하지 않는 방향으로 정리한다
- `EncounterTableConfig`는 이제 EnemyState를 직접 만들지 않는다
- `EnemyDefinition` 참조 검증 테스트를 추가한다

### 변경할 파일

- `src/main/resources/balance/encounters.json`
- `src/main/java/com/example/dueltower/engine/config/EncounterTableConfig.java`
- 필요 시 `src/main/java/com/example/dueltower/engine/config/EncounterTables.java`
- 테스트 파일 추가

### 변경 전 문제

현재 `encounters.json`의 enemy 항목은 다음과 같은 직접 수치를 가진다.

- `enemyId`
- `maxHp`
- `hpPerFloor`
- `attackPower`
- `attackPowerPerFloor`
- `healingPower`
- `healingPowerPerFloor`

이 구조는 적 정의와 조우 구성을 섞고 있다.

변경 후에는 적의 기본 수치는 `enemies.json`의 `EnemyDefinition.stats`가 담당한다.

`encounters.json`은 “어떤 적 정의를 어떤 instanceId로 배치하고, 조우별/층별 보정을 얼마나 줄지”만 담당한다.

### 변경 후 encounters.json 구조

`src/main/resources/balance/encounters.json`을 다음 구조로 변경한다.

최상위 필드:

- `fallbackEncounterId`
- `encounters`

각 encounter 필드:

- `encounterId`
- `minFloor`
- `maxFloor`
- `requiredNodePhase`
- `enemies`

각 enemy template 필드:

- `enemyDefId`
- `instanceId`
- `hpPerFloor`
- `attackPowerPerFloor`
- `healingPowerPerFloor`

예시:

- `fallbackEncounterId`: `RUN-DEFAULT-COMBAT`
- encounter:
  - `encounterId`: `RUN-DEFAULT-COMBAT`
  - `minFloor`: 1
  - `requiredNodePhase`: `COMBAT`
  - enemies:
    - `enemyDefId`: `E002_TOWER_RAT`
    - `instanceId`: `RUN-ENEMY-1`
    - `hpPerFloor`: 4
    - `attackPowerPerFloor`: 1
    - `healingPowerPerFloor`: 0

### EncounterTableConfig 변경 요구사항

`EnemyTemplateRaw`를 변경한다.

필드:

- `String enemyDefId`
- `String instanceId`
- `Integer hpPerFloor`
- `Integer attackPowerPerFloor`
- `Integer healingPowerPerFloor`

`EnemyTemplate`를 변경한다.

필드:

- `String enemyDefId`
- `String instanceId`
- `int hpPerFloor`
- `int attackPowerPerFloor`
- `int healingPowerPerFloor`

검증 규칙:

- `enemyDefId`는 null/blank 불가
- `instanceId`는 null/blank 불가
- `hpPerFloor`는 null이면 0으로 처리해도 된다
- `attackPowerPerFloor`는 null이면 0으로 처리해도 된다
- `healingPowerPerFloor`는 null이면 0으로 처리해도 된다
- 같은 encounter 안에서 `instanceId` 중복 금지
- `encounter.enemies`는 비어 있으면 안 된다
- 기존 `fallbackEncounterId` 검증은 유지한다
- 기존 `minFloor`, `maxFloor`, `requiredNodePhase` 검증은 유지한다

### instantiateEncounterEnemies 정리

현재 `EncounterTableConfig`가 직접 `EnemyState`를 만드는 메서드를 가지고 있다면 다음 중 하나로 처리한다.

추천 1순위:

- `instantiateEncounterEnemies(RunState runState)` 제거
- 이 메서드를 호출하던 곳은 다음 PR에서 변경할 예정이므로, 이번 PR에서 컴파일을 위해 필요하다면 임시로 유지하지 말고 PR 3까지 같이 진행해도 된다

만약 PR 2를 독립적으로 통과시키기 어렵다면 추천 2순위:

- `instantiateEncounterEnemies(RunState runState)`를 deprecated 처리
- 내부에서 더 이상 사용하지 않도록 표시
- 하지만 새 구조에서는 EnemyDefinition 없이 EnemyState 생성이 불가능하므로 가능하면 제거한다

실제 컴파일이 깨지는 경우에는 `StartCombatCommand`의 해당 호출부를 임시로 최소 수정할 수 있다. 다만 실제 EnemyDefinition 기반 생성은 PR 3에서 진행한다.

### EncounterTableConfig에 floor 계산 공개 메서드 추가

다음 PR에서 EnemyStateFactory가 floorDelta를 계산해야 한다.

기존 private floor 계산 로직이 있다면 공개 메서드로 빼라.

예시 메서드:

- `public int resolveFloor(RunState runState)`

또는 static utility:

- `public static int resolveFloorFor(RunState runState)`

기존 선택 로직과 동일한 기준을 써야 한다.

기준:

- `runState.currentNode()`가 있고 node phase가 `COMBAT`이면 current node의 floor 사용
- 아니면 `runState.floor()` 사용
- 최소 1 보장

### EnemyDefinition 참조 검증

이번 PR에서 `EncounterTableConfig` 자체는 `EnemyService`를 알 필요가 없다.

대신 별도 테스트에서 `EncounterTables`로 로드한 encounters와 `EnemyService`의 defs를 비교해 참조 무결성을 검증한다.

테스트 이름 예시:

- `EncounterEnemyDefinitionReferenceTest`

검증 항목:

1. 모든 encounter enemy template의 `enemyDefId`가 `EnemyService`에 존재한다.
2. 같은 encounter 안에서 `instanceId`가 중복되지 않는다.
3. fallbackEncounterId가 실제 encounters에 존재한다.
4. 기본 `balance/encounters.json`을 로드할 수 있다.
5. 기본 encounter가 최소 하나의 enemy template을 가진다.

### 이번 PR에서 하지 말 것

- `EnemyStateFactory` 구현
- `StartCombatCommand`와 완전 연결
- AI 구현
- 적 자동 행동
- 적 카드 사용
- 인텐트 UI
- EnemyDefinition을 DB로 저장

### 완료 조건

- `balance/encounters.json`이 enemyDefId 참조 구조로 변경된다.
- `EncounterTableConfig`가 새 JSON 구조를 정상 파싱한다.
- encounter 내부 instanceId 중복 검증이 동작한다.
- encounter의 enemyDefId 참조 검증 테스트가 통과한다.
- 기존 테스트가 깨지면 새 구조에 맞게 최소 수정한다.
- `./gradlew test`가 통과한다.
- 테스트 결과를 요약한다.

---

# PR 3. StartCombatCommand를 EnemyDefinition 기반 EnemyState 생성으로 연결

## Codex 프롬프트

Duel Tower 프로젝트에서 전투 시작 시 `EncounterTemplate`의 `enemyDefId`를 통해 `EnemyDefinition`을 조회하고, 이를 기반으로 `EnemyState`를 생성하도록 연결해줘.

이번 PR은 PR 1의 EnemyDefinition 콘텐츠 레이어와 PR 2의 encounter 참조 구조를 실제 전투 시작 흐름에 연결하는 작업이다.

AI, 적 자동 행동, 적 카드 사용은 여전히 구현하지 않는다.

### 구현 목표

- `EnemyState`에 정의 기반 필드 최소 추가
- `EnemyStateFactory` 추가
- `EngineContext`에 enemy definition map 추가
- `SessionLifecycleService`에서 `EnemyService`를 주입하고 `EngineContext`에 전달
- `StartCombatCommand.ensureRunEncounterExists(...)`가 `EnemyDefinition`을 사용하도록 변경
- 전투 시작 시 enemy startStatuses와 passives가 EnemyState에 반영되도록 구현
- 기존 런타임 구조를 최대한 유지

### 변경할 파일

- `src/main/java/com/example/dueltower/engine/model/EnemyState.java`
- `src/main/java/com/example/dueltower/engine/core/enemy/EnemyStateFactory.java`
- `src/main/java/com/example/dueltower/engine/core/EngineContext.java`
- `src/main/java/com/example/dueltower/session/service/SessionLifecycleService.java`
- `src/main/java/com/example/dueltower/engine/command/StartCombatCommand.java`
- 필요 시 관련 테스트 fixture

### EnemyState 변경 요구사항

현재 `EnemyState`는 런타임 상태를 담는다. 여기에 다음 필드를 추가한다.

- `String enemyDefId`
- `String name`
- `int maxAp`
- `List<String> passiveIds`

추가 메서드:

- `String enemyDefId()`
- `void enemyDefId(String enemyDefId)`
- `String name()`
- `void name(String name)`
- `int maxAp()`
- `void maxAp(int value)`
- `List<String> passiveIds()`
- `void passiveIds(Collection<String> value)`

규칙:

- `enemyDefId`는 trim해서 저장한다.
- `name()`은 name이 없으면 `enemyId.value()`를 fallback으로 반환한다.
- `maxAp`는 0 이상으로 clamp한다.
- `maxAp` 변경 시 현재 `ap`가 `maxAp`를 넘으면 clamp한다.
- `passiveIds()`는 unmodifiable view를 반환한다.
- `passiveIds(Collection<String>)`는 null이면 빈 목록으로 처리한다.
- blank passive id는 예외 처리한다.
- 기존 `EnemyState` 생성자와 기존 테스트를 최대한 깨지 않도록 한다.

### EnemyStateFactory 추가

새 파일:

- `src/main/java/com/example/dueltower/engine/core/enemy/EnemyStateFactory.java`

정적 factory로 구현한다.

메서드:

- `public static EnemyState create(EncounterTableConfig.EnemyTemplate template, EnemyDefinition definition, int floorDelta)`

생성 규칙:

- `maxHp = max(1, definition.stats().maxHp() + template.hpPerFloor() * floorDelta)`
- `attackPower = max(0, definition.stats().attackPower() + template.attackPowerPerFloor() * floorDelta)`
- `healPower = max(0, definition.stats().healPower() + template.healingPowerPerFloor() * floorDelta)`
- `EnemyState.enemyId`는 `template.instanceId()`를 사용한다
- `EnemyState.enemyDefId`는 `definition.id()`를 사용한다
- `EnemyState.name`은 `definition.name()`을 사용한다
- `EnemyState.maxAp`는 `definition.stats().maxActionPoint()`를 사용한다
- `EnemyState.ap`는 일단 `maxActionPoint`로 채운다
- `EnemyState.attackPower`는 계산된 attackPower 사용
- `EnemyState.healPower`는 계산된 healPower 사용
- `EnemyState.passiveIds`는 `definition.passives().passiveId` 목록으로 채운다
- `definition.startStatuses()`는 `EnemyState.statusAdd(statusId, stacks)`로 반영한다

이번 PR에서 deck은 실행하지 않는다.

### EngineContext 변경 요구사항

`EngineContext`에 enemy definition map을 추가한다.

필드:

- `Map<String, EnemyDefinition> enemyDefs`

메서드:

- `public boolean hasEnemyDef(String id)`
- `public EnemyDefinition enemyDef(String id)`

`enemyDef(String id)`는 없으면 `IllegalArgumentException`을 던진다.

기존 생성자 오버로드가 많으므로 최대한 안전하게 수정한다.

추천 방식:

- 가장 긴 private/main 생성자에 `enemyDefs` 파라미터 추가
- 기존 오버로드들은 `Map.of()`를 전달하도록 유지
- `SessionLifecycleService`에서 사용하는 생성자에는 `enemyService.defsMap()`을 전달할 수 있게 한다
- 생성자 변경으로 기존 테스트가 많이 깨질 수 있으니, 기존 테스트용 생성자들은 가능한 유지한다

### SessionLifecycleService 변경 요구사항

`SessionLifecycleService`에 `EnemyService`를 생성자 주입한다.

필드 추가:

- `private final EnemyService enemyService;`

생성자 파라미터 추가:

- `EnemyService enemyService`

`createSession(...)`에서 `EngineContext` 생성 시 `enemyService.defsMap()`을 전달한다.

주의:

- 기존 `CardService`, `StatusService`, `KeywordService`, `PassiveService`, `CardModifierService`, `ItemService`, `EquipService` 흐름을 유지한다.
- 생성자 파라미터 순서가 너무 길어지므로, import와 필드 할당을 정리한다.
- 컴파일 오류가 나지 않도록 모든 생성자 호출부를 확인한다.

### StartCombatCommand 변경 요구사항

현재 전투 시작 시 적이 비어 있으면 encounter table에서 적을 직접 생성하던 흐름을 바꾼다.

새 흐름:

1. 이미 `state.enemies()`가 비어 있지 않으면 return
2. `EncounterTableConfig.EncounterTemplate encounter = ctx.encounterTable().selectEncounter(state.runState())`
3. floor 계산
4. anchorFloor 계산
5. floorDelta 계산
6. encounter.enemies 반복
7. 각 enemy template의 `enemyDefId`로 `ctx.enemyDef(enemyDefId)` 조회
8. `EnemyStateFactory.create(template, definition, floorDelta)` 호출
9. `state.enemies().put(enemy.enemyId(), enemy)` 등록
10. instanceId 중복이면 명확한 예외 발생
11. 로그 이벤트에 instanceId와 enemyDefId를 함께 남김

floor 계산은 기존 `EncounterTableConfig`의 기준과 일치해야 한다.

기준:

- current combat node가 있으면 current node floor
- 아니면 runState.floor()
- 최소 1

### 로그 예시

적 배치 로그는 기존 로그를 유지하되 정의 ID를 같이 표시하면 좋다.

예시 메시지:

- `런 인카운터 적이 배치되었다: RUN-ENEMY-1 (E002_TOWER_RAT)`

### 테스트 추가

다음 테스트를 추가한다.

- `src/test/java/com/example/dueltower/engine/core/enemy/EnemyStateFactoryTest.java`
- `src/test/java/com/example/dueltower/engine/command/StartCombatEnemyDefinitionTest.java`

`EnemyStateFactoryTest` 검증 항목:

1. EnemyDefinition 기본 수치로 EnemyState가 생성된다.
2. floorDelta가 HP/공격력/치유력에 반영된다.
3. maxHp는 최소 1로 clamp된다.
4. attackPower는 최소 0으로 clamp된다.
5. healPower는 최소 0으로 clamp된다.
6. enemyDefId가 채워진다.
7. name이 채워진다.
8. maxAp와 ap가 채워진다.
9. startStatuses가 statusValues에 반영된다.
10. passives가 passiveIds에 반영된다.

`StartCombatEnemyDefinitionTest` 검증 항목:

1. StartCombatCommand 실행 시 encounter의 enemyDefId를 통해 EnemyState가 생성된다.
2. 생성된 enemy의 `enemyDefId`가 expected value와 같다.
3. 생성된 enemy의 `name`이 EnemyDefinition의 이름과 같다.
4. 생성된 enemy의 hp/maxHp가 EnemyDefinition과 encounter scaling 기준으로 계산된다.
5. 생성된 enemy의 attackPower/healPower가 EnemyDefinition과 encounter scaling 기준으로 계산된다.
6. 적 instanceId가 `EnemyState.enemyId`로 사용된다.
7. encounter 내 instanceId 중복 시 실패한다.
8. 존재하지 않는 enemyDefId면 실패한다.

테스트 fixture는 가능한 한 작은 `GameState`, `EngineContext`, `EncounterTableConfig`를 직접 만들어 사용한다. Spring Context 전체를 띄우는 테스트는 필요한 경우에만 사용한다.

### 이번 PR에서 하지 말 것

- Enemy AI
- EnemyAiPolicy
- 적 자동 행동
- TurnFlow에서 적 행동 실행
- 적 카드 사용
- 적 deck shuffle/draw
- 적 intent DTO
- UI 수정
- DB 수정

### 완료 조건

- 전투 시작 시 `enemies.json`의 EnemyDefinition을 참조해 EnemyState가 생성된다.
- 기존 `balance/encounters.json`의 enemyDefId 기반 encounter가 정상 작동한다.
- `EngineContext`가 enemyDefs를 제공한다.
- `SessionLifecycleService`에서 새 EngineContext 생성이 정상 동작한다.
- 기존 테스트가 새 구조에 맞게 통과한다.
- `./gradlew test`가 통과한다.
- 테스트 결과를 요약한다.

---

# PR 4. 콘텐츠 검증과 문서 정리

## Codex 프롬프트

Duel Tower 프로젝트의 JSON 기반 EnemyDefinition 도입 후속 정리 작업을 해줘.

이번 PR은 기능 추가가 아니라 검증 강화와 문서화가 목적이다.

AI, 적 자동 행동, 적 카드 사용은 여전히 구현하지 않는다.

### 구현 목표

- EnemyDefinition/Encounter 참조 검증 테스트 보강
- 에러 메시지 개선
- `docs`에 적 콘텐츠 작성 가이드 추가
- `enemies.json`과 `encounters.json` 작성 규칙 명문화
- 향후 AI/적 카드 사용 확장 지점을 문서화하되 구현하지 않는다

### 새 문서

다음 문서를 추가한다.

- `docs/enemy-content-json.md`

문서 내용:

1. 전체 구조 설명
2. `enemies.json` 역할
3. `encounters.json` 역할
4. EnemyDefinition과 EnemyState의 차이
5. `enemyDefId`와 `instanceId`의 차이
6. `deck`, `startStatuses`, `passives` 필드의 의미
7. 이번 버전에서 AI는 제외되어 있음을 명시
8. 적 카드 사용은 아직 실행되지 않고 참조 검증만 한다는 점 명시
9. 새 적 추가 절차
10. 새 encounter 추가 절차
11. 흔한 오류와 해결법
12. 테스트 실행 명령

### 문서에 포함할 핵심 설명

`EnemyDefinition`:

- 정적 콘텐츠 정의
- JSON에서 로드됨
- id/name/role/stats/deck/startStatuses/passives를 가짐
- 전투 중 변경되지 않음

`EnemyState`:

- 전투 중 런타임 상태
- hp/ap/statusValues/passiveIds 등을 가짐
- `EnemyDefinition`을 바탕으로 생성됨
- 전투 중 변경됨

`enemyDefId`:

- 어떤 적 정의를 사용할지 가리키는 ID
- 예: `E002_TOWER_RAT`

`instanceId`:

- 특정 encounter에서 생성되는 적 개체 ID
- 예: `RUN-ENEMY-1`
- 같은 전투 안에서 중복되면 안 됨

`deck`:

- cdb 카드 ID 목록
- 현재는 참조 검증만 함
- 적 카드 사용 로직은 아직 없음

`startStatuses`:

- 전투 시작 시 적에게 적용할 상태 목록
- statusId와 stacks를 가짐
- 실제 StatusService에 존재해야 함

`passives`:

- pdb 패시브 ID 목록
- 현재는 EnemyState.passiveIds에 보관
- 패시브 실행 연결은 별도 PR에서 다룰 수 있음

### 검증 강화

다음 케이스를 테스트로 추가하거나 기존 테스트에 보강한다.

1. enemies.json에 중복 enemy id가 있으면 실패
2. enemies.json에 잘못된 role이 있으면 실패
3. enemies.json에 blank name이 있으면 실패
4. encounters.json에 blank enemyDefId가 있으면 실패
5. encounters.json에 blank instanceId가 있으면 실패
6. encounters.json에 같은 encounter 내 중복 instanceId가 있으면 실패
7. encounter가 없는 fallbackEncounterId면 실패
8. encounter enemyDefId가 enemies.json에 없으면 실패
9. startStatuses stacks가 0 이하이면 실패
10. deck에 없는 카드 ID가 있으면 실패
11. passives에 없는 passive ID가 있으면 실패

### 에러 메시지 개선

검증 실패 메시지는 다음 정보를 포함하도록 한다.

- 파일 또는 콘텐츠 종류
- enemyId 또는 encounterId
- 문제 필드명
- 잘못된 값

예시:

- `invalid enemy stat: enemyId=E002_TOWER_RAT, field=maxHp, value=0`
- `missing enemy definition referenced by encounter: encounterId=RUN-DEFAULT-COMBAT, enemyDefId=E999_UNKNOWN`
- `duplicate enemy instance id in encounter: encounterId=RUN-DEFAULT-COMBAT, instanceId=RUN-ENEMY-1`

### 이번 PR에서 하지 말 것

- 기능 동작 변경
- AI 구현
- 적 자동 행동
- 적 카드 사용
- UI 수정
- DB 수정

### 완료 조건

- 문서가 추가된다.
- 새 적/인카운터 작성법이 문서만 보고 이해 가능하다.
- 검증 테스트가 강화된다.
- `./gradlew test`가 통과한다.
- 테스트 결과를 요약한다.

---

# 전체 구현 순서 추천

## 권장 순서

1. PR 1: EnemyDefinition JSON 로딩 레이어
2. PR 2: Encounter JSON을 enemyDefId 참조 구조로 변경
3. PR 3: StartCombatCommand와 EnemyStateFactory 연결
4. PR 4: 검증 강화와 문서 정리

## 합쳐도 되는 PR

프로젝트 상황에 따라 PR 2와 PR 3은 같이 진행해도 된다.

이유:

- `EncounterTableConfig`에서 직접 `EnemyState`를 만들던 기존 메서드를 제거하면 `StartCombatCommand`도 같이 수정해야 컴파일이 맞을 수 있다.
- 다만 작업이 커지므로 가능하면 PR 2는 데이터 구조 변경, PR 3은 실제 전투 연결로 나누는 편이 안전하다.

## 절대 섞지 말 것

아래 작업은 이번 묶음과 섞지 않는다.

- 적 AI
- 적 턴 자동 처리
- 적 카드 사용
- 보스 패턴
- 인텐트 UI
- DB 콘텐츠화
- YAML 전환

---

# 최종 검수 체크리스트

## 데이터

- `balance/enemies.json` 존재
- `balance/encounters.json`이 `enemyDefId` 구조 사용
- `encounters.json`에서 직접 maxHp/attackPower/healingPower를 들고 있지 않음
- `enemyDefId`와 `instanceId`가 분리되어 있음

## 코드

- `EnemyDefinition`과 `EnemyState` 역할이 분리되어 있음
- `EnemyService`가 적 정의를 로드하고 검증함
- `EnemyStateFactory`가 EnemyDefinition으로 EnemyState를 생성함
- `EngineContext`에서 enemy definition 조회가 가능함
- `SessionLifecycleService`가 EnemyService를 EngineContext에 연결함
- `StartCombatCommand`가 EnemyDefinition 기반으로 적을 배치함

## 테스트

- Enemy JSON 로딩 테스트 있음
- Enemy 참조 검증 테스트 있음
- Encounter enemyDefId 참조 검증 테스트 있음
- EnemyStateFactory 테스트 있음
- StartCombatCommand 연결 테스트 있음
- `./gradlew test` 통과

## 범위 준수

- AI 없음
- 적 자동 행동 없음
- 적 카드 사용 없음
- 인텐트 없음
- DB 변경 없음
- YAML 없음

