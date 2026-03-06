<script lang="ts">
  import { createEventDispatcher } from 'svelte'

  export type DetailKind = 'card' | 'status'

  export type DetailItem = {
    kind: DetailKind
    name: string
    summary: string
    description: string
    tags?: string[]
    stats?: Array<{ label: string; value: string | number }>
  }

  const dispatch = createEventDispatcher<{ close: void }>()

  export let open = false
  export let item: DetailItem | null = null
</script>

{#if open}
  <section class="drawer">
    <div class="inner">
      <div class="row">
        <div>
          <div class="kind">{item?.kind === 'status' ? '상태' : '카드'} 상세</div>
          <div class="title">{item?.name ?? '선택 없음'}</div>
          {#if item?.summary}<div class="summary">{item.summary}</div>{/if}
        </div>
        <button class="btn" on:click={() => dispatch('close')}>닫기</button>
      </div>

      {#if item}
        {#if item.tags?.length}
          <div class="tags">
            {#each item.tags as tag (tag)}
              <span class="tag">{tag}</span>
            {/each}
          </div>
        {/if}

        <p class="desc">{item.description}</p>

        {#if item.stats?.length}
          <div class="stats">
            {#each item.stats as stat (stat.label)}
              <div class="stat"><span>{stat.label}</span><b>{stat.value}</b></div>
            {/each}
          </div>
        {/if}
      {:else}
        <div class="summary">대상을 선택해 주세요.</div>
      {/if}
    </div>
  </section>
{/if}

<style>
  .drawer{position:fixed;left:16px;right:16px;bottom:16px;z-index:50}
  .inner{padding:14px;border:1px solid var(--line-default);border-radius:16px;background:var(--surface-1)}
  .row{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}
  .kind{font-size:12px;color:var(--text-muted)}
  .title{font-weight:800}
  .summary{font-size:12px;color:var(--text-muted);margin-top:4px}
  .tags{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px}
  .tag{padding:3px 8px;border:1px solid var(--line-default);border-radius:999px;font-size:11px;color:var(--text-muted)}
  .desc{margin:10px 0 0;line-height:1.5}
  .stats{margin-top:10px;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
  .stat{display:flex;justify-content:space-between;padding:8px;border:1px solid var(--line-default);border-radius:10px;background:var(--surface-2);font-size:12px}
</style>
