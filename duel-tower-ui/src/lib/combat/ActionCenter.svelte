<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { ActionStage, PendingAction } from './types'

  const dispatch = createEventDispatcher<{ cancel: void; confirm: void }>()

  export let stage: ActionStage = 'idle'
  export let action: PendingAction | null = null
  export let busy = false
</script>

<section class="board">
  <div class="boardTop">
    <div>
      <div class="panelTitle">ActionCenter</div>
      <div class="hint">행동 선택 → 대상 선택 → 확정</div>
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
        <div class="hint">대상: {action.target ? `${action.target.type} · ${action.target.playerId}${action.target.summonId ? `/${action.target.summonId}` : ''}` : '미선택'}</div>
      {/if}
    </div>
  {/if}

  <div class="spacer"></div>
  <div class="row" style="justify-content:flex-end">
    <button class="btn" disabled={busy || stage === 'idle'} on:click={() => dispatch('cancel')}>취소</button>
    <button class="btn primary" disabled={busy || stage !== 'confirming'} on:click={() => dispatch('confirm')}>확정</button>
  </div>
</section>
