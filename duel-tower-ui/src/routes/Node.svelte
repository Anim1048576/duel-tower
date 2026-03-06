<script lang="ts">
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { navigate } from '../lib/router'
  import { info, warn } from '../stores/log'
  import { setExplorationResult } from '../stores/exploration'
  import NodeChoiceCard, { type NodeChoice } from '../lib/components/node/NodeChoiceCard.svelte'
  import NodeConfirmModal from '../lib/components/node/NodeConfirmModal.svelte'
  import JudgementModal from '../lib/components/node/JudgementModal.svelte'

  const nodes: NodeChoice[] = [
    { id: 'N-1', name: '회랑 정찰', typeLabel: '판정', rule: '판정 성공 시 안전한 지름길 발견', phase: 'judgement', danger: 'mid' },
    { id: 'N-2', name: '붕괴 전장', typeLabel: '전투', rule: '적 선공 확률 증가', phase: 'combat', danger: 'high' },
    { id: 'N-3', name: '폐허 저장고', typeLabel: '이벤트', rule: '보상 카드 1장 획득', phase: 'event', danger: 'low' },
    { id: 'N-4', name: '봉인된 균열', typeLabel: '전투', rule: '열쇠 미보유 시 입장 불가', phase: 'combat', danger: 'high', disabled: true, disabledReason: '균열 열쇠가 없어 진입할 수 없음' },
  ]

  let selectedNode: NodeChoice | null = null
  let confirmOpen = false
  let judgementOpen = false

  function selectNode(node: NodeChoice) {
    selectedNode = node
    confirmOpen = true
    info('노드 선택', `${node.name} (${node.typeLabel})`)
  }

  function closeConfirm() {
    confirmOpen = false
  }

  function confirmNode(node: NodeChoice) {
    confirmOpen = false

    if (node.phase === 'judgement') {
      judgementOpen = true
      return
    }

    if (node.phase === 'combat') {
      info('노드 확정', `${node.name} 진입 · 전투 페이즈 이동`)
      navigate('/combat')
      return
    }

    setExplorationResult(node.name, [
      {
        id: `reward-${Date.now()}`,
        type: 'reward',
        title: '보상 획득',
        summary: `${node.name} 이벤트 완료`,
        detail: '기억 파편 2개와 카드 강화 재료 1개를 획득했다.',
      },
    ])
    info('노드 확정', `${node.name} 이벤트 결과 확인`)
    navigate('/results')
  }

  function onJudgementResolved(result: { success: boolean; gap: number; memoryAccepted: boolean; roll: number }) {
    judgementOpen = false
    if (!selectedNode) return

    const summary = result.success ? '보상 획득' : '탐사 실패'
    const detail = result.success
      ? `격차 ${result.gap}로 판정 성공. 기억 수용: ${result.memoryAccepted ? '예' : '아니오'}`
      : `격차 ${result.gap}로 판정 실패. 다음 노드에서 회복 필요.`

    setExplorationResult(selectedNode.name, [
      {
        id: `judge-${Date.now()}`,
        type: result.success ? 'reward' : 'explore_fail',
        title: summary,
        summary: `${selectedNode.name} · d20 ${result.roll}`,
        detail,
      },
    ])

    if (result.success) info('Judgement 성공', detail)
    else warn('Judgement 실패', detail)

    navigate('/results')
  }
</script>

<PageSkeleton title="Node" summary="탐색 플로우 전용 UI">
  <div class="hint">노드 선택 → 확인 모달 → 페이즈 분기(Judgement/Combat/EventResult)</div>

  <div class="list" style="margin-top:12px">
    {#each nodes as node (node.id)}
      <NodeChoiceCard {node} onSelect={selectNode} />
    {/each}
  </div>
</PageSkeleton>

<NodeConfirmModal open={confirmOpen} node={selectedNode} onCancel={closeConfirm} onConfirm={confirmNode} />
<JudgementModal open={judgementOpen} nodeName={selectedNode?.name ?? ''} onCancel={() => (judgementOpen = false)} onResolve={onJudgementResolved} />
