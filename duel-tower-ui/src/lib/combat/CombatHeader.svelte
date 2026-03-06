<script lang="ts">
  import { createEventDispatcher } from 'svelte'

  const dispatch = createEventDispatcher<{ toggleLogs: void }>()

  export let round = 0
  export let turnPlayer = '—'
  export let initiative = '—'
  export let showLogs = false
</script>

<header class="header panel">
  <div class="item">
    <div class="k">라운드</div>
    <div class="v">{round}</div>
  </div>
  <div class="item">
    <div class="k">턴 주체</div>
    <div class="v mono">{turnPlayer}</div>
  </div>
  <div class="item grow">
    <div class="k">이니셔티브</div>
    <div class="v mono">{initiative}</div>
  </div>
  <button class="btn" on:click={() => dispatch('toggleLogs')}>{showLogs ? '로그 닫기' : '로그 열기'}</button>
</header>

<style>
  .header{display:flex; gap:10px; align-items:center; flex-wrap:wrap}
  .item{padding:8px 10px; border:1px solid var(--line); border-radius:12px; background: rgba(0,0,0,.16)}
  .k{font-size:11px; color:var(--muted)}
  .v{font-weight:800; margin-top:4px}
  .grow{flex:1; min-width:200px}
</style>
