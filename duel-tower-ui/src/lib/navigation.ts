export type PageArea = 'public' | 'app'

export type PageKey =
  | 'login'
  | 'signup'
  | 'hub'
  | 'home'
  | 'lobby'
  | 'inventory'
  | 'shop'
  | 'cards'
  | 'decks'
  | 'card-detail'
  | 'character'
  | 'character-create'
  | 'character-detail'
  | 'reference'
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

export type RouteParams = Record<string, string>

export type RouteMatch = {
  page: PageDefinition
  params: RouteParams
}

export const routePaths = {
  home: '/',
  login: '/',
  signup: '/signup',
  hub: '/hub',
  characterList: '/characters',
  characterCreate: '/characters/new',
  characterDetail: '/characters/detail',
  cardLibrary: '/cards',
  deckList: '/decks',
  inventory: '/inventory',
  shop: '/shop',
  reference: '/reference',
  deckEditor: '/decks/editor',
  sessionEntry: '/lobby',
  sessionLobbyPlayer: '/lobby/player',
  sessionLobbyGm: '/lobby/gm',
  combat: '/combat',
} as const

export const routePatterns = {
  characterDetail: '/characters/:id',
  cardDetail: '/cards/:id',
  deckEditor: '/decks/:id/editor',
  sessionLobbyPlayer: '/sessions/:code/player',
  sessionLobbyGm: '/sessions/:code/gm',
  combat: '/sessions/:code/combat',
} as const

function hasRouteParam(value?: string): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function encodeRouteSegment(value: string) {
  return encodeURIComponent(value.trim())
}

function buildPathFromPattern(pattern: string, params: RouteParams) {
  return pattern.replace(/:([A-Za-z0-9_]+)/g, (_, key: string) => {
    const value = params[key]

    if (!hasRouteParam(value)) {
      throw new Error(`Missing route parameter: ${key}`)
    }

    return encodeRouteSegment(value)
  })
}

export const pathBuilders = {
  home: () => routePaths.home,
  login: () => routePaths.login,
  signup: () => routePaths.signup,
  hub: () => routePaths.hub,
  characterList: () => routePaths.characterList,
  characterCreate: () => routePaths.characterCreate,
  characterDetail: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.characterDetail, { id })
      : routePaths.characterDetail,
  cardLibrary: () => routePaths.cardLibrary,
  deckList: () => routePaths.deckList,
  inventory: () => routePaths.inventory,
  shop: () => routePaths.shop,
  cardDetail: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.cardDetail, { id })
      : routePaths.cardLibrary,
  reference: () => routePaths.reference,
  deckEditor: (id?: string) =>
    hasRouteParam(id)
      ? buildPathFromPattern(routePatterns.deckEditor, { id })
      : routePaths.deckEditor,
  sessionEntry: () => routePaths.sessionEntry,
  sessionLobbyPlayer: (code?: string) =>
    hasRouteParam(code)
      ? buildPathFromPattern(routePatterns.sessionLobbyPlayer, { code })
      : routePaths.sessionLobbyPlayer,
  sessionLobbyGm: (code?: string) =>
    hasRouteParam(code)
      ? buildPathFromPattern(routePatterns.sessionLobbyGm, { code })
      : routePaths.sessionLobbyGm,
  combat: (code?: string) =>
    hasRouteParam(code)
      ? buildPathFromPattern(routePatterns.combat, { code })
      : routePaths.combat,
} as const

