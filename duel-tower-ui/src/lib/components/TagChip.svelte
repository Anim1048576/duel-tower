<script lang="ts">
  type TagTone = 'accent' | 'muted' | 'success' | 'warning'

  type Props = {
    label: string
    tone?: TagTone
  }

  let { label, tone = 'muted' }: Props = $props()
</script>

<span class={`tag-chip tag-chip--${tone}`}>{label}</span>

<style>
  .tag-chip {
    display: inline-flex;
    align-items: center;
    min-height: 1.75rem;
    padding: 0.2rem 0.6rem;
    border: 1px solid var(--color-border);
    color: var(--color-text-soft);
    font-size: 0.7rem;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    white-space: nowrap;
  }

  .tag-chip--accent {
    border-color: rgba(226, 193, 155, 0.38);
    color: var(--color-accent);
    background: rgba(226, 193, 155, 0.08);
  }

  .tag-chip--muted {
    color: var(--color-text-muted);
    background: rgba(152, 143, 135, 0.08);
  }

  .tag-chip--success {
    border-color: rgba(188, 204, 173, 0.3);
    color: var(--color-success);
    background: rgba(188, 204, 173, 0.08);
  }

  .tag-chip--warning {
    border-color: rgba(199, 167, 125, 0.34);
    color: var(--color-warning);
    background: rgba(199, 167, 125, 0.08);
  }
</style>
