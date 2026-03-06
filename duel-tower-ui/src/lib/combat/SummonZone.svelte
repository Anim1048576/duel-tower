<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { ActionDescriptor } from '../model'
  import type { TeamSummon } from './types'
  import DisabledReason from '../components/DisabledReason.svelte'

  const dispatch = createEventDispatcher<{ summonAction: { action: ActionDescriptor } }>()

  export let summons: TeamSummon[] = []
  export let actionBySummonId: Record<string, ActionDescriptor | undefined> = {}
  export let actionLocked = false
  export let lockReason = ''
</script>

<section class="panel">
  <div class="row" style="justify-content:space-between">
    <div class="panelTitle">SummonZone</div>
    <span class="badge">{summons.length}</span>
  </div>
  <div class="spacer"></div>

  {#if !summons.length}
    <div class="hint">소환수 없음</div>
  {:else}
    <div class="stack">
      {#each summons as s (s.summonId)}
        {@const action = actionBySummonId[s.summonId]}
        <div class="ti">
          <div class="row" style="justify-content:space-between">
            <b>{s.summonId}</b>
            <span class="badge">HP {s.hp}</span>
          </div>
          <div class="hint">ATK {s.atk} · HEAL {s.heal} · owner {s.owner}</div>
          <div class="spacer"></div>
          <button class="btn" disabled={actionLocked || Boolean(action?.disabledReason)} title={lockReason || action?.disabledReason || ''} on:click={() => action && dispatch('summonAction', { action })}>소환 행동</button>
          <DisabledReason show={actionLocked || Boolean(action?.disabledReason)} reason={lockReason || action?.disabledReason || ''} />
        </div>
      {/each}
    </div>
  {/if}
</section>

<style>
  .stack{display:flex; flex-direction:column; gap:8px}
</style>
