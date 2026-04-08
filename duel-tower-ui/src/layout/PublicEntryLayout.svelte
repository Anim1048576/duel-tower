<script lang="ts">
  import type { Snippet } from 'svelte'

  type Props = {
    children?: Snippet
  }

  let { children }: Props = $props()
</script>

<div class="public-shell">
  <div class="public-entry__wash" aria-hidden="true"></div>
  <div class="public-entry__grain" aria-hidden="true"></div>

  <main class="public-shell__stage public-entry">
    <section class="public-entry__brand">
      <p class="public-entry__eyebrow">Living Ledger</p>
      <h1>Duel Tower</h1>
      <p class="public-entry__copy">
        검게 그을린 기록 보관소에서 원정대와 전술을 정리하고, 세션 준비와 전투 지휘로
        이어지는 전략 UI의 첫 진입점을 구성합니다.
      </p>
    </section>

    <section class="public-entry__panel">
      {@render children?.()}
    </section>
  </main>
</div>

<style>
  .public-entry__wash,
  .public-entry__grain {
    position: absolute;
    inset: 0;
  }

  .public-entry__wash {
    background:
      radial-gradient(circle at 14% 18%, rgba(255, 179, 175, 0.14), transparent 24%),
      radial-gradient(circle at 82% 20%, rgba(226, 193, 155, 0.14), transparent 22%),
      linear-gradient(120deg, rgba(0, 0, 0, 0.18), transparent 38%);
    z-index: 0;
  }

  .public-entry__grain {
    background-image:
      linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.015) 1px, transparent 1px);
    background-size: 3px 3px, 3px 3px;
    opacity: 0.25;
    mix-blend-mode: soft-light;
    z-index: 0;
  }

  .public-entry {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(22rem, 30rem);
    align-items: center;
    gap: 3rem;
  }

  .public-entry__brand {
    max-width: 38rem;
    display: grid;
    gap: 1rem;
  }

  .public-entry__eyebrow {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.75rem;
    letter-spacing: 0.22em;
    text-transform: uppercase;
  }

  .public-entry__brand h1 {
    margin: 0;
    font-family: var(--font-display);
    font-size: clamp(3rem, 5vw, 5rem);
    line-height: 0.92;
  }

  .public-entry__copy {
    margin: 0;
    max-width: 32rem;
    color: var(--color-text-soft);
    font-size: 1rem;
    line-height: 1.8;
  }

  .public-entry__panel {
    border: 1px solid var(--color-border-strong);
    background: rgba(27, 24, 22, 0.84);
    box-shadow: 0 24px 48px var(--color-shadow);
    backdrop-filter: blur(12px);
  }

  @media (max-width: 960px) {
    .public-entry {
      grid-template-columns: 1fr;
      align-items: start;
      gap: 1.75rem;
      padding-top: 3rem;
      padding-bottom: 3rem;
    }

    .public-entry__panel {
      width: min(100%, 32rem);
    }
  }
</style>