export const PAGES: PageDefinition[] = [
  {
    key: 'home',
    label: 'Home',
    path: routePaths.home,
    title: 'Home',
    description: '?쒖옉 ?붾㈃?낅땲??',
  },
  {
    key: 'lobby',
    label: 'Lobby / Session',
    path: routePaths.sessionEntry,
    title: 'Lobby / Session',
    description: '?몄뀡??留뚮뱾嫄곕굹 李멸??⑸땲??',
  },
  {
    key: 'inventory',
    label: 'Inventory',
    path: routePaths.inventory,
    title: 'Inventory',
    description: '?꾩옱 蹂댁쑀 ?먯썝???뺤씤?⑸땲??',
  },
  {
    key: 'shop',
    label: 'Shop',
    path: routePaths.shop,
    title: 'Shop',
    description: '?곸젏 ?곹뭹???뺤씤?섍퀬 援щℓ?⑸땲??',
  },
  {
    key: 'cards',
    label: 'Cards',
    path: routePaths.cardLibrary,
    title: 'Cards',
    description: '移대뱶 紐⑸줉怨??곸꽭 ?뺣낫瑜??뺤씤?⑸땲??',
  },
  {
    key: 'decks',
    label: 'Decks',
    path: routePaths.deckList,
    title: 'Decks',
    description: '?깆쓣 議고쉶?섍퀬 ?몄쭛?⑸땲??',
  },
  {
    key: 'character',
    label: 'Character',
    path: '/character',
    title: 'Character',
    description: '罹먮┃??紐⑸줉怨??꾨줈?꾩쓣 ?뺤씤?⑸땲??',
  },
  {
    key: 'reference',
    label: 'Reference',
    path: routePaths.reference,
    title: 'Reference',
    description: '?ㅼ썙?? ?곹깭, ?⑥떆釉?洹쒖튃???뺤씤?⑸땲??',
  },
  {
    key: 'combat',
    label: 'Combat',
    path: routePaths.combat,
    title: 'Combat',
    description: '?꾪닾 ?곹깭? 紐낅졊???뺤씤?⑸땲??',
  },
  {
    key: 'logs',
    label: 'Logs / Results',
    path: '/logs',
    title: 'Logs / Results',
    description: '?꾪닾 濡쒓렇瑜??뺤씤?⑸땲??',
  },
]

const pageMap = new Map(PAGES.map((page) => [page.path, page]))

export type AppNavItem = {
  key: Exclude<PageKey, 'login' | 'signup'>
  label: string
  path: string
  description: string
  enabled: boolean
}

export const APP_NAV_ITEMS = [
  {
    key: 'hub',
    label: 'Hub',
    path: routePaths.hub,
    description: '주요 화면 바로가기',
    enabled: true,
  },
  {
    key: 'character',
    label: 'Characters',
    path: routePaths.characterList,
    description: '캐릭터 목록과 프로필',
    enabled: true,
  },
  {
    key: 'inventory',
    label: 'Inventory',
    path: routePaths.inventory,
    description: '현재 보유 자원',
    enabled: true,
  },
  {
    key: 'shop',
    label: 'Shop',
    path: routePaths.shop,
    description: '상점 목록',
    enabled: true,
  },
  {
    key: 'cards',
    label: 'Cards',
    path: routePaths.cardLibrary,
    description: '카드 목록과 상세',
    enabled: true,
  },
  {
    key: 'decks',
    label: 'Decks',
    path: routePaths.deckList,
    description: '덱 목록과 편집',
    enabled: true,
  },
  {
    key: 'reference',
    label: 'Reference',
    path: routePaths.reference,
    description: '규칙 참고',
    enabled: true,
  },
  {
    key: 'lobby',
    label: 'Session',
    path: routePaths.sessionEntry,
    description: '세션 입장과 로비',
    enabled: true,
  },
] satisfies AppNavItem[]

