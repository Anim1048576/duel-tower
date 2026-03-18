export type DetailKind = 'card' | 'status'

export type DetailItem = {
  kind: DetailKind
  name: string
  summary: string
  description: string
  tags?: string[]
  stats?: Array<{ label: string; value: string | number }>
}
