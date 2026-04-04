# Item Blueprint Name Rule

- item blueprint는 `ItemBlueprint`를 구현한다.
- 파일명 규칙 예시
  - `I001_SmallPotion.java`
  - `I002_Antidote.java`
- `public static final String ID` 상수 사용을 권장한다.
- `definition().id()`와 `id()`는 항상 동일해야 한다.
- Spring 빈으로 로드되도록 `@Component` 등록이 필요하다.
- 실제 효과 로직은 추후 `ItemEffect` 훅 확장 이후 단계에서 구현한다.
