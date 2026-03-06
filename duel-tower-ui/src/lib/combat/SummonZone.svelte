<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { TeamSummon } from './types'

  const dispatch = createEventDispatcher<{ summonAction: { summonId: string; ownerId: string } }>()

  export let summons: TeamSummon[] = []
  export let ownerId = ''
  export let disabled = false
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
        <div class="ti">
          <div class="row" style="justify-content:space-between">
            <b>{s.summonId}</b>
            <span class="badge">HP {s.hp}</span>
          </div>
          <div class="hint">ATK {s.atk} · HEAL {s.heal} · owner {s.owner}</div>
          <div class="spacer"></div>
          <button class="btn" disabled={disabled || !s.actionAvailable} on:click={() => dispatch('summonAction', { summonId: s.summonId, ownerId: ownerId || s.owner })}>소환 행동</button>
        </div>
      {/each}
    </div>
  {/if}
</section>

<style>
  .stack{display:flex; flex-direction:column; gap:8px}
</style>
