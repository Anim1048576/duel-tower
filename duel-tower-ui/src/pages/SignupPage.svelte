<script lang="ts">
  import TagChip from '../lib/components/TagChip.svelte'
  import { authState } from '../lib/auth/authState.svelte'
  import { pathBuilders } from '../lib/navigation'

  let username = $state('')
  let password = $state('')
  let passwordConfirm = $state('')
  let feedbackVisible = $state(false)
  let localError = $state<string | null>(null)

  const feedbackMessage = $derived.by(() => localError ?? authState.error)
  const shouldShowFeedback = $derived.by(() => feedbackVisible || Boolean(authState.error))
  const isSubmitDisabled = $derived.by(() => {
    return authState.loading || !username.trim() || !password || !passwordConfirm
  })

  function resetFeedback() {
    localError = null
    feedbackVisible = false
    authState.clearError()
  }

  function handleInput() {
    resetFeedback()
  }

  async function handleSubmit(event: SubmitEvent) {
    event.preventDefault()

    if (authState.loading) return

    const normalizedUsername = username.trim()
    if (!normalizedUsername || !password || !passwordConfirm) {
      localError = '아이디와 비밀번호를 모두 입력해 주세요.'
      feedbackVisible = true
      return
    }

    if (password !== passwordConfirm) {
      localError = '비밀번호 확인이 일치하지 않습니다.'
      feedbackVisible = true
      return
    }

    resetFeedback()

    try {
      await authState.signup({
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

<section class="signup-page">
  <header class="signup-page__header">
    <div class="signup-page__tags">
      <TagChip label="회원가입" tone="accent" />
      <TagChip label="즉시 입장" tone="success" />
    </div>

    <div class="signup-page__copy">
      <h2>새 계정 생성</h2>
      <p>
        새 계정을 만들면 바로 로그인 세션을 열고 허브로 이동합니다. 로그인 화면으로
        되돌아가 기존 계정으로 입장할 수도 있습니다.
      </p>
    </div>
  </header>

  <form class="signup-page__form" onsubmit={handleSubmit} aria-busy={authState.loading}>
    <fieldset class="signup-page__fieldset" disabled={authState.loading}>
      <label class="signup-page__field">
        <span>Username</span>
        <input
          bind:value={username}
          name="username"
          autocomplete="username"
          placeholder="new-keeper"
          required
          aria-invalid={shouldShowFeedback && Boolean(feedbackMessage)}
          aria-describedby={shouldShowFeedback && feedbackMessage ? 'signup-feedback' : undefined}
          disabled={authState.loading}
          oninput={handleInput}
        />
      </label>

      <label class="signup-page__field">
        <span>Password</span>
        <input
          bind:value={password}
          name="password"
          type="password"
          autocomplete="new-password"
          placeholder="********"
          required
          aria-invalid={shouldShowFeedback && Boolean(feedbackMessage)}
          aria-describedby={shouldShowFeedback && feedbackMessage ? 'signup-feedback' : undefined}
          disabled={authState.loading}
          oninput={handleInput}
        />
      </label>

      <label class="signup-page__field">
        <span>Confirm password</span>
        <input
          bind:value={passwordConfirm}
          name="passwordConfirm"
          type="password"
          autocomplete="new-password"
          placeholder="********"
          required
          aria-invalid={shouldShowFeedback && Boolean(feedbackMessage)}
          aria-describedby={shouldShowFeedback && feedbackMessage ? 'signup-feedback' : undefined}
          disabled={authState.loading}
          oninput={handleInput}
        />
      </label>

      <div class="signup-page__actions">
        <button type="submit" disabled={isSubmitDisabled}>회원가입</button>
        <a class="signup-page__link-button" data-nav href={pathBuilders.login()}>로그인으로 돌아가기</a>
      </div>
    </fieldset>
  </form>

  {#if shouldShowFeedback && feedbackMessage}
    <p id="signup-feedback" class="signup-page__feedback" role="alert" aria-live="polite">
      {feedbackMessage}
    </p>
  {/if}
</section>

<style>
  .signup-page {
    padding: 1.5rem;
    display: grid;
    gap: 1.5rem;
  }

  .signup-page__header,
  .signup-page__copy,
  .signup-page__form {
    display: grid;
    gap: 1rem;
  }

  .signup-page__fieldset {
    padding: 0;
    margin: 0;
    border: 0;
    display: grid;
    gap: 1rem;
    min-width: 0;
  }

  .signup-page__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .signup-page__copy h2 {
    margin: 0;
    font-family: var(--font-display);
    font-size: clamp(2rem, 3vw, 2.6rem);
    line-height: 0.98;
  }

  .signup-page__copy p {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .signup-page__field {
    display: grid;
    gap: 0.45rem;
  }

  .signup-page__field span {
    color: var(--color-text-muted);
    font-size: 0.78rem;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .signup-page__field input {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.7);
    padding: 0.95rem 1rem;
    outline: none;
  }

  .signup-page__field input:focus {
    border-color: rgba(255, 179, 175, 0.4);
  }

  .signup-page__field input:disabled {
    opacity: 0.72;
  }

  .signup-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .signup-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .signup-page__link-button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: var(--color-text-soft);
    text-decoration: none;
    background: rgba(255, 255, 255, 0.03);
  }

  .signup-page__link-button:hover {
    border-color: rgba(255, 179, 175, 0.4);
    color: var(--color-text);
  }

  .signup-page__actions button:disabled {
    opacity: 0.76;
  }

  .signup-page__feedback {
    margin: 0;
    color: var(--color-warning);
    font-size: 0.92rem;
    line-height: 1.6;
  }
</style>
