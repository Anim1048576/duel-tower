# Combat Lab

Combat Lab은 실제 전투 세션을 시작하지 않고 주사위 표기와 카드 효과를 실험하는 일반 사용자용 Lab 기능입니다. 카드 고점/저점 비교, 빌드별 핵심 카드 효율 확인, 상태 조합 테스트, 카드 밸런스 확인에 사용할 수 있습니다.

프론트엔드는 주사위나 카드 효과를 다시 계산하지 않습니다. 입력을 구성해 Lab API를 호출하고, 백엔드가 반환한 결과를 그대로 표시합니다.

## 1. 개요

Combat Lab은 두 가지 도구로 구성됩니다.

- Dice API Tester: 서버의 DiceUtility를 통해 주사위 표기를 검증하고 굴림 결과, 기대값, 최소/최대값, 분포를 확인합니다.
- Effect Probe: 실제 전투 세션 없이 사용자가 입력한 공격력, 치유력, HP, 상태, 대상 상태를 기반으로 CardEffect.validate/resolve 결과를 관찰합니다.

주요 용도는 다음과 같습니다.

- 공격력/치유력 변화에 따른 카드 결과 비교
- 보호, 취약, 고통 같은 상태 조합 확인
- 카드 고점/저점 및 빌드별 핵심 카드 효율 비교
- 카드 밸런스 확인과 버그 리포트용 request/response 기록

## 2. Dice 사용법

Dice 탭은 `POST /api/lab/dice`를 호출합니다.

지원 표기 예시는 다음과 같습니다.

- `1d6`
- `d20`
- `3d6+2`
- `4D10 - 1`
- `1D6+2D4+3`
- `4D6KH2`: 높은 값 2개만 선택해 합산
- `4D6KL2`: 낮은 값 2개만 선택해 합산
- `4D6DH2`: 높은 값 2개를 제거하고 남은 값 합산
- `4D6DL2`: 낮은 값 2개를 제거하고 남은 값 합산
- `4D6MAX`: 가장 높은 값 1개만 선택
- `4D6MIN`: 가장 낮은 값 1개만 선택
- `3D6KH2+2D4MAX+1`

입력 필드:

- notation: 주사위 표기입니다. 예: `3D6KH2+2D4MAX+1`
- rollCount: 굴림 횟수입니다. `0`이면 실제 굴림 없이 parse/min/max/expected 정보만 확인합니다.
- seed: 선택 입력입니다. 같은 seed와 같은 요청이면 같은 rolls를 재현할 수 있습니다.

결과 필드:

- spec: 단일 단순 주사위식일 때 제공되는 하위 호환 필드입니다. 복합식이나 selector 표기에서는 `null`일 수 있습니다.
- expression: 서버가 파싱한 항 목록입니다. 프론트는 이 값을 표시만 하며 다시 파싱하지 않습니다.
- min/max: 가능한 최소값과 최대값입니다.
- expected: 기대값입니다. 프론트 표시를 위해 문자열로 내려옵니다.
- expectedAvailable: 기대값 계산 가능 여부입니다.
- expectedNote: 기대값을 제공할 수 없을 때의 설명입니다.
- rolls: 서버가 실제로 굴린 결과 목록입니다.
- histogram: 서버가 계산한 value/count 분포입니다. 프론트는 이 값을 재집계하지 않습니다.

선택형 주사위(`KH`, `KL`, `DH`, `DL`, `MAX`, `MIN`)가 포함된 경우 현재 기대값 계산은 제공되지 않을 수 있습니다. 이때 API는 요청을 실패시키지 않고 `expectedAvailable=false`, `expected="N/A"`와 `expectedNote`를 반환합니다. min/max/rolls/histogram은 정상적으로 확인할 수 있습니다.

## 3. Effect Probe 사용법

Effect Probe 탭은 `GET /api/lab/effects/cards`와 `POST /api/lab/effects/probe`를 사용합니다.

기본 흐름:

1. Card 목록을 불러오고 실험할 카드를 선택합니다.
2. Actor 입력에서 attackPower, healPower, hp, maxHp, statuses를 설정합니다.
3. Target 입력에서 대상 kind, id, hp, maxHp, statuses를 설정합니다.
4. 필요하면 Selection 고급 입력에서 choiceId, discardIds, selectedIds, alias 기반 extra card 선택을 설정합니다.
5. Validate 또는 Resolve를 실행합니다.

Validate와 Resolve의 차이:

- Validate: `CardEffect.validate`만 실행합니다. resolve를 실행하지 않으므로 before와 after가 같아야 합니다.
- Resolve: validate가 성공했을 때만 `CardEffect.resolve`를 실행합니다.

결과 Summary 읽는 법:

