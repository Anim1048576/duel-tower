<script lang="ts">
  type Props = {
    value: string | number
    label: string
    note?: string
  }

  let { value, label, note }: Props = $props()
</script>

<div class="stat-block">
  <strong>{value}</strong>
  <span>{label}</span>

  {#if note}
    <p>{note}</p>
  {/if}
</div>

<style>
  .stat-block {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    padding: 1rem;
    display: grid;
    gap: 0.25rem;
  }

  .stat-block strong {
    font-family: var(--font-display);
    font-size: clamp(1.7rem, 2.8vw, 2.2rem);
    line-height: 1;
  }

  .stat-block span {
    color: var(--color-text-soft);
    font-size: 0.88rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .stat-block p {
    margin: 0.2rem 0 0;
    color: var(--color-text-muted);
    font-size: 0.85rem;
    line-height: 1.55;
  }
</style>
