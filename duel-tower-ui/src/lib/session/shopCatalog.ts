export type ShopCatalogOffer = {
  offerId: string
  refId: string
  price: number
  stock: number
  bound: boolean
}

export type ShopOfferFallback = {
  name: string
  summary: string
  description: string
  tags: string[]
}

export const defaultShopCatalog: readonly ShopCatalogOffer[] = [
  { offerId: 'O-1', refId: 'I-1', price: 50, stock: 5, bound: false },
  { offerId: 'O-2', refId: 'I-2', price: 200, stock: 5, bound: false },
  { offerId: 'O-3', refId: 'I-3', price: 500, stock: 5, bound: false },
  { offerId: 'O-4', refId: 'I-4', price: 50, stock: 5, bound: false },
  { offerId: 'O-5', refId: 'I-5', price: 200, stock: 5, bound: false },
  { offerId: 'O-6', refId: 'I-6', price: 250, stock: 5, bound: false },
  { offerId: 'O-7', refId: 'I-7', price: 500, stock: 5, bound: false },
  { offerId: 'O-8', refId: 'E-1', price: 200, stock: 5, bound: false },
  { offerId: 'O-9', refId: 'E-2', price: 250, stock: 5, bound: false },
  { offerId: 'O-10', refId: 'I-8', price: 25, stock: 5, bound: false },
] as const

const fallbackOfferMap: Record<string, ShopOfferFallback> = {
  'E-1': {
    name: 'Sturdy Spear',
    summary: '기본 상점 무기 상품입니다.',
    description:
      '상세 장비 설명이 없습니다.',
    tags: ['equipment', 'weapon'],
  },
  'E-2': {
    name: 'Portable Pistol',
    summary: '기본 상점 탄약 무기 상품입니다.',
    description:
      '상세 장비 설명이 없습니다.',
    tags: ['equipment', 'weapon', 'ammo'],
  },
  'I-1': {
    name: 'Cheap Healing Potion',
    summary: '기본 상점 상품입니다.',
    description: '상세 상품 설명이 없습니다.',
    tags: ['item', 'consumable'],
  },
  'I-2': {
    name: 'Healing Potion',
    summary: '기본 상점 상품입니다.',
    description: '상세 상품 설명이 없습니다.',
    tags: ['item', 'consumable'],
  },
  'I-3': {
    name: 'Advanced Healing Potion',
    summary: '기본 상점 상품입니다.',
    description: '상세 상품 설명이 없습니다.',
    tags: ['item', 'consumable'],
  },
  'I-4': {
    name: 'Cheap Barrier Generator',
    summary: '기본 상점 상품입니다.',
    description: '상세 상품 설명이 없습니다.',
    tags: ['item', 'consumable'],
  },
  'I-5': {
    name: 'Barrier Generator',
    summary: '기본 상점 상품입니다.',
    description: '상세 상품 설명이 없습니다.',
    tags: ['item', 'consumable'],
  },
  'I-6': {
    name: 'Antidote',
    summary: '기본 상점 상품입니다.',
    description: '상세 상품 설명이 없습니다.',
    tags: ['item', 'consumable'],
  },
  'I-7': {
    name: 'Emergency Smoke Bomb',
    summary: '기본 상점 상품입니다.',
    description: '상세 상품 설명이 없습니다.',
    tags: ['item', 'consumable'],
  },
  'I-8': {
    name: 'Bullet Bundle',
    summary: '기본 탄약 묶음 상품입니다.',
    description: '상세 상품 설명이 없습니다.',
    tags: ['item', 'ammo', 'special'],
  },
}

export function isEquipmentOfferRef(refId: string) {
  return refId.trim().toUpperCase().startsWith('E-')
}

export function getShopOfferFallback(refId: string): ShopOfferFallback {
  const normalizedRefId = refId.trim().toUpperCase()

  return (
    fallbackOfferMap[normalizedRefId] ?? {
      name: normalizedRefId,
      summary: '기본 상점 상품입니다.',
      description:
        '상세 상품 설명이 없습니다.',
      tags: ['shop'],
    }
  )
}
