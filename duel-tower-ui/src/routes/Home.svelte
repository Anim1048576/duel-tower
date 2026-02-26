<script lang="ts">
  import { navigate } from '../lib/router'
  import { createSession, joinSession } from '../stores/session'

  let joinCode = ''
</script>

<section class="card">
  <h1>Duel Tower</h1>

  <div class="row">
    <button class="btn primary" on:click={() => { createSession(); navigate('/lobby') }}>
      세션 만들기
    </button>
  </div>

  <form class="row" on:submit|preventDefault={() => { joinSession(joinCode); navigate('/lobby') }}>
    <input class="input mono" bind:value={joinCode} placeholder="세션 코드" />
    <button class="btn" type="submit">참가</button>
  </form>
</section>