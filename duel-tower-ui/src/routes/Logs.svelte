<script lang="ts">
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { toasts } from '../stores/log'
</script>

<PageSkeleton title="Logs" summary="로그 페이지 스켈레톤">
  <div class="cardTitle">최근 토스트 로그 ({$toasts.length})</div>
  <div class="spacer"></div>
  {#if !$toasts.length}
    <div class="hint">표시할 로그가 아직 없음.</div>
  {:else}
    <div class="table">
      {#each $toasts as t (t.id)}
        <div class="tr">
          <div class="grow">
            <b>{t.title}</b>
            <div class="hint">{t.message || '—'}</div>
          </div>
          <div class="mono">{new Date(t.at).toLocaleTimeString()}</div>
        </div>
      {/each}
    </div>
  {/if}
</PageSkeleton>
