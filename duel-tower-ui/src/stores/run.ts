import { writable } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'

export type NodeType = 'BATTLE' | 'EVENT' | 'SHOP' | 'REST' | 'ELITE' | 'BOSS'

export type RunNode = {
  floor: number
  type: NodeType
  title: string
  desc: string
}

export type RunState = {
  seed: number
  floor: number
  history: RunNode[]
  choices: RunNode[]
}

function rand(seed: number) {
  let x = seed | 0
  return () => {
    x ^= x << 13
    x ^= x >>> 17
    x ^= x << 5
    return ((x >>> 0) % 1_000_000) / 1_000_000
  }
}

function makeChoices(floor: number, seed: number): RunNode[] {
  const r = rand(seed ^ (floor * 9973))
  const pool: { type: NodeType; title: string; desc: string }[] = [
    { type: 'BATTLE', title: '전투', desc: '일반 전투. 보상으로 카드/재화가 나올 수 있다.' },
    { type: 'EVENT', title: '이벤트', desc: '선택형 이벤트. 리스크/리턴이 크다.' },
    { type: 'SHOP', title: '상점', desc: '카드/아이템 구매, 덱 정리.' },
    { type: 'REST', title: '휴식', desc: '회복 또는 강화.' },
    { type: 'ELITE', title: '정예', desc: '난이도 상승. 좋은 보상.' },
  ]
  const pick = () => pool[Math.floor(r() * pool.length)]
  const out: RunNode[] = []
  while (out.length < 3) {
    const p = pick()
    if (out.some((x) => x.type === p.type)) continue
    out.push({ floor, ...p })
  }
  return out
}

const seed: RunState = load(KEY.run, {
  seed: Math.floor(Math.random() * 1_000_000_000),
  floor: 1,
  history: [],
  choices: [],
})

if (!seed.choices.length) seed.choices = makeChoices(seed.floor, seed.seed)

export const run = writable<RunState>(seed)
run.subscribe((v) => save(KEY.run, v))

export function resetRun() {
  const s = Math.floor(Math.random() * 1_000_000_000)
  run.set({ seed: s, floor: 1, history: [], choices: makeChoices(1, s) })
}

export function pickNode(index: number) {
  run.update((r) => {
    const node = r.choices[index]
    if (!node) return r
    const nextFloor = r.floor + 1
    return {
      ...r,
      floor: nextFloor,
      history: [node, ...r.history].slice(0, 20),
      choices: makeChoices(nextFloor, r.seed),
    }
  })
}
