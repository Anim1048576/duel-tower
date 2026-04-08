export type PageArea = 'public' | 'app'

export type PageKey =
  | 'login'
  | 'hub'
  | 'home'
  | 'lobby'
  | 'decks'
  | 'character'
  | 'character-detail'
  | 'inventory'
  | 'combat'
  | 'logs'
  | 'deck-editor'
  | 'player-lobby'
  | 'gm-lobby'

export type PageTag = {
  label: string
  tone?: 'accent' | 'muted' | 'success' | 'warning'
}

export type PageDefinition = {
  key: PageKey
  label: string
  path: string
  area?: PageArea
  title: string
  description: string
  eyebrow?: string
  tags?: PageTag[]
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

export const APP_NAV_ITEMS = [
  {
    key: 'hub',
    label: 'Hub',
    path: '/hub',
    description: 'Overview and central entry point',
    enabled: true,
  },
  {
    key: 'character',
    label: 'Characters',
    path: '/characters',
    description: 'Roster and detail records',
    enabled: true,
  },
  {
    key: 'decks',
    label: 'Decks',
    path: '/decks',
    description: 'List and editor flow',
    enabled: true,
  },
  {
    key: 'lobby',
    label: 'Session',
    path: '/lobby',
    description: 'Entry and lobby flow',
    enabled: true,
  },
  {
    key: 'combat',
    label: 'Combat',
    path: '/combat',
    description: 'Combat command screen',
    enabled: true,
  },
] satisfies AppNavItem[]

export type AppNavItem = {
  key: Exclude<PageKey, 'login'>
  label: string
  path: string
  description: string
  enabled: boolean
}

const activePageMap = new Map<string, PageDefinition>([
  [
    '/',
    {
      key: 'login',
      label: 'Login',
      path: '/',
      area: 'public',
      title: 'Archive Access',
      description: 'Public entry screen for archive access.',
      eyebrow: 'Public Entry',
    },
  ],
  [
    '/hub',
    {
      key: 'hub',
      label: 'Hub',
      path: '/hub',
      area: 'app',
      title: 'Duel Tower Hub',
      description: 'Internal hub for overview, status, and next work targets.',
      eyebrow: 'Grand Archive',
      tags: [
        { label: 'Batch 1', tone: 'accent' },
        { label: 'MVP Shell', tone: 'success' },
      ],
    },
  ],
  [
    '/characters',
    {
      key: 'character',
      label: 'Characters',
      path: '/characters',
      area: 'app',
      title: 'Character Roster',
      description: 'Browse the roster and move into the next detail step from the current selection.',
      eyebrow: 'Roster Ledger',
      tags: [{ label: 'Batch 2', tone: 'accent' }],
    },
  ],
  [
    '/decks',
    {
      key: 'decks',
      label: 'Decks',
      path: '/decks',
      area: 'app',
      title: 'Deck List',
      description: 'Review deck summaries and move into the next editor step from the current selection.',
      eyebrow: 'Tactical Archive',
      tags: [{ label: 'Batch 2', tone: 'accent' }],
    },
  ],
  [
    '/lobby',
    {
      key: 'lobby',
      label: 'Session Entry',
      path: '/lobby',
      area: 'app',
      title: 'Session Entry',
      description: 'Enter by code or choose from open sessions before moving into the player lobby.',
      eyebrow: 'Session Gate',
      tags: [{ label: 'Batch 2', tone: 'accent' }],
    },
  ],
  [
    '/characters/detail',
    {
      key: 'character-detail',
      label: 'Character Detail',
      path: '/characters/detail',
      area: 'app',
      title: 'Character Detail / Edit',
      description: 'Read the selected adventurer record and prepare the next edit step.',
      eyebrow: 'Record Detail',
      tags: [{ label: 'Batch 3', tone: 'accent' }],
    },
  ],
  [
    '/decks/editor',
    {
      key: 'deck-editor',
      label: 'Deck Editor',
      path: '/decks/editor',
      area: 'app',
      title: 'Deck Editor',
      description: 'Review the selected deck structure before wiring the real editor actions.',
      eyebrow: 'Tactical Editor',
      tags: [{ label: 'Batch 3', tone: 'accent' }],
    },
  ],
  [
    '/lobby/player',
    {
      key: 'player-lobby',
      label: 'Player Lobby',
      path: '/lobby/player',
      area: 'app',
      title: 'Player Lobby',
      description: 'Inspect session summary, participant slots, and readiness flow from the player side.',
      eyebrow: 'Session Lobby',
      tags: [{ label: 'Batch 3', tone: 'accent' }],
    },
  ],
  [
    '/lobby/gm',
    {
      key: 'gm-lobby',
      label: 'GM Lobby',
      path: '/lobby/gm',
      area: 'app',
      title: 'GM Lobby',
      description: 'Manage participant readiness and session controls from the GM side.',
      eyebrow: 'Session Control',
      tags: [{ label: 'Batch 4', tone: 'accent' }],
    },
  ],
  [
    '/combat',
    {
      key: 'combat',
      label: 'Combat Command',
      path: '/combat',
      area: 'app',
      title: 'Combat Command',
      description: 'Review battle state, issue commands, and track the action log in one screen.',
      eyebrow: 'Battlefield Control',
      tags: [{ label: 'Batch 4', tone: 'accent' }],
    },
  ],
])

// TODO: Expand fixed routes such as /characters/detail, /decks/editor, /lobby/player,
// and /lobby/gm into id/code-based routes when the data contract is finalized.

export function normalizePath(pathname: string): string {
  if (!pathname) return '/'
  return pathname.endsWith('/') && pathname !== '/' ? pathname.slice(0, -1) : pathname
}

export function resolvePage(pathname: string): PageDefinition {
  const normalized = normalizePath(pathname)
  return activePageMap.get(normalized) ?? activePageMap.get('/') ?? pageMap.get(normalized) ?? PAGES[0]
}
