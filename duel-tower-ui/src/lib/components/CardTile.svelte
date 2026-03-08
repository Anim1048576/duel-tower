<script lang="ts">
  export type CardLite = {
    id: string
    name: string
    cost: number
    token?: boolean
    keywords?: string[]
  }

  export let card: CardLite
  export let onClick: (() => void) | undefined

  $: keywords = card?.keywords ?? []
  $: clickable = Boolean(onClick)

  function handleKeydown(event: KeyboardEvent) {
    if (!clickable) return
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onClick?.()
    }
  }
</script>

<div
  class="gcard"
  class:clickable
  role={clickable ? 'button' : undefined}
  tabindex={clickable ? 0 : undefined}
  on:click={() => onClick?.()}
  on:keydown={handleKeydown}
>
  <div class="row" style="justify-content:space-between; align-items:flex-start">
    <div class="gcardTitle">{card.name}</div>
    <span class="badge">{card.cost}</span>
  </div>
  <div class="gcardSub mono">{card.id}</div>
  <div class="gcardTags">
    {#if card.token}<span class="tag d">TOKEN</span>{/if}
    {#each keywords as k (k)}
      <span class="tag p">{k}</span>
    {/each}
  </div>
</div>


<style>
  .clickable { cursor: pointer; }
</style>
