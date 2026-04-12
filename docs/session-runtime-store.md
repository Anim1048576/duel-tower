# Session Runtime Store

`SessionRuntimeStore`는 active session runtime 보관 경계를 분리하기 위해 도입됐다.

## 왜 필요한가

- 세션 생명주기 정책과 runtime 저장 구현을 분리하기 위해
- 현재 in-memory 구현을 유지하면서도 이후 저장소 교체 가능성을 확보하기 위해
- 다른 서비스가 `ConcurrentHashMap` 같은 내부 자료구조를 직접 알지 않게 만들기 위해

이번 단계의 목표는 저장소 교체 가능성 확보이지, Redis/DB 저장소 도입이 아니다.

## 현재 구조

- `SessionLifecycleService`
  - 세션 생성 / 조회 / 삭제 / 만료 정리
  - TTL / expire 판단
  - 공식 lock 진입점 `withLockedSession(...)`
- `SessionRuntimeStore`
  - runtime 저장 / 조회 / 삭제 / 나열
- `InMemorySessionRuntimeStore`
  - 현재 유일한 구현체
  - 내부적으로 `ConcurrentHashMap<String, SessionRuntime>` 사용

## 책임 경계

- 저장소 책임:
  - code 기준 저장
  - code 기준 조회
  - 삭제
  - 전체 엔트리 snapshot 나열
- lifecycle 책임:
  - 세션 존재 판단과 예외 의미
  - last access 기반 TTL / expire 정책
  - cleanup 스케줄링
  - lock 진입 정책

Lock 책임은 store가 아니라 lifecycle에 둔다.
현재는 `SessionRuntime` 내부 lock을 사용하지만, 서비스들은 `SessionLifecycleService#withLockedSession(...)`를 통해서만 잠금 진입을 읽을 수 있게 정리했다.

## 현재 한계

- 아직 `InMemorySessionRuntimeStore`만 존재한다.
- `SessionRuntime`은 mutable in-memory 객체다.
- Redis 같은 외부 저장소로 가려면 직렬화, 동시성, 분산 lock 정책을 별도로 설계해야 한다.

즉, 지금 단계는 저장소 교체 가능성을 위한 경계 설계까지가 범위다.
