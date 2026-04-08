<script lang="ts">
  import TagChip from '../lib/components/TagChip.svelte'
  import { authState } from '../lib/auth/authState.svelte'
  import { pathBuilders } from '../lib/navigation'

  let username = $state('')
  let password = $state('')
  let feedbackVisible = $state(false)
  let localError = $state<string | null>(null)

  const feedbackMessage = $derived.by(() => localError ?? authState.error)
  const shouldShowFeedback = $derived.by(() => feedbackVisible || Boolean(authState.error))
  const isSubmitDisabled = $derived.by(() => authState.loading || !username.trim() || !password)

  function handleInput() {
    localError = null
    feedbackVisible = false
    authState.clearError()
  }

  async function handleSubmit(event: SubmitEvent) {
    event.preventDefault()

    if (authState.loading) return

    const normalizedUsername = username.trim()
    if (!normalizedUsername || !password) {
      localError = 'Enter both username and password to continue.'
      feedbackVisible = true
      return
    }

    localError = null
    feedbackVisible = false

    try {
      await authState.login({
        username: normalizedUsername,
        password,
      })

      history.replaceState({}, '', pathBuilders.hub())
      window.dispatchEvent(new PopStateEvent('popstate'))
    } catch {
      feedbackVisible = true
    }
  }
</script>

<section class="login-page">
  <header class="login-page__header">
    <div class="login-page__tags">
      <TagChip label="MVP Access" tone="accent" />
      <TagChip label="Session Auth" tone="success" />
    </div>

    <div class="login-page__copy">
      <h2>기록 보관소 접속</h2>
      <p>
        이번 배치에서는 공개 진입 화면과 내부 앱 셸을 분리합니다. 실제 인증 연동은 하지 않고,
        구조와 상태 자리만 우선 확보합니다.
      </p>
    </div>
  </header>

  <form class="login-page__form" onsubmit={handleSubmit} aria-busy={authState.loading}>
    <fieldset class="login-page__fieldset" disabled={authState.loading}>
    <label class="login-page__field">
      <span>Username</span>
      <input
        bind:value={username}
        name="username"
        autocomplete="username"
        placeholder="tower-keeper"
        required
        aria-invalid={shouldShowFeedback && Boolean(feedbackMessage)}
        aria-describedby={shouldShowFeedback && feedbackMessage ? 'login-feedback' : undefined}
        disabled={authState.loading}
        oninput={handleInput}
      />
    </label>

    <label class="login-page__field">
      <span>Password</span>
      <input
        bind:value={password}
        name="password"
        type="password"
        autocomplete="current-password"
        placeholder="********"
        required
        aria-invalid={shouldShowFeedback && Boolean(feedbackMessage)}
        aria-describedby={shouldShowFeedback && feedbackMessage ? 'login-feedback' : undefined}
        disabled={authState.loading}
        oninput={handleInput}
      />
    </label>

    <div class="login-page__actions">
      <button type="submit">기록 보관소 열기</button>
      <a class="login-page__preview" data-nav href={pathBuilders.hub()}>허브 미리보기</a>
    </div>
    </fieldset>
  </form>

  {#if shouldShowFeedback && feedbackMessage}
    <p id="login-feedback" class="login-page__feedback" role="alert" aria-live="polite">
      {feedbackMessage}
    </p>
  {/if}

  {#if false}
    <p class="login-page__feedback">
      인증 API는 아직 연결되지 않았습니다. TODO 위치에 세션 부트스트랩 로직을 연결하면
      됩니다.
    </p>
  {/if}
</section>

<style>
  .login-page {
    padding: 1.5rem;
    display: grid;
    gap: 1.5rem;
  }

  .login-page__header,
  .login-page__copy,
  .login-page__form {
    display: grid;
    gap: 1rem;
  }

  .login-page__fieldset {
    padding: 0;
    margin: 0;
    border: 0;
    display: grid;
    gap: 1rem;
    min-width: 0;
  }

  .login-page__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .login-page__copy h2 {
    margin: 0;
    font-family: var(--font-display);
    font-size: clamp(2rem, 3vw, 2.6rem);
    line-height: 0.98;
  }

  .login-page__copy p {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .login-page__field {
    display: grid;
    gap: 0.45rem;
  }

  .login-page__field span {
    color: var(--color-text-muted);
    font-size: 0.78rem;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .login-page__field input {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.7);
    padding: 0.95rem 1rem;
    outline: none;
  }

  .login-page__field input:focus {
    border-color: rgba(255, 179, 175, 0.4);
  }

  .login-page__field input:disabled {
    opacity: 0.72;
  }

  .login-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .login-page__actions button,
  .login-page__preview {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .login-page__actions button {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .login-page__actions button:disabled {
    opacity: 0.76;
  }

  .login-page__preview {
    display: none;
    color: var(--color-text-soft);
    background: rgba(255, 255, 255, 0.02);
  }

  .login-page__feedback {
    margin: 0;
    color: var(--color-warning);
    font-size: 0.92rem;
    line-height: 1.6;
  }
</style>
