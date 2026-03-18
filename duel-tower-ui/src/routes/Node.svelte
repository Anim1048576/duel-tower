<script lang="ts">
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { navigate } from '../lib/router'
  import { info, warn } from '../stores/log'
  import NodeChoiceCard from '../lib/components/node/NodeChoiceCard.svelte'
  import type { NodeChoice } from '../lib/components/node/types'
  import NodeConfirmModal from '../lib/components/node/NodeConfirmModal.svelte'
  import { combat, command } from '../stores/combat'

  let selectedNode: NodeChoice | null = null
  let confirmOpen = false

  function normalizeDanger(danger: string): NodeChoice['danger'] {
    const d = (danger || '').toLowerCase()
    if (d === 'high') return 'high'
    if (d === 'mid') return 'mid'
    return 'low'
  }

  function normalizePhase(phase: string): NodeChoice['phase'] {
    const p = (phase || '').toLowerCase()
    if (p === 'combat') return 'combat'
    if (p === 'judgement') return 'judgement'
    return 'event'
  }

  $: nodes = ($combat.state?.run?.availableChoices ?? []).map((choice) => ({
    id: choice.id,
    name: choice.name,
    typeLabel: choice.typeLabel,
    rule: choice.rule,
    phase: normalizePhase(choice.phase),
    danger: normalizeDanger(choice.danger),
    disabled: Boolean(choice.disabled),
    disabledReason: choice.disabledReason,
  })) as NodeChoice[]

  async function selectNode(node: NodeChoice) {
    selectedNode = node
    confirmOpen = true
    info('노드 선택', `${node.name} (${node.typeLabel})`)
  }

  function closeConfirm() {
    confirmOpen = false
  }

  async function confirmNode(node: NodeChoice) {
    confirmOpen = false
    const res = await command({ type: 'SELECT_NODE_CHOICE', choiceId: node.id })
    if (!res?.accepted) return

    if (node.phase === 'combat') {
      const started = await command({ type: 'START_COMBAT' })
      if (started?.accepted) {
        info('노드 확정', `${node.name} 진입 · 전투 페이즈 이동`)
        navigate('/combat')
      }
      return
    }

    if (node.phase === 'judgement') info('Judgement 성공', `${node.name} 판정 결과를 기록했다.`)
    else warn('탐색 결과', `${node.name} 이벤트 결과를 기록했다.`)

    navigate('/results')
  }
</script>

<PageSkeleton title="Node" summary="탐색 플로우 전용 UI">
  <div class="hint">서버 상태 기반 노드 선택</div>

  {#if !nodes.length}
    <div class="hint" style="margin-top:12px">표시할 노드 선택지가 없다.</div>
  {:else}
    <div class="list" style="margin-top:12px">
      {#each nodes as node (node.id)}
        <NodeChoiceCard {node} onSelect={selectNode} />
      {/each}
    </div>
  {/if}
</PageSkeleton>

<NodeConfirmModal open={confirmOpen} node={selectedNode} onCancel={closeConfirm} onConfirm={confirmNode} />