const activePageMap = new Map<string, PageDefinition>([
  [
    routePaths.login,
    {
      key: 'login',
      label: 'Login',
      path: routePaths.login,
      area: 'public',
      title: 'Archive Access',
      description: '濡쒓렇???붾㈃?낅땲??',
      eyebrow: 'Public Entry',
    },
  ],
  [
    routePaths.signup,
    {
      key: 'signup',
      label: 'Signup',
      path: routePaths.signup,
      area: 'public',
      title: 'Create Archive Access',
      description: '?뚯썝媛???붾㈃?낅땲??',
      eyebrow: 'Public Entry',
    },
  ],
  [
    routePaths.hub,
    {
      key: 'hub',
      label: 'Hub',
      path: routePaths.hub,
      area: 'app',
      title: 'Duel Tower Hub',
      description: '二쇱슂 ?붾㈃?쇰줈 ?대룞?⑸땲??',
      eyebrow: 'Grand Archive',
      tags: [
        { label: 'Overview', tone: 'accent' },
        { label: 'Archive', tone: 'success' },
      ],
    },
  ],
  [
    routePaths.characterList,
    {
      key: 'character',
      label: 'Characters',
      path: routePaths.characterList,
      area: 'app',
      title: 'Character Roster',
      description: '罹먮┃?곕? ?좏깮?섍퀬 ?곸꽭濡??대룞?⑸땲??',
      eyebrow: 'Roster Ledger',
      tags: [{ label: 'Roster', tone: 'accent' }],
    },
  ],
  [
    routePaths.deckList,
    {
      key: 'decks',
      label: 'Decks',
      path: routePaths.deckList,
      area: 'app',
      title: 'Deck Archive',
      description: '?깆쓣 ?좏깮?섍퀬 ?몄쭛?⑸땲??',
      eyebrow: 'Deck Ledger',
      tags: [{ label: 'Deck Archive', tone: 'accent' }],
    },
  ],
  [
    routePaths.inventory,
    {
      key: 'inventory',
      label: 'Expedition Supply',
      path: routePaths.inventory,
      area: 'app',
      title: 'Expedition Supply',
      description: '?꾩옱 蹂댁쑀 ?먯썝???뺤씤?⑸땲??',
      eyebrow: 'Supply Locker',
      tags: [{ label: 'Inventory', tone: 'accent' }],
    },
  ],
  [
    routePaths.shop,
    {
      key: 'shop',
      label: 'Expedition Shop',
      path: routePaths.shop,
      area: 'app',
      title: 'Expedition Shop',
      description: '?곹뭹???뺤씤?섍퀬 援щℓ?⑸땲??',
      eyebrow: 'Field Merchant',
      tags: [{ label: 'Shop', tone: 'accent' }],
    },
  ],
  [
    routePaths.cardLibrary,
    {
      key: 'cards',
      label: 'Card Library',
      path: routePaths.cardLibrary,
      area: 'app',
      title: 'Card Library',
      description: '移대뱶瑜?寃?됲븯怨??곸꽭瑜??뺤씤?⑸땲??',
      eyebrow: 'Content Library',
      tags: [{ label: 'Card Archive', tone: 'accent' }],
    },
  ],
  [
    routePatterns.cardDetail,
    {
      key: 'card-detail',
      label: 'Card Detail',
      path: routePatterns.cardDetail,
      area: 'app',
      title: 'Card Detail',
      description: '?좏깮??移대뱶 ?뺣낫瑜??뺤씤?⑸땲??',
      eyebrow: 'Card Record',
      tags: [{ label: 'Card Archive', tone: 'accent' }],
    },
  ],
  [
    routePaths.reference,
    {
      key: 'reference',
      label: 'Tactical Reference',
      path: routePaths.reference,
      area: 'app',
      title: 'Tactical Reference',
      description: '?ㅼ썙?? ?곹깭, ?⑥떆釉뚮? ?뺤씤?⑸땲??',
      eyebrow: 'Rules Codex',
      tags: [{ label: 'Codex', tone: 'accent' }],
    },
  ],
  [
    routePaths.sessionEntry,
    {
      key: 'lobby',
      label: 'Session Entry',
      path: routePaths.sessionEntry,
      area: 'app',
      title: 'Session Entry',
      description: '?몄뀡??留뚮뱾嫄곕굹 李멸??⑸땲??',
      eyebrow: 'Session Gate',
      tags: [{ label: 'Session Flow', tone: 'accent' }],
    },
  ],
  [
    routePaths.characterDetail,
    {
      key: 'character-detail',
      label: 'Character Detail',
      path: routePaths.characterDetail,
      area: 'app',
      title: 'Character Detail / Edit',
      description: '?좏깮??罹먮┃?곕? ?뺤씤?섍퀬 ?섏젙?⑸땲??',
      eyebrow: 'Record Detail',
      tags: [{ label: 'Character Record', tone: 'accent' }],
    },
  ],
  [
    routePaths.characterCreate,
    {
      key: 'character-create',
      label: 'Character Create',
      path: routePaths.characterCreate,
      area: 'app',
      title: 'Character Create',
      description: '??罹먮┃?곕? ?앹꽦?⑸땲??',
      eyebrow: 'Record Creation',
      tags: [{ label: 'Character Record', tone: 'accent' }],
    },
  ],
  [
    routePaths.deckEditor,
    {
      key: 'deck-editor',
      label: 'Deck Editor',
      path: routePaths.deckEditor,
      area: 'app',
      title: 'Deck Editor',
      description: '?좏깮???깆쓣 ?뺤씤?섍퀬 ?섏젙?⑸땲??',
      eyebrow: 'Tactical Editor',
      tags: [{ label: 'Deck Editor', tone: 'accent' }],
    },
  ],
  [
    routePaths.sessionLobbyPlayer,
    {
      key: 'player-lobby',
      label: 'Player Lobby',
      path: routePaths.sessionLobbyPlayer,
      area: 'app',
      title: 'Player Lobby',
      description: '?뚮젅?댁뼱 濡쒕퉬 ?곹깭瑜??뺤씤?⑸땲??',
      eyebrow: 'Session Lobby',
      tags: [{ label: 'Player View', tone: 'accent' }],
    },
  ],
  [
    routePaths.sessionLobbyGm,
    {
      key: 'gm-lobby',
      label: 'GM Lobby',
      path: routePaths.sessionLobbyGm,
      area: 'app',
      title: 'GM Lobby',
      description: 'GM 濡쒕퉬 ?곹깭瑜?愿由ы빀?덈떎.',
      eyebrow: 'Session Control',
      tags: [{ label: 'GM View', tone: 'accent' }],
    },
  ],
  [
    routePaths.combat,
    {
      key: 'combat',
      label: 'Combat Command',
      path: routePaths.combat,
      area: 'app',
      title: 'Combat Command',
      description: '?꾪닾 ?곹깭? 紐낅졊???뺤씤?⑸땲??',
      eyebrow: 'Battlefield Control',
      tags: [{ label: 'Battle View', tone: 'accent' }],
    },
  ],
])

