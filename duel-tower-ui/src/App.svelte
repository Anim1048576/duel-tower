<script lang="ts">
  import { Router, Route, Link } from 'svelte-navigator'
  import Home from './routes/Home.svelte'
  import Lobby from './routes/Lobby.svelte'
  import Presets from './routes/Presets.svelte'
  import Node from './routes/Node.svelte'
  import Combat from './routes/Combat.svelte'

  import { session } from './stores/session'

  const basepath = import.meta.env.PROD ? '/ui' : '/'
</script>

<Router {basepath}>
  <header class="topbar">
    <div class="brand">
      <div class="logo">DT</div>
      <div class="brandText">
        <div class="brandTitle">Duel Tower</div>
        <div class="brandSub">노드 탐색형 협동 턴제 카드 전투</div>
      </div>
    </div>

    <nav class="nav">
      <Link class="navItem" to="/">홈</Link>
      <Link class="navItem" to="/lobby">로비</Link>
      <Link class="navItem" to="/presets">프리셋</Link>
      <Link class="navItem" to="/node">노드</Link>
      <Link class="navItem" to="/combat">전투</Link>
    </nav>

    <div class="topRight">
      <div class="pill">세션: <span class="mono">{$session.code ?? '—'}</span></div>
      <div class="pill">상태: <span>{$session.phase ?? '—'}</span></div>
    </div>
  </header>

  <main class="page">
    <Route path="/" component={Home} />
    <Route path="/lobby" component={Lobby} />
    <Route path="/presets" component={Presets} />
    <Route path="/node" component={Node} />
    <Route path="/combat" component={Combat} />
  </main>

  <footer class="footer">
    <div class="muted">UI 프로토타입 · 서버 권위 룰 엔진 붙이기 전 단계</div>
    <div class="muted mono">/ui</div>
  </footer>
</Router>