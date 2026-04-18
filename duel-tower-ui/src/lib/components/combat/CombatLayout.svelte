<script lang="ts">
  import type { Snippet } from 'svelte'

  type Props = {
    header?: Snippet
    field?: Snippet
    sidebar?: Snippet
    hand?: Snippet
  }

  let { header, field, sidebar, hand }: Props = $props()
</script>

<div class="combat-layout">
  {#if header}
    <div class="combat-layout__header">
      {@render header()}
    </div>
  {/if}

  <div class="combat-layout__stage">
    <div class="combat-layout__main" class:combat-layout__main--with-sidebar={Boolean(sidebar)}>
      <div class="combat-layout__field">
        {@render field?.()}
      </div>

      {#if hand}
        <div class="combat-layout__hand">
          {@render hand()}
        </div>
      {/if}
    </div>

    {#if sidebar}
      <aside class="combat-layout__sidebar" aria-label="Combat context panel">
        {@render sidebar()}
      </aside>
    {/if}
  </div>
</div>

<style>
  .combat-layout {
    --combat-surface: #151311;
    --combat-surface-low: #1d1b19;
    --combat-surface-panel: rgba(33, 31, 29, 0.88);
    --combat-surface-panel-strong: rgba(44, 41, 39, 0.94);
    --combat-surface-card: rgba(29, 27, 25, 0.92);
    --combat-border: rgba(152, 143, 135, 0.24);
    --combat-border-strong: rgba(226, 193, 155, 0.42);
    --combat-primary: #ffb3af;
    --combat-primary-dark: #6b181a;
    --combat-secondary: #e2c19b;
    --combat-tertiary: #bcccad;
    --combat-danger: #ffb4ab;
    --combat-muted: #988f87;
    --combat-text: #e7e1de;
    --combat-text-soft: #cfc5bc;
    --combat-shadow: 0 22px 70px rgba(0, 0, 0, 0.35);
    --combat-focus: 0 0 0 1px rgba(255, 179, 175, 0.42), 0 0 38px rgba(255, 179, 175, 0.12);

    position: relative;
    isolation: isolate;
    overflow: visible;
    --combat-stage-gap: clamp(1rem, 2vw, 1.4rem);
    --combat-context-width: clamp(22rem, 25vw, 28rem);
    min-height: calc(100vh - 4rem);
    padding: clamp(1rem, 1.8vw, 1.5rem);
    color: var(--combat-text);
    background:
      linear-gradient(115deg, rgba(21, 19, 17, 0.94), rgba(21, 19, 17, 0.72) 48%, rgba(16, 14, 12, 0.96)),
      radial-gradient(circle at 32% 42%, rgba(226, 193, 155, 0.12), transparent 28rem),
      radial-gradient(circle at 78% 30%, rgba(255, 179, 175, 0.1), transparent 22rem),
      repeating-linear-gradient(90deg, rgba(231, 225, 222, 0.035) 0 1px, transparent 1px 72px),
      repeating-linear-gradient(0deg, rgba(231, 225, 222, 0.025) 0 1px, transparent 1px 72px),
      var(--combat-surface);
    border: 1px solid rgba(226, 193, 155, 0.16);
    box-shadow: var(--combat-shadow);
  }

  .combat-layout::before,
  .combat-layout::after {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: -1;
  }

  .combat-layout::before {
    background:
      linear-gradient(120deg, transparent 0 48%, rgba(226, 193, 155, 0.08) 49%, transparent 50%),
      linear-gradient(28deg, transparent 0 56%, rgba(188, 204, 173, 0.06) 57%, transparent 58%);
    opacity: 0.8;
    filter: blur(0.2px);
  }

  .combat-layout::after {
    inset: auto 0 0;
    height: 35%;
    background: linear-gradient(0deg, rgba(16, 14, 12, 0.78), transparent);
  }

  .combat-layout,
  .combat-layout__stage,
  .combat-layout__field,
  .combat-layout__sidebar,
  .combat-layout__main,
  .combat-layout__hand {
    display: grid;
    gap: var(--combat-stage-gap);
  }

  .combat-layout__header,
  .combat-layout__stage,
  .combat-layout__main,
  .combat-layout__hand,
  .combat-layout__sidebar {
    position: relative;
    z-index: 1;
  }

  .combat-layout__stage {
    min-height: clamp(36rem, calc(100vh - 16rem), 58rem);
  }

  .combat-layout__main {
    min-width: 0;
    min-height: inherit;
    align-content: start;
    grid-template-rows: minmax(0, 1fr) auto;
  }

  .combat-layout__main--with-sidebar {
    padding-inline-end: calc(var(--combat-context-width) + var(--combat-stage-gap));
  }

  .combat-layout__field {
    min-width: 0;
    min-height: 0;
  }

  .combat-layout__sidebar {
    position: absolute;
    inset: 0 0 0 auto;
    width: var(--combat-context-width);
    min-width: 0;
    min-height: 0;
    align-content: start;
  }

  .combat-layout__hand {
    position: sticky;
    bottom: 0.8rem;
    z-index: 2;
  }

  .combat-layout__sidebar :global(.section-frame) {
    position: sticky;
    top: 0.8rem;
    display: grid;
    grid-template-rows: auto minmax(0, 1fr);
    max-height: calc(100vh - 6.4rem);
    overflow: hidden;
  }

  .combat-layout__sidebar :global(.section-frame__body) {
    display: grid;
    min-height: 0;
  }

  .combat-layout :global(.section-frame) {
    border-color: rgba(226, 193, 155, 0.18);
    background:
      linear-gradient(160deg, rgba(33, 31, 29, 0.9), rgba(16, 14, 12, 0.82)),
      var(--combat-surface-panel);
    box-shadow: var(--combat-shadow);
  }

  .combat-layout :global(.section-frame__eyebrow),
  .combat-layout :global(.section-frame__description) {
    color: var(--combat-text-soft);
  }

  .combat-layout :global(.section-frame__header h3) {
    color: var(--combat-secondary);
    letter-spacing: 0.02em;
  }

  .combat-layout :global(.stat-block) {
    border-color: rgba(226, 193, 155, 0.2);
    background: rgba(16, 14, 12, 0.58);
  }

  .combat-layout :global(.stat-block strong) {
    color: var(--combat-text);
  }

  .combat-layout :global(.stat-block span) {
    color: var(--combat-secondary);
  }

  .combat-layout :global(.stat-block p) {
    color: var(--combat-text-soft);
  }

  .combat-layout :global(.content-state-panel) {
    border-color: rgba(152, 143, 135, 0.24);
    background: rgba(16, 14, 12, 0.54);
  }

  .combat-layout :global(.content-state-panel--error) {
    border-color: rgba(255, 180, 171, 0.48);
    background: rgba(107, 24, 26, 0.22);
  }

  .combat-layout :global(.content-state-panel__copy p),
  .combat-layout :global(.content-state-panel__detail p) {
    color: var(--combat-text-soft);
  }

  .combat-layout :global(.content-state-panel__action) {
    border-color: rgba(226, 193, 155, 0.42);
    background: rgba(226, 193, 155, 0.1);
    color: var(--combat-text);
  }

  .combat-layout :global(.tag-chip) {
    min-height: 1.55rem;
    background: rgba(16, 14, 12, 0.48);
  }

  @media (max-width: 1200px) {
    .combat-layout__stage,
    .combat-layout__main {
      min-height: auto;
    }

    .combat-layout__main--with-sidebar {
      padding-inline-end: 0;
    }

    .combat-layout__sidebar,
    .combat-layout__hand {
      position: static;
    }

    .combat-layout__sidebar {
      inset: auto;
      width: 100%;
    }

    .combat-layout__sidebar :global(.section-frame) {
      position: static;
      max-height: none;
    }
  }

  @media (max-width: 720px) {
    .combat-layout {
      min-height: auto;
      padding: 0.75rem;
    }
  }
</style>