- Card: 실행한 카드 이름과 ID입니다.
- Validation: validate 결과입니다. Invalid이면 validationErrors를 확인합니다.
- Resolve: resolve 실행 여부입니다.
- Target HP / Actor HP: 서버 response.before/after와 response.changes를 기반으로 표시합니다.
- Status changes: 서버 changes에 포함된 상태 변화 수입니다.
- Events: 서버가 반환한 GameEvent 목록입니다.
- Notes: Probe가 지원하거나 의도적으로 생략한 범위입니다.
- Raw JSON: request/response 원문입니다. 버그 리포트나 카드 밸런스 기록에 복사하기 좋습니다.

## 4. 실전 전투와 다른 점

Effect Probe는 CardEffect 관찰 도구입니다. 실제 PlayCardCommand나 세션 전투 흐름을 재현하지 않습니다.

의도적으로 생략하는 범위:

- AP 비용 검사 없음
- 손패 소유 검사 없음
- 실제 덱/묘지/제외/필드 이동 없음
- PlayCardCommand 사용 후 카드 이동 없음
- 턴 시작/턴 종료 처리 없음
- pending decision 전체 처리 없음
- 세션 권한 검사 없음
- expectedVersion 검사 없음
- GM/플레이어 세션 동기화 검증 없음

CardEffect 내부에서 직접 발생시키는 카드 이동이나 이벤트는 관찰될 수 있습니다. 다만 실전 카드 사용 전체 절차를 보장하지 않습니다.

## 5. 이런 용도에 적합

- 공격력 7일 때 기본 공격 피해량 확인
- 치유력 10일 때 회복 카드의 회복량 확인
- 보호/취약/고통 같은 상태 조합 확인
- 보호막이 있는 대상에게 공격했을 때 HP와 상태 변화 확인
- 카드 고점/저점 비교
- 빌드별 핵심 카드 효율 비교
- 카드 밸런스 조정 전후 결과 비교

## 6. 이런 용도에는 부적합

- 실제 전투 전체 재현
- 덱 순환 검증
- 손패/버림/드로우 흐름 검증
- 턴 시작/턴 종료 상태 처리 검증
- pending decision 전체 UX 검증
- 세션 권한과 expectedVersion 동기화 검증
- 여러 사용자가 동시에 참여하는 전투 운영 검증

## 7. 예시 시나리오

### 기본 공격 카드

1. Card에서 기본 공격 계열 카드를 선택합니다.
2. Actor attackPower를 `7`로 설정합니다.
3. Target kind를 `ENEMY`, HP를 `30`, maxHp를 `30`으로 설정합니다.
4. Resolve를 실행합니다.
5. Summary에서 Target HP 변화와 Changes를 확인합니다.

### 회복 카드

1. 회복 카드를 선택합니다.
2. Actor healPower를 `10`으로 설정합니다.
3. Target kind를 `PLAYER`로 설정하거나 actor 자신을 대상으로 설정합니다.
4. Target HP를 낮게 설정합니다. 예: hp `8`, maxHp `20`
5. Resolve 후 Target HP 또는 Actor HP 변화를 확인합니다.

### 상태 부여 카드

1. 상태 부여 카드를 선택합니다.
2. Actor attackPower/healPower를 조정합니다.
3. Target statuses를 `{}`로 두거나 기존 상태를 입력합니다.
4. Resolve 후 Status changes를 확인합니다.

### 보호막이 있는 대상에게 공격

1. 공격 카드를 선택합니다.
2. Target statuses에 다음처럼 입력합니다.

```json
{
  "SHIELD": 3
}
```

3. Resolve를 실행합니다.
4. Target HP 변화와 SHIELD 상태 변화를 확인합니다.

## 8. 개발자 참고

주요 경로:

- Frontend route: `/lab`
- Dice API: `POST /api/lab/dice`
- Effect card list API: `GET /api/lab/effects/cards`
- Effect Probe API: `POST /api/lab/effects/probe`

프론트엔드 책임:

- 입력 UI 구성
- JSON 입력 파싱
- Lab API 호출
- 백엔드 response 표시

프론트엔드가 하지 않는 일:

- dice parser 구현
- 주사위 expected/min/max/histogram 계산
- damage/heal/status 계산
- CardEffect 결과 예측
- changes 재계산

백엔드 책임:

- 기존 DiceUtility 호출
- EngineContext에서 CardDefinition/CardEffect 조회
- Probe용 GameState 구성
- CardEffect.validate/resolve 실행
- before/after/changes/events/notes 반환

Effect Probe 확장 입력 예:

```json
{
  "targets": [
    { "kind": "ENEMY", "id": "enemy_a", "hp": 30, "maxHp": 30 },
    { "kind": "ENEMY", "id": "enemy_b", "hp": 25, "maxHp": 25 }
  ],
  "extraCards": [
    { "alias": "hand_1", "cardId": "C001", "zone": "HAND" }
  ],
  "selection": {
    "discardAliases": ["hand_1"],
    "selectedAliases": []
  }
}
```

하위 호환을 위해 기존 단일 `target` request도 계속 지원합니다. `target`과 `targets`가 함께 있으면 단일 `target`을 우선 처리하고, 그 사실은 response notes에 표시합니다.
