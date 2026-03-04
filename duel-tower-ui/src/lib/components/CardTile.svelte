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
</script>

<div class="gcard" on:click={() => onClick?.()}>
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
