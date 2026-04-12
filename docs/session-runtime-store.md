# Session Runtime Store

`SessionRuntimeStore`는 active session runtime 보관 경계를 분리하기 위해 도입됐다.

## 왜 필요한가

- 세션 생명주기 정책과 runtime 저장 구현을 분리하기 위해
- 현재 in-memory 구현을 유지하면서도 이후 저장소 교체 가능성을 확보하기 위해
- 다른 서비스가 `ConcurrentHashMap` 같은 내부 자료구조를 직접 알지 않게 만들기 위해

이번 단계의 목적은 Redis/DB 도입이 아니라 교체 가능성 확보다.

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

- store 책임
  - code 기준 저장
  - code 기준 조회
  - 삭제
  - 전체 엔트리 snapshot 나열
- lifecycle 책임
  - 세션 존재 판단과 예외 의미
  - last access 기반 TTL / expire 정책
  - cleanup 스케줄링
  - 공식 lock 진입 정책

Lock 책임은 store가 아니라 lifecycle에 둔다.

현재 서비스 코드는 `SessionLifecycleService#withLockedSession(...)`을 기준으로 잠금 진입을 읽는다.
예외적으로 `SessionAccessResolver`는 이미 확보된 동일 `SessionRuntime` 인스턴스에 대해 권한 판단을 이어서 수행해야 하므로 직접 `SessionRuntime.withLock(...)`를 사용한다.

## 현재 한계

- 아직 `InMemorySessionRuntimeStore`만 존재한다.
- `SessionRuntime`은 mutable in-memory 객체다.
- Redis 같은 외부 저장소로 가려면 직렬화, 동시성, 분산 lock 정책을 별도로 설계해야 한다.

즉, 지금 단계는 저장소 교체 가능성을 위한 경계 설계까지가 범위다.
