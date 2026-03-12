<script lang="ts">
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { navigate } from '../lib/router'
  import { info, warn } from '../stores/log'
  import { combat, command, refreshState } from '../stores/combat'
  import NodeChoiceCard, { type NodeChoice } from '../lib/components/node/NodeChoiceCard.svelte'
  import NodeConfirmModal from '../lib/components/node/NodeConfirmModal.svelte'

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

  $: choices = $combat.state?.run?.availableChoices ?? []
  $: nodes = choices.map((choice) => ({
    id: choice.id,
    name: choice.name,
    typeLabel: choice.typeLabel,
    rule: choice.rule,
    phase: normalizePhase(choice.phase),
    danger: normalizeDanger(choice.danger),
    disabled: Boolean(choice.disabled),
    disabledReason: choice.disabledReason,
  })) as NodeChoice[]

  function selectNode(node: NodeChoice) {
    selectedNode = node
    confirmOpen = true
    info('노드 선택', `${node.name} (${node.typeLabel})`)
  }

  function closeConfirm() {
    confirmOpen = false
  }

  async function confirmNode(node: NodeChoice) {
    confirmOpen = false

    const res = await command({
      type: 'SELECT_NODE_CHOICE',
      choiceId: node.id,
    })
    if (!res?.accepted) {
      warn('노드 선택 실패', node.name)
      await refreshState()
      return
    }

    if (node.phase === 'combat') {
      info('노드 확정', `${node.name} 진입 · 전투 페이즈 이동`)
      navigate('/combat')
      return
    }

    info('노드 확정', `${node.name} 결과 확인`)
    navigate('/results')
  }
</script>

<PageSkeleton title="Node" summary="서버 상태 기반 노드 선택">
  <div class="hint">세션 run.availableChoices를 기준으로 노드를 렌더링한다.</div>

  {#if !$combat.state}
    <div class="hint" style="margin-top:12px">세션 상태를 불러오는 중...</div>
  {:else if !nodes.length}
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