type DynamicRouteSourceEntry = {
  pattern: string
  page: PageDefinition | undefined
}

type DynamicRouteEntry = {
  pattern: string
  page: PageDefinition
}

function hasDynamicRoutePage(entry: DynamicRouteSourceEntry): entry is DynamicRouteEntry {
  return entry.page !== undefined
}

function createDynamicRouteEntries(entries: readonly DynamicRouteSourceEntry[]) {
  return entries.filter(hasDynamicRoutePage)
}

const dynamicRouteEntries = createDynamicRouteEntries([
  { pattern: routePatterns.characterDetail, page: activePageMap.get(routePaths.characterDetail) },
  { pattern: routePatterns.cardDetail, page: activePageMap.get(routePatterns.cardDetail) },
  { pattern: routePatterns.deckEditor, page: activePageMap.get(routePaths.deckEditor) },
  { pattern: routePatterns.sessionLobbyPlayer, page: activePageMap.get(routePaths.sessionLobbyPlayer) },
  { pattern: routePatterns.sessionLobbyGm, page: activePageMap.get(routePaths.sessionLobbyGm) },
  { pattern: routePatterns.combat, page: activePageMap.get(routePaths.combat) },
])

function getPathSegments(pathname: string) {
  const normalized = normalizePath(pathname)

  if (normalized === routePaths.home) {
    return [] as string[]
  }

  return normalized.slice(1).split('/')
}

export function matchRoutePattern(pattern: string, pathname: string): RouteParams | null {
  const patternSegments = getPathSegments(pattern)
  const pathSegments = getPathSegments(pathname)

  if (patternSegments.length !== pathSegments.length) {
    return null
  }

  const params: RouteParams = {}

  for (const [index, patternSegment] of patternSegments.entries()) {
    const pathSegment = pathSegments[index]

    if (patternSegment.startsWith(':')) {
      const key = patternSegment.slice(1)

      if (!key || !pathSegment) {
        return null
      }

      params[key] = decodeURIComponent(pathSegment)
      continue
    }

    if (patternSegment !== pathSegment) {
      return null
    }
  }

  return params
}

export function normalizePath(pathname: string): string {
  if (!pathname) return routePaths.home

  const withoutHash = pathname.split('#')[0] ?? pathname
  const withoutQuery = withoutHash.split('?')[0] ?? withoutHash

  if (!withoutQuery) return routePaths.home

  return withoutQuery.endsWith('/') && withoutQuery !== routePaths.home
    ? withoutQuery.slice(0, -1)
    : withoutQuery
}

export function resolveRouteMatch(pathname: string): RouteMatch | null {
  const normalized = normalizePath(pathname)
  const staticPage = activePageMap.get(normalized) ?? pageMap.get(normalized)

  if (staticPage) {
    return {
      page: staticPage,
      params: {},
    }
  }

  for (const entry of dynamicRouteEntries) {
    const params = matchRoutePattern(entry.pattern, normalized)

    if (params) {
      const page: PageDefinition = {
        ...entry.page,
        path: normalized,
      }

      return {
        page,
        params,
      }
    }
  }

  return null
}

export function resolvePage(pathname: string): PageDefinition {
  return (
    resolveRouteMatch(pathname)?.page ??
    activePageMap.get(routePaths.login) ??
    pageMap.get(routePaths.home) ??
    PAGES[0]
  )
}
