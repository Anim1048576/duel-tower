<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { ActionStage, PendingAction } from './types'

  const dispatch = createEventDispatcher<{ cancel: void; confirm: void }>()

  export let stage: ActionStage = 'idle'
  export let action: PendingAction | null = null
  export let busy = false

  function describeTarget() {
    if (!action?.target) return '미선택'
    if (action.target.type === 'player') return `player · ${action.target.playerId}`
    if (action.target.type === 'summon') return `summon · ${action.target.playerId}/${action.target.summonId}`
    return `enemy · ${action.target.enemyId}`
  }
</script>

<section class="board">
  <div class="boardTop">
    <div>
      <div class="panelTitle">ActionCenter</div>
      <div class="hint">행동 선택 → validTargets 하이라이트 → 확정</div>
    </div>
    <div class="badge">{stage.toUpperCase()}</div>
  </div>

  <div class="spacer"></div>

  {#if !action}
    <div class="hint">아래 존에서 행동을 선택해 주세요.</div>
  {:else}
    <div class="ti">
      <div class="logHead">선택된 행동</div>
      <div class="logBody">{action.label}</div>
      {#if action.requiresTarget}
        <div class="hint">대상: {describeTarget()}</div>
      {/if}
      {#if action.disabledReason}
        <div class="hint" style="color: var(--danger)">{action.disabledReason}</div>
      {/if}
    </div>
  {/if}

  <div class="spacer"></div>
  <div class="row" style="justify-content:flex-end">
    <button class="btn" disabled={busy || stage === 'idle'} on:click={() => dispatch('cancel')}>취소</button>
    <button class="btn primary" disabled={busy || stage !== 'confirming' || Boolean(action?.disabledReason)} on:click={() => dispatch('confirm')}>확정</button>
  </div>
</section>
