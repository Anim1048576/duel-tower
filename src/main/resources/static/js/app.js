/* Duel Tower UI prototype (localStorage demo)
 * - 목적: 동선/화면 구성 먼저 고정
 * - 서버 붙일 때: localStorage 부분을 API/WebSocket으로 교체
 */

(function () {
  const KEY = {
    session: 'dt.session.v1',
    presets: 'dt.presets.v1',
    selectedPreset: 'dt.preset.selected.v1',
    run: 'dt.run.v1',
    combat: 'dt.combat.v1',
    log: 'dt.log.v1'
  };

  const $ = (q, el = document) => el.querySelector(q);
  const $$ = (q, el = document) => Array.from(el.querySelectorAll(q));

  const load = (k, fallback) => {
    try {
      const raw = localStorage.getItem(k);
      return raw ? JSON.parse(raw) : fallback;
    } catch {
      return fallback;
    }
  };

  const save = (k, v) => localStorage.setItem(k, JSON.stringify(v));

  const now = () => new Date().toISOString();

  const rand = (n) => Math.floor(Math.random() * n);
  const code = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let out = '';
    for (let i = 0; i < 6; i++) out += chars[rand(chars.length)];
    return out;
  };

  const toast = (title, message) => {
    const host = $('#toast');
    if (!host) return;
    const el = document.createElement('div');
    el.className = 'toastItem';
    el.innerHTML = `<div class="t">${escapeHtml(title)}</div><div class="m">${escapeHtml(message)}</div>`;
    host.appendChild(el);
    setTimeout(() => {
      el.style.opacity = '0';
      el.style.transform = 'translateY(4px)';
      el.style.transition = 'all .2s ease';
      setTimeout(() => el.remove(), 220);
    }, 1800);
  };

  const escapeHtml = (s) => String(s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');

  const getPath = (obj, path) => {
    if (!obj) return undefined;
    return path.split('.').reduce((acc, key) => (acc == null ? acc : acc[key]), obj);
  };

  const bind = () => {
    const session = load(KEY.session, null);
    const run = load(KEY.run, null);
    const combat = load(KEY.combat, null);
    const selectedPresetName = (() => {
      const presets = load(KEY.presets, []);
      const sel = load(KEY.selectedPreset, null);
      const found = presets.find(p => p.id === sel);
      return found ? found.name : '—';
    })();

    const ctx = {
      session: session ? {
        code: session.code,
        phase: session.phase,
        gm: session.gm,
        playerCount: session.players?.length ?? 0,
      } : { code: '—', phase: '—', gm: '—', playerCount: 0 },
      preset: { selectedName: selectedPresetName },
      run: run ? {
        current: run.current,
        floor: run.floor,
        visitedCount: run.visited?.length ?? 0,
      } : { current: '—', floor: '—', visitedCount: 0 },
      combat: combat ? {
        round: combat.round
      } : { round: '—' },
      me: combat?.me ? {
        hp: combat.me.hp,
        ap: combat.me.ap,
        deck: combat.me.deck.length,
        grave: combat.me.grave.length,
        exiled: combat.me.exiled.length,
        exCd: combat.me.exCooldown
      } : { hp: '—', ap: '—', deck: '—', grave: '—', exiled: '—', exCd: '—' }
    };

    $$('[data-bind]').forEach(el => {
      const p = el.getAttribute('data-bind');
      const v = getPath(ctx, p);
      el.textContent = (v === undefined || v === null) ? '—' : String(v);
    });

    // top pills
    const pillPhase = $('#pillPhase');
    if (pillPhase && session?.phase) {
      pillPhase.style.borderColor = session.phase === 'combat'
        ? 'rgba(109,255,177,.35)'
        : session.phase === 'node'
          ? 'rgba(93,214,255,.35)'
          : 'var(--line)';
    }
  };

  const ensureSeed = () => {
    // presets
    const presets = load(KEY.presets, null);
    if (!Array.isArray(presets) || presets.length === 0) {
      const seed = [
        {
          id: cryptoId(),
          name: '린(서포터)',
          deckText: 'Strike, Strike, Guard, Guard, Tactical Search, Charge, Recovery, Strike, Guard, Install Turret, Strike, Guard',
          ex: 'Overclock'
        },
        {
          id: cryptoId(),
          name: '카이(딜러)',
          deckText: 'Strike, Strike, Strike, Charge, Charge, Tactical Search, Strike, Guard, Guard, Strike, Strike, Recovery',
          ex: 'Execute'
        },
        {
          id: cryptoId(),
          name: '더미 몹',
          deckText: 'Bash, Bash, Guard, Guard, Strike, Strike, Strike, Weakness, Stun, Strike, Guard, Strike',
          ex: 'Rage'
        }
      ];
      save(KEY.presets, seed);
      save(KEY.selectedPreset, seed[0].id);
    }

    // session
    const session = load(KEY.session, null);
    if (!session) {
      save(KEY.session, {
        code: '—',
        phase: 'lobby',
        gm: 'GM',
        players: [{ id: 'me', name: 'Me', ready: false }],
        createdAt: now()
      });
    }

    // run
    const run = load(KEY.run, null);
    if (!run) {
      save(KEY.run, defaultRun());
    }

    // log
    const log = load(KEY.log, null);
    if (!Array.isArray(log)) save(KEY.log, []);

    // combat
    const combat = load(KEY.combat, null);
    if (!combat) {
      save(KEY.combat, defaultCombat());
    }

    bind();
  };

  const cryptoId = () => {
    // avoid depending on crypto.randomUUID in older setups
    const s4 = () => Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
    return `${s4()}${s4()}-${s4()}-${s4()}-${s4()}-${s4()}${s4()}${s4()}`;
  };

  const defaultRun = () => ({
    floor: 1,
    current: '입구',
    visited: [],
    lockedChoice: null,
    choices: [
      { id: 'c1', type: '전투', title: '정찰대와 조우', desc: '일반 전투. 전투 화면으로 이동.' },
      { id: 'c2', type: '시설', title: '휴식 구역', desc: '회복/강화 이벤트(데모).' },
      { id: 'c3', type: '???', title: '수상한 문', desc: '무슨 일이 생길지 모른다.' }
    ]
  });

  const cardCatalog = {
    'Strike': { cost: 1, tags: ['공격'], desc: '단일 대상에게 피해.' },
    'Guard': { cost: 1, tags: ['수비'], desc: '보호/방벽(진영) 부여.' },
    'Recovery': { cost: 1, tags: ['회복'], desc: 'HP 회복 + 재생.' },
    'Charge': { cost: 1, tags: ['증강 1'], desc: '다음 공격 증폭.' },
    'Tactical Search': { cost: 1, tags: ['서치'], desc: '덱에서 조건 카드 1장 서치 후 셔플.' },
    'Install Turret': { cost: 2, tags: ['설치'], desc: '필드에 설치(최대 5).' },
    'Bash': { cost: 2, tags: ['공격', '취약'], desc: '피해 + 취약 부여.' },
    'Weakness': { cost: 1, tags: ['쇠약'], desc: '쇠약 부여.' },
    'Stun': { cost: 2, tags: ['기절'], desc: '기절 부여.' },
  };

  const normalizeCard = (name) => {
    const n = String(name || '').trim();
    if (!n) return null;
    const c = cardCatalog[n] || { cost: 1, tags: ['기타'], desc: '효과 미정(데모).' };
    return {
      id: cryptoId(),
      name: n,
      cost: c.cost,
      tags: c.tags,
      desc: c.desc,
    };
  };

  const defaultCombat = () => ({
    round: 0,
    currentTurn: 0,
    turnOrder: [
      { id: 'me', name: 'Me', team: 'ally' },
      { id: 'p2', name: 'P2', team: 'ally' },
      { id: 'e1', name: 'Goblin', team: 'enemy' },
    ],
    selectedCardId: null,
    usedHandSwapThisTurn: false,
    me: {
      hp: 30,
      ap: 3,
      exCooldown: 0,
      deck: [],
      hand: [],
      grave: [],
      field: [],
      exiled: [],
    },
    allies: [
      { id: 'me', name: 'Me', hp: 30, statuses: [] },
      { id: 'p2', name: 'P2', hp: 28, statuses: ['방벽'] },
    ],
    enemies: [
      { id: 'e1', name: 'Goblin', hp: 20, statuses: [] },
      { id: 'e2', name: 'Boar', hp: 26, statuses: ['도발'] },
    ],
    globalEffects: ['방벽(진영)']
  });

  const pushLog = (text) => {
    const log = load(KEY.log, []);
    log.unshift({ at: now(), text });
    save(KEY.log, log.slice(0, 80));
  };

  const openModal = (id) => {
    const m = $(id);
    if (!m) return;
    m.classList.add('isOpen');
    m.setAttribute('aria-hidden', 'false');
  };

  const closeModal = (id) => {
    const m = $(id);
    if (!m) return;
    m.classList.remove('isOpen');
    m.setAttribute('aria-hidden', 'true');
  };

  const initHome = () => {
    const btnCreate = $('#btnCreateSession');
    const joinForm = $('#joinForm');
    const joinCode = $('#joinCode');
    const out = $('#createdCode');

    btnCreate?.addEventListener('click', () => {
      const s = load(KEY.session, null) || {};
      const newCode = code();
      const session = {
        code: newCode,
        phase: 'lobby',
        gm: 'GM',
        players: [{ id: 'me', name: 'Me', ready: false }],
        createdAt: now()
      };
      save(KEY.session, session);
      out.textContent = `생성됨: ${newCode}`;
      toast('세션 생성', `코드 ${newCode}`);
      bind();
      setTimeout(() => (location.href = '/ui/lobby'), 120);
    });

    joinForm?.addEventListener('submit', (e) => {
      e.preventDefault();
      const c = (joinCode?.value || '').trim().toUpperCase() || code();
      const session = load(KEY.session, null) || { gm: 'GM', players: [] };
      session.code = c;
      session.phase = 'lobby';
      session.players = session.players?.length ? session.players : [{ id: 'me', name: 'Me', ready: false }];
      save(KEY.session, session);
      toast('세션 참가', `코드 ${c}`);
      bind();
      location.href = '/ui/lobby';
    });
  };

  const initLobby = () => {
    const table = $('#playersTable');
    const btnAdd = $('#btnAddDummy');
    const btnToggle = $('#btnToggleMe');
    const btnStart = $('#btnStart');

    const render = () => {
      const session = load(KEY.session, null);
      if (!session) return;
      const players = session.players || [];

      if (table) {
        table.innerHTML = '';
        const header = document.createElement('div');
        header.className = 'tr th';
        header.innerHTML = `<div style="width:160px">이름</div><div style="width:90px">상태</div><div class="grow">설명</div>`;
        table.appendChild(header);

        players.forEach(p => {
          const row = document.createElement('div');
          row.className = 'tr';
          row.innerHTML = `
            <div style="width:160px"><b>${escapeHtml(p.name)}</b></div>
            <div style="width:90px"><span class="badge ${p.ready ? 'ok' : 'no'}">${p.ready ? 'READY' : 'WAIT'}</span></div>
            <div class="grow muted">${p.id === 'me' ? '당신' : '참가자'}</div>
          `;
          table.appendChild(row);
        });
      }

      const allReady = players.length > 0 && players.every(p => !!p.ready);
      if (btnStart) btnStart.disabled = !allReady;

      bind();
    };

    btnAdd?.addEventListener('click', () => {
      const session = load(KEY.session, null);
      if (!session) return;
      const n = (session.players?.length || 1) + 1;
      session.players = session.players || [];
      session.players.push({ id: `p${n}`, name: `P${n}`, ready: false });
      save(KEY.session, session);
      toast('더미 추가', `P${n} 참가`);
      render();
    });

    btnToggle?.addEventListener('click', () => {
      const session = load(KEY.session, null);
      if (!session) return;
      const me = (session.players || []).find(p => p.id === 'me');
      if (!me) return;
      me.ready = !me.ready;
      save(KEY.session, session);
      toast('READY 변경', me.ready ? 'READY' : 'WAIT');
      render();
    });

    btnStart?.addEventListener('click', () => {
      const session = load(KEY.session, null);
      if (!session) return;
      session.phase = 'node';
      save(KEY.session, session);
      toast('세션 시작', '노드 화면으로 이동');
      bind();
      location.href = '/ui/node';
    });

    render();
  };

  const initPresets = () => {
    const grid = $('#presetGrid');
    const btnNew = $('#btnNewPreset');

    const modal = $('#presetModal');
    const closeBtn = $('#presetModalClose');
    const cancelBtn = $('#btnCancelPreset');
    const saveBtn = $('#btnSavePreset');
    const deleteBtn = $('#btnDeletePreset');

    const inName = $('#presetName');
    const inDeck = $('#presetDeck');
    const inEx = $('#presetEx');

    let editingId = null;

    const render = () => {
      const presets = load(KEY.presets, []);
      const selected = load(KEY.selectedPreset, null);

      if (grid) {
        grid.innerHTML = '';
        presets.forEach(p => {
          const card = document.createElement('div');
          card.className = 'card';
          const deckCount = p.deckText.split(',').map(s => s.trim()).filter(Boolean).length;
          card.innerHTML = `
            <div class="row">
              <div>
                <div class="cardTitle">${escapeHtml(p.name)}</div>
                <div class="muted">덱 ${deckCount}/12 · EX ${escapeHtml(p.ex || '—')}</div>
              </div>
              <div class="grow"></div>
              ${p.id === selected ? '<span class="badge ok">선택됨</span>' : '<span class="badge"> </span>'}
            </div>
            <div class="spacer"></div>
            <div class="muted" style="font-size:12px; line-height:1.5">${escapeHtml(p.deckText)}</div>
            <div class="spacer"></div>
            <div class="row wrap">
              <button class="btn" data-act="select" data-id="${p.id}">선택</button>
              <button class="btn" data-act="edit" data-id="${p.id}">편집</button>
              <button class="btn" data-act="copy" data-id="${p.id}">복사</button>
            </div>
          `;
          grid.appendChild(card);
        });
      }

      bind();

      // wire actions
      $$('button[data-act]').forEach(b => {
        b.onclick = () => {
          const id = b.getAttribute('data-id');
          const act = b.getAttribute('data-act');
          if (!id || !act) return;
          if (act === 'select') {
            save(KEY.selectedPreset, id);
            toast('프리셋 선택', presets.find(x => x.id === id)?.name || '');
            render();
          }
          if (act === 'copy') {
            const src = presets.find(x => x.id === id);
            if (!src) return;
            const c = { ...src, id: cryptoId(), name: `${src.name} (복사)` };
            presets.unshift(c);
            save(KEY.presets, presets);
            toast('복사 완료', c.name);
            render();
          }
          if (act === 'edit') {
            openEditor(id);
          }
        };
      });
    };

    const openEditor = (id) => {
      const presets = load(KEY.presets, []);
      const p = presets.find(x => x.id === id);
      if (!p) return;
      editingId = id;
      inName.value = p.name;
      inDeck.value = p.deckText;
      inEx.value = p.ex || '';
      deleteBtn.style.display = 'inline-flex';
      openModal('#presetModal');
    };

    const openNew = () => {
      editingId = null;
      inName.value = '';
      inDeck.value = '';
      inEx.value = '';
      deleteBtn.style.display = 'none';
      openModal('#presetModal');
    };

    const close = () => {
      closeModal('#presetModal');
      editingId = null;
    };

    const validatePreset = (name, deckText, ex) => {
      const deck = deckText.split(',').map(s => s.trim()).filter(Boolean);
      if (!name.trim()) return '이름이 필요함';
      if (deck.length !== 12) return '덱은 12장이어야 함';
      if (!String(ex || '').trim()) return 'EX 1장이 필요함';
      return null;
    };

    btnNew?.addEventListener('click', openNew);
    closeBtn?.addEventListener('click', close);
    cancelBtn?.addEventListener('click', close);

    modal?.addEventListener('click', (e) => {
      if (e.target === modal) close();
    });

    saveBtn?.addEventListener('click', () => {
      const presets = load(KEY.presets, []);
      const name = inName.value;
      const deckText = inDeck.value;
      const ex = inEx.value;

      const err = validatePreset(name, deckText, ex);
      if (err) {
        toast('저장 실패', err);
        return;
      }

      if (editingId) {
        const p = presets.find(x => x.id === editingId);
        if (!p) return;
        p.name = name;
        p.deckText = deckText;
        p.ex = ex;
        toast('저장', p.name);
      } else {
        const p = { id: cryptoId(), name, deckText, ex };
        presets.unshift(p);
        toast('생성', p.name);
      }

      save(KEY.presets, presets);
      close();
      render();
    });

    deleteBtn?.addEventListener('click', () => {
      const presets = load(KEY.presets, []);
      if (!editingId) return;
      const idx = presets.findIndex(x => x.id === editingId);
      if (idx < 0) return;
      const removed = presets.splice(idx, 1)[0];
      save(KEY.presets, presets);
      const selected = load(KEY.selectedPreset, null);
      if (selected === removed.id) {
        save(KEY.selectedPreset, presets[0]?.id || null);
      }
      toast('삭제', removed.name);
      close();
      render();
    });

    render();
  };

  const initNode = () => {
    const choiceList = $('#choiceList');
    const visitedList = $('#visitedList');

    const modal = $('#choiceModal');
    const closeBtn = $('#choiceModalClose');
    const cancelBtn = $('#btnChoiceCancel');
    const confirmBtn = $('#btnChoiceConfirm');
    const text = $('#choiceModalText');

    const btnReset = $('#btnResetRun');

    let pending = null;

    const render = () => {
      const run = load(KEY.run, defaultRun());

      if (visitedList) {
        visitedList.innerHTML = '';
        const all = [...run.visited].reverse();
        if (all.length === 0) {
          visitedList.innerHTML = '<div class="muted">아직 이력이 없음</div>';
        } else {
          all.forEach(v => {
            const el = document.createElement('div');
            el.className = 'ti';
            el.innerHTML = `<div class="mono muted">${escapeHtml(v.at)}</div><div style="margin-top:6px"><b>${escapeHtml(v.title)}</b> <span class="muted">(${escapeHtml(v.type)})</span></div>`;
            visitedList.appendChild(el);
          });
        }
      }

      if (choiceList) {
        choiceList.innerHTML = '';
        (run.choices || []).forEach(c => {
          const el = document.createElement('div');
          el.className = 'choice';
          el.innerHTML = `<div class="row"><div class="choiceTitle">${escapeHtml(c.title)}</div><div class="grow"></div><span class="badge">${escapeHtml(c.type)}</span></div><div class="choiceDesc">${escapeHtml(c.desc)}</div>`;
          el.onclick = () => {
            pending = c;
            text.textContent = `${c.title} (${c.type})`;
            openModal('#choiceModal');
          };
          choiceList.appendChild(el);
        });
      }

      bind();
    };

    const close = () => {
      pending = null;
      closeModal('#choiceModal');
    };

    closeBtn?.addEventListener('click', close);
    cancelBtn?.addEventListener('click', close);
    modal?.addEventListener('click', (e) => { if (e.target === modal) close(); });

    confirmBtn?.addEventListener('click', () => {
      if (!pending) return;
      const run = load(KEY.run, defaultRun());

      run.visited = run.visited || [];
      run.visited.push({ at: new Date().toLocaleString(), title: pending.title, type: pending.type });
      run.current = pending.title;

      // simple progression
      run.floor += 1;
      run.choices = [
        { id: cryptoId(), type: '전투', title: '다음 전투', desc: '전투로 이동(데모).' },
        { id: cryptoId(), type: '저주', title: '상처난 바닥', desc: '리스크 있는 선택(데모).' },
        { id: cryptoId(), type: '시설', title: '정비소', desc: '프리셋/덱 수정 느낌(데모).' }
      ];

      save(KEY.run, run);
      pushLog(`노드 선택: ${pending.title} (${pending.type})`);
      toast('선택 확정', pending.title);
      close();
      render();

      if (pending.type === '전투') {
        const session = load(KEY.session, null);
        if (session) {
          session.phase = 'combat';
          save(KEY.session, session);
        }
        setTimeout(() => (location.href = '/ui/combat'), 160);
      }
    });

    btnReset?.addEventListener('click', () => {
      save(KEY.run, defaultRun());
      toast('초기화', '런이 초기화됨');
      render();
    });

    render();
  };

  const initCombat = () => {
    const turnList = $('#turnList');
    const enemyRow = $('#enemyRow');
    const allyRow = $('#allyRow');
    const handRow = $('#handRow');
    const fieldRow = $('#fieldRow');
    const logPanel = $('#logPanel');
    const globalEffects = $('#globalEffects');

    const btnStart = $('#btnStartCombat');
    const btnEndTurn = $('#btnEndTurn');
    const btnHandSwap = $('#btnHandSwap');
    const btnUseEx = $('#btnUseEx');
    const btnToGrave = $('#btnToGrave');
    const btnClearLog = $('#btnClearLog');

    // search modal
    const searchModal = $('#searchModal');
    const searchClose = $('#searchModalClose');
    const searchCancel = $('#btnSearchCancel');
    const searchPick = $('#btnSearchPick');
    const searchList = $('#searchList');
    const searchSubtitle = $('#searchSubtitle');

    let searchSelectedId = null;

    const render = () => {
      const combat = load(KEY.combat, defaultCombat());
      const log = load(KEY.log, []);

      // turn list
      if (turnList) {
        turnList.innerHTML = '';
        combat.turnOrder.forEach((t, i) => {
          const el = document.createElement('div');
          el.className = 'turnItem' + (i === combat.currentTurn ? ' isActive' : '');
          el.innerHTML = `<div><b>${escapeHtml(t.name)}</b> <span class="muted">(${escapeHtml(t.team)})</span></div><span class="badge">#${i + 1}</span>`;
          turnList.appendChild(el);
        });
      }

      // units
      const unit = (u) => {
        const st = (u.statuses || []).slice(0, 3).map(s => `<span class="tag p">${escapeHtml(s)}</span>`).join('');
        return `
          <div class="unit">
            <div class="unitName">${escapeHtml(u.name)}</div>
            <div class="unitMeta">HP <span class="mono">${u.hp}</span></div>
            <div class="gcardTags" style="margin-top:8px">${st || '<span class="tag">—</span>'}</div>
          </div>
        `;
      };

      if (enemyRow) enemyRow.innerHTML = combat.enemies.map(unit).join('');
      if (allyRow) allyRow.innerHTML = combat.allies.map(unit).join('');

      // cards
      const renderCard = (c, selectedId) => {
        const tags = (c.tags || []).map(t => `<span class="tag ${t.includes('서치') ? 'p' : ''}">${escapeHtml(t)}</span>`).join('');
        const sel = c.id === selectedId ? ' isSelected' : '';
        return `
          <div class="gcard${sel}" data-card="${c.id}">
            <div class="row">
              <div class="gcardTitle">${escapeHtml(c.name)}</div>
              <div class="grow"></div>
              <div class="badge">${c.cost}</div>
            </div>
            <div class="gcardSub">${escapeHtml(c.desc || '')}</div>
            <div class="gcardTags">${tags}</div>
          </div>
        `;
      };

      if (handRow) {
        handRow.innerHTML = combat.me.hand.map(c => renderCard(c, combat.selectedCardId)).join('');
        $$('[data-card]', handRow).forEach(el => {
          el.onclick = () => {
            const id = el.getAttribute('data-card');
            combat.selectedCardId = (combat.selectedCardId === id) ? null : id;
            save(KEY.combat, combat);
            render();
          };
        });
      }

      if (fieldRow) {
        fieldRow.innerHTML = combat.me.field.map(c => renderCard(c, combat.selectedCardId)).join('');
        $$('[data-card]', fieldRow).forEach(el => {
          el.onclick = () => {
            const id = el.getAttribute('data-card');
            combat.selectedCardId = (combat.selectedCardId === id) ? null : id;
            save(KEY.combat, combat);
            render();
          };
        });
      }

      // global effects chips
      if (globalEffects) {
        globalEffects.innerHTML = '';
        (combat.globalEffects || []).forEach(e => {
          const c = document.createElement('span');
          c.className = 'chip';
          c.textContent = e;
          globalEffects.appendChild(c);
        });
      }

      // log
      if (logPanel) {
        logPanel.innerHTML = '';
        log.slice(0, 40).forEach(item => {
          const el = document.createElement('div');
          el.className = 'logItem';
          el.innerHTML = `<div class="logHead mono">${escapeHtml(new Date(item.at).toLocaleString())}</div><div class="logBody">${escapeHtml(item.text)}</div>`;
          logPanel.appendChild(el);
        });
      }

      bind();
    };

    const draw = (combat, n) => {
      for (let i = 0; i < n; i++) {
        if (combat.me.deck.length === 0) {
          // refill from grave
          combat.me.deck = shuffle(combat.me.grave);
          combat.me.grave = [];
          pushLog('덱 리필: 묘지를 덱으로 되돌림');
        }
        const c = combat.me.deck.shift();
        if (!c) break;
        combat.me.hand.push(c);
      }
    };

    const shuffle = (arr) => {
      const a = [...arr];
      for (let i = a.length - 1; i > 0; i--) {
        const j = rand(i + 1);
        [a[i], a[j]] = [a[j], a[i]];
      }
      return a;
    };

    const startCombat = () => {
      const presets = load(KEY.presets, []);
      const selId = load(KEY.selectedPreset, null);
      const preset = presets.find(p => p.id === selId) || presets[0];

      const deckNames = (preset?.deckText || '').split(',').map(s => s.trim()).filter(Boolean);
      const deck = deckNames.map(normalizeCard).filter(Boolean);

      const combat = defaultCombat();
      combat.round = 1;
      combat.currentTurn = 0;
      combat.usedHandSwapThisTurn = false;
      combat.me.deck = shuffle(deck);
      combat.me.hand = [];
      combat.me.grave = [];
      combat.me.field = [];
      combat.me.exiled = [];
      combat.me.exCooldown = 0;

      // start hand 4
      draw(combat, 4);

      save(KEY.combat, combat);

      const session = load(KEY.session, null);
      if (session) {
        session.phase = 'combat';
        save(KEY.session, session);
      }

      pushLog(`전투 시작: 프리셋 "${preset?.name || '—'}" (손패 4장)`);
      toast('전투 시작', '손패 4장 드로우');
      render();
    };

    const endTurn = () => {
      const combat = load(KEY.combat, defaultCombat());

      // next turn
      combat.currentTurn = (combat.currentTurn + 1) % combat.turnOrder.length;
      if (combat.currentTurn === 0) combat.round += 1;

      combat.selectedCardId = null;
      combat.usedHandSwapThisTurn = false;

      // demo: only "me" draws on my turn
      const current = combat.turnOrder[combat.currentTurn];
      pushLog(`턴 전환: ${current.name}`);

      if (current.id === 'me') {
        const need = combat.me.hand.length < 4 ? 2 : 1;
        draw(combat, need);
        pushLog(`턴 시작 드로우: ${need}장`);

        // hand limit (demo warning)
        if (combat.me.hand.length > 6) {
          pushLog('손패 초과: 6장 초과 → 버리기 UI 필요(데모)');
          toast('손패 초과', '버리기 UI 필요');
        }
      }

      // EX cooldown tick
      if (combat.me.exCooldown > 0) combat.me.exCooldown -= 1;

      save(KEY.combat, combat);
      render();
    };

    const useSelectedCard = (fromZone) => {
      const combat = load(KEY.combat, defaultCombat());
      const id = combat.selectedCardId;
      if (!id) {
        toast('카드 선택', '먼저 카드를 클릭');
        return;
      }

      const zone = fromZone === 'field' ? combat.me.field : combat.me.hand;
      const idx = zone.findIndex(c => c.id === id);
      if (idx < 0) {
        toast('오류', '카드를 찾을 수 없음');
        return;
      }

      const card = zone[idx];

      // demo: cost check (AP)
      if (combat.me.ap < card.cost) {
        pushLog(`거부: 코스트 부족 (${combat.me.ap} < ${card.cost})`);
        toast('거부', '코스트 부족');
        return;
      }

      // spend AP
      combat.me.ap -= card.cost;

      // search card opens modal
      if ((card.tags || []).some(t => t.includes('서치')) || card.name.includes('Search')) {
        openSearchModal(card);
        // do not move card yet (assume effect resolves first)
        save(KEY.combat, combat);
        render();
        return;
      }

      // install/summon stays on field
      if ((card.tags || []).some(t => t.includes('설치') || t.includes('소환'))) {
        if (combat.me.field.length >= 5) {
          pushLog('거부: 필드 최대 5');
          toast('거부', '필드 최대 5');
          return;
        }
        // move to field if from hand
        if (fromZone !== 'field') {
          zone.splice(idx, 1);
          combat.me.field.push(card);
          pushLog(`카드 사용(설치): ${card.name} → 필드`);
        } else {
          pushLog(`카드 활성(필드): ${card.name}`);
        }
      } else {
        // normal card goes to grave
        zone.splice(idx, 1);
        combat.me.grave.push(card);
        pushLog(`카드 사용: ${card.name} → 묘지`);
      }

      combat.selectedCardId = null;
      save(KEY.combat, combat);
      render();
    };

    const openSearchModal = (sourceCard) => {
      const combat = load(KEY.combat, defaultCombat());
      searchSelectedId = null;
      searchPick.disabled = true;

      // demo filter: show only cost 1 cards from deck
      const candidates = combat.me.deck.filter(c => c.cost === 1).slice(0, 12);
      const pool = candidates.length ? candidates : combat.me.deck.slice(0, 12);

      if (searchSubtitle) searchSubtitle.textContent = `조건: (데모) 코스트 1 우선 · 후보 ${pool.length}장`;

      if (searchList) {
        searchList.innerHTML = pool.map(c => `
          <div class="gcard" data-search="${c.id}">
            <div class="row">
              <div class="gcardTitle">${escapeHtml(c.name)}</div>
              <div class="grow"></div>
              <div class="badge">${c.cost}</div>
            </div>
            <div class="gcardSub">${escapeHtml(c.desc || '')}</div>
            <div class="gcardTags">${(c.tags||[]).map(t=>`<span class="tag">${escapeHtml(t)}</span>`).join('')}</div>
          </div>
        `).join('');

        $$('[data-search]', searchList).forEach(el => {
          el.onclick = () => {
            $$('[data-search]', searchList).forEach(x => x.classList.remove('isSelected'));
            el.classList.add('isSelected');
            searchSelectedId = el.getAttribute('data-search');
            searchPick.disabled = !searchSelectedId;
          };
        });
      }

      openModal('#searchModal');

      // stash on modal
      searchModal.dataset.source = sourceCard.id;
    };

    const closeSearch = () => {
      closeModal('#searchModal');
      searchSelectedId = null;
      searchPick.disabled = true;
    };

    btnStart?.addEventListener('click', startCombat);

    btnEndTurn?.addEventListener('click', () => {
      // reset AP each turn for demo
      const combat = load(KEY.combat, defaultCombat());
      const current = combat.turnOrder[combat.currentTurn];
      if (current.id === 'me') {
        // end my turn
        combat.me.ap = 3;
        save(KEY.combat, combat);
      }
      endTurn();
    });

    btnHandSwap?.addEventListener('click', () => {
      const combat = load(KEY.combat, defaultCombat());
      const current = combat.turnOrder[combat.currentTurn];
      if (current.id !== 'me') {
        toast('불가', '내 턴 아님');
        return;
      }
      if (combat.usedHandSwapThisTurn) {
        toast('불가', '턴당 1회');
        return;
      }
      if (combat.me.hand.length === 0) {
        toast('불가', '손패 없음');
        return;
      }
      // discard first card
      const c = combat.me.hand.shift();
      combat.me.grave.push(c);
      combat.usedHandSwapThisTurn = true;

      // draw 1
      const before = combat.me.hand.length;
      if (combat.me.deck.length === 0) {
        combat.me.deck = shuffle(combat.me.grave);
        combat.me.grave = [];
        pushLog('덱 리필: 묘지를 덱으로');
      }
      const d = combat.me.deck.shift();
      if (d) combat.me.hand.push(d);

      pushLog(`패 교환: ${c.name} 버림 → 1장 드로우`);
      save(KEY.combat, combat);
      render();
    });

    btnUseEx?.addEventListener('click', () => {
      const combat = load(KEY.combat, defaultCombat());
      const current = combat.turnOrder[combat.currentTurn];
      if (current.id !== 'me') {
        toast('불가', '내 턴 아님');
        return;
      }
      if (combat.me.exCooldown > 0) {
        toast('불가', `쿨다운 ${combat.me.exCooldown}`);
        return;
      }
      combat.me.exCooldown = 1;
      pushLog('EX 사용: (데모) 다음 라운드 종료까지 비활성 처리');
      toast('EX', '사용됨');
      save(KEY.combat, combat);
      render();
    });

    // click outside selection usage: double click card to use
    handRow?.addEventListener('dblclick', () => useSelectedCard('hand'));
    fieldRow?.addEventListener('dblclick', () => useSelectedCard('field'));

    btnToGrave?.addEventListener('click', () => {
      const combat = load(KEY.combat, defaultCombat());
      const id = combat.selectedCardId;
      if (!id) {
        toast('선택 필요', '필드 카드를 선택');
        return;
      }
      const idx = combat.me.field.findIndex(c => c.id === id);
      if (idx < 0) {
        toast('불가', '필드 카드만 가능');
        return;
      }
      const card = combat.me.field.splice(idx, 1)[0];
      combat.me.grave.push(card);
      combat.selectedCardId = null;
      pushLog(`필드 정리: ${card.name} → 묘지`);
      save(KEY.combat, combat);
      render();
    });

    btnClearLog?.addEventListener('click', () => {
      save(KEY.log, []);
      toast('로그', '초기화');
      render();
    });

    // search modal events
    searchClose?.addEventListener('click', closeSearch);
    searchCancel?.addEventListener('click', closeSearch);
    searchModal?.addEventListener('click', (e) => { if (e.target === searchModal) closeSearch(); });

    searchPick?.addEventListener('click', () => {
      if (!searchSelectedId) return;
      const combat = load(KEY.combat, defaultCombat());

      const idx = combat.me.deck.findIndex(c => c.id === searchSelectedId);
      if (idx < 0) {
        toast('오류', '덱에서 찾을 수 없음');
        return;
      }

      const picked = combat.me.deck.splice(idx, 1)[0];
      combat.me.hand.push(picked);
      combat.me.deck = shuffle(combat.me.deck);

      // after search, move source card to grave (demo)
      const srcId = searchModal.dataset.source;
      const hIdx = combat.me.hand.findIndex(c => c.id === srcId);
      if (hIdx >= 0) {
        const used = combat.me.hand.splice(hIdx, 1)[0];
        combat.me.grave.push(used);
      }

      pushLog(`덱 서치: ${picked.name} 선택 → 손패 / 덱 셔플`);
      toast('서치 완료', picked.name);

      combat.selectedCardId = null;
      save(KEY.combat, combat);
      closeSearch();
      render();
    });

    // initial render
    render();
  };

  const boot = () => {
    ensureSeed();

    const page = document.querySelector('[data-page]')?.getAttribute('data-page');
    if (page === 'home') initHome();
    if (page === 'lobby') initLobby();
    if (page === 'presets') initPresets();
    if (page === 'node') initNode();
    if (page === 'combat') initCombat();

    bind();
  };

  document.addEventListener('DOMContentLoaded', boot);
})();
