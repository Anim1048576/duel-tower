<script lang="ts">
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { navigate } from '../lib/router'
  import { info, warn } from '../stores/log'
  import NodeChoiceCard from '../lib/components/node/NodeChoiceCard.svelte'
  import type { NodeChoice } from '../lib/components/node/types'
  import NodeConfirmModal from '../lib/components/node/NodeConfirmModal.svelte'
  import { combat, command } from '../stores/combat'
  import { session } from '../stores/session'

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
  $: runState = $combat.state?.run ?? null
  $: resultPending = Boolean(runState?.resultPending)
  $: currentNode = runState?.currentNode ?? null
  $: canStartCombat = Boolean($session.gmToken)

  async function selectNode(node: NodeChoice) {
    if (resultPending) {
      navigate('/results')
      return
    }
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
      if (!canStartCombat) {
        warn('GM 시작 대기', `${node.name} 선택 완료 · GM이 전투를 시작해야 합니다.`)
        navigate('/lobby')
        return
      }

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

  {#if resultPending}
    <div class="hint" style="margin-top:12px">현재 노드 <b>{currentNode?.name ?? '—'}</b> 결과를 먼저 확인해야 다음 선택지가 열립니다.</div>
    <div style="margin-top:12px">
      <button class="btn primary" on:click={() => navigate('/results')}>결과 확인</button>
    </div>
  {:else}

    {#if !nodes.length}
      <div class="hint" style="margin-top:12px">표시할 노드 선택지가 없다.</div>
    {:else}
      <div class="list" style="margin-top:12px">
        {#each nodes as node (node.id)}
          <NodeChoiceCard {node} onSelect={selectNode} />
        {/each}
      </div>
    {/if}
  {/if}
</PageSkeleton>

<NodeConfirmModal open={confirmOpen} node={selectedNode} onCancel={closeConfirm} onConfirm={confirmNode} />
