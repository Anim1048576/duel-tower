export type PageKey =
  | 'home'
  | 'lobby'
  | 'decks'
  | 'character'
  | 'inventory'
  | 'combat'
  | 'logs'

export type PageDefinition = {
  key: PageKey
  label: string
  path: string
  title: string
  description: string
}

export const PAGES: PageDefinition[] = [
  {
    key: 'home',
    label: 'Home',
    path: '/',
    title: 'Home',
    description: '새로운 UI 기반을 위한 출발 지점입니다. 핵심 대시보드는 추후 재구성됩니다.',
  },
  {
    key: 'lobby',
    label: 'Lobby / Session',
    path: '/lobby',
    title: 'Lobby / Session',
    description: '세션 준비, 참여, 관리 흐름은 이후 요구사항에 맞춰 다시 설계될 예정입니다.',
  },
  {
    key: 'decks',
    label: 'Decks',
    path: '/decks',
    title: 'Decks',
    description: '덱 편집과 상세 인터랙션은 이후 단계에서 새 UX 기준으로 재구축됩니다.',
  },
  {
    key: 'character',
    label: 'Character',
    path: '/character',
    title: 'Character',
    description: '캐릭터 프로필/성장 화면은 향후 API 계약에 맞춰 재작성될 예정입니다.',
  },
  {
    key: 'inventory',
    label: 'Inventory',
    path: '/inventory',
    title: 'Inventory',
    description: '인벤토리 조회 및 아이템 동작 UI는 다음 단계에서 다시 구현합니다.',
  },
  {
    key: 'combat',
    label: 'Combat',
    path: '/combat',
    title: 'Combat',
    description: '전투 보드 및 액션 UI는 별도 전투 설계안 기반으로 재개발될 예정입니다.',
  },
  {
    key: 'logs',
    label: 'Logs / Results',
    path: '/logs',
    title: 'Logs / Results',
    description: '전투 로그와 결과 뷰는 이후 정보 구조를 정리한 뒤 다시 구축됩니다.',
  },
]

const pageMap = new Map(PAGES.map((page) => [page.path, page]))

export function normalizePath(pathname: string): string {
  if (!pathname) return '/'
  return pathname.endsWith('/') && pathname !== '/' ? pathname.slice(0, -1) : pathname
}

export function resolvePage(pathname: string): PageDefinition {
  const normalized = normalizePath(pathname)
  return pageMap.get(normalized) ?? PAGES[0]
}
