/* === Educational Psychology Daily — PWA edition (백엔드 0) ===
 *
 * State priorities mirror the Android app:
 *   - install epoch day (localStorage) drives "today's paper"
 *   - day index navigation, history, search, favorites all client-side
 *   - translation = open external service in new tab (no API calls)
 */
'use strict';

const PREFS_KEY_INSTALL_DAY = 'edupsych:installEpochDay';
const PREFS_KEY_FAVORITES   = 'edupsych:favorites';

const $ = (id) => document.getElementById(id);

const els = {
  views: {
    today:    $('view-today'),
    history:  $('view-history'),
    settings: $('view-settings'),
  },
  dayLabel:    $('day-label'),
  paperTitle:  $('paper-title'),
  paperMeta:   $('paper-meta'),
  paperAuthors:$('paper-authors'),
  paperAbstract:$('paper-abstract'),
  fav:         $('btn-fav'),
  doi:         $('btn-doi'),
  prev:        $('btn-prev'),
  next:        $('btn-next'),
  todayJump:   $('btn-today-jump'),
  history:     $('btn-history'),
  settings:    $('btn-settings'),
  translatePapago: $('btn-translate-papago'),
  translateGoogle: $('btn-translate-google'),
  refreshData: $('btn-refresh-data'),
  searchInput: $('search-input'),
  tabAll:      $('tab-all'),
  tabFavs:     $('tab-favs'),
  historyList: $('history-list'),
  historyEmpty:$('history-empty'),
  infoCount:   $('info-count'),
};

const state = {
  papers: [],
  todayIdx: 0,
  dayIdx: 0,
  favorites: new Set(),
  showFavoritesOnly: false,
  query: '',
};

// ---------- bootstrap ----------
async function init() {
  state.favorites = loadFavorites();
  await loadPapers();

  state.todayIdx = computeTodayIdx();
  state.dayIdx = state.todayIdx;

  bindEvents();
  registerServiceWorker();
  render();
}

async function loadPapers() {
  // Cache-first via service worker. Fallback shows an error if even the
  // bundled copy fails to load.
  try {
    const res = await fetch('papers.json', { cache: 'no-cache' });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const json = await res.json();
    state.papers = Array.isArray(json) ? json : (json.papers || []);
    if (state.papers.length === 0) throw new Error('empty papers list');
  } catch (e) {
    document.body.innerHTML =
      `<p style="padding:40px;color:#ba1a1a">데이터 로드 실패: ${e.message}</p>`;
    throw e;
  }
}

function computeTodayIdx() {
  const today = epochDay();
  let installDay = Number(localStorage.getItem(PREFS_KEY_INSTALL_DAY));
  if (!Number.isFinite(installDay) || installDay <= 0) {
    installDay = today;
    localStorage.setItem(PREFS_KEY_INSTALL_DAY, String(installDay));
  }
  const n = state.papers.length;
  const delta = today - installDay;
  return ((delta % n) + n) % n;
}

function epochDay() {
  // Days since 1970-01-01 in *local* timezone, matching Android's
  // LocalDate.now(systemDefault()).toEpochDay().
  const now = new Date();
  const local = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.floor(local.getTime() / 86400000);
}

// ---------- event binding ----------
function bindEvents() {
  els.prev.onclick = () => { state.dayIdx = (state.dayIdx - 1 + state.papers.length) % state.papers.length; render(); };
  els.next.onclick = () => { state.dayIdx = (state.dayIdx + 1) % state.papers.length; render(); };
  els.todayJump.onclick = () => { state.dayIdx = state.todayIdx; render(); };
  els.history.onclick = () => showView('history');
  els.settings.onclick = () => showView('settings');

  document.querySelectorAll('[data-close]').forEach((btn) => {
    btn.onclick = () => showView('today');
  });

  els.fav.onclick = () => {
    const id = state.papers[state.dayIdx].id;
    if (state.favorites.has(id)) state.favorites.delete(id);
    else state.favorites.add(id);
    saveFavorites();
    render();
  };

  els.translatePapago.onclick = () => {
    const p = state.papers[state.dayIdx];
    const text = `${p.title}\n\n${p.abstract}`;
    // Papago accepts ~5000 chars in `st`. Truncate defensively.
    const truncated = text.length > 4500 ? text.slice(0, 4500) + '…' : text;
    const url = `https://papago.naver.com/?sk=en&tk=ko&st=${encodeURIComponent(truncated)}`;
    window.open(url, '_blank', 'noopener');
  };

  els.translateGoogle.onclick = () => {
    const p = state.papers[state.dayIdx];
    const text = `${p.title}\n\n${p.abstract}`;
    const url = `https://translate.google.com/?sl=en&tl=ko&op=translate&text=${encodeURIComponent(text)}`;
    window.open(url, '_blank', 'noopener');
  };

  els.refreshData.onclick = async () => {
    if (!('serviceWorker' in navigator)) {
      alert('이 브라우저는 캐시 새로고침을 지원하지 않습니다.');
      return;
    }
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      if (reg) await reg.update();
      const res = await fetch('papers.json', { cache: 'reload' });
      if (res.ok) {
        const json = await res.json();
        state.papers = Array.isArray(json) ? json : (json.papers || []);
        state.todayIdx = computeTodayIdx();
        state.dayIdx = state.todayIdx;
        render();
        alert('데이터 갱신 완료');
      }
    } catch (e) {
      alert('갱신 실패: ' + e.message);
    }
  };

  // History
  els.tabAll.onclick = () => { state.showFavoritesOnly = false; renderHistory(); };
  els.tabFavs.onclick = () => { state.showFavoritesOnly = true; renderHistory(); };
  els.searchInput.addEventListener('input', () => {
    state.query = els.searchInput.value.trim();
    renderHistory();
  });
}

// ---------- view switching ----------
function showView(name) {
  for (const [k, el] of Object.entries(els.views)) {
    el.hidden = (k !== name);
  }
  if (name === 'history') renderHistory();
  // Reset scroll on view switch
  window.scrollTo(0, 0);
}

// ---------- render: today ----------
function render() {
  const p = state.papers[state.dayIdx];
  if (!p) return;

  els.dayLabel.textContent = `Day ${state.dayIdx + 1} / ${state.papers.length}`;
  els.paperTitle.textContent = p.title;

  const ageYears = Math.max(new Date().getFullYear() - p.year, 3);
  const perYear = (p.scorePerYear ?? (p.citedBy / ageYears)).toFixed(1);
  els.paperMeta.textContent = `인용 ${p.citedBy.toLocaleString()}회 · ${p.year}년 · ${perYear}/yr`;

  els.paperAuthors.textContent = (p.authors || []).filter(Boolean).join(', ');
  els.paperAbstract.textContent = p.abstract;

  const fav = state.favorites.has(p.id);
  els.fav.textContent = fav ? '★' : '☆';
  els.fav.classList.toggle('empty', !fav);
  els.fav.setAttribute('aria-pressed', String(fav));

  if (p.doi) {
    els.doi.href = 'https://doi.org/' + p.doi;
    els.doi.hidden = false;
  } else {
    els.doi.hidden = true;
  }

  els.todayJump.hidden = (state.dayIdx === state.todayIdx);

  els.infoCount.textContent = `${state.papers.length}편`;
  els.tabFavs.textContent = `즐겨찾기 (${state.favorites.size})`;
  els.tabAll.textContent = `전체 (${state.papers.length})`;
}

// ---------- render: history ----------
function renderHistory() {
  els.tabAll.classList.toggle('active', !state.showFavoritesOnly);
  els.tabFavs.classList.toggle('active', state.showFavoritesOnly);

  let list = state.papers;
  if (state.showFavoritesOnly) list = list.filter((p) => state.favorites.has(p.id));
  if (state.query) {
    const q = state.query.toLowerCase();
    list = list.filter((p) =>
      p.title.toLowerCase().includes(q) ||
      (p.authors || []).some((a) => a && a.toLowerCase().includes(q))
    );
  }

  els.historyList.innerHTML = '';
  els.historyEmpty.hidden = list.length > 0;

  const frag = document.createDocumentFragment();
  for (const p of list) {
    const row = document.createElement('div');
    row.className = 'row-item';
    const isToday = (p.dayIndex === state.todayIdx);
    const isFav = state.favorites.has(p.id);

    row.innerHTML = `
      <div>
        <div class="row-day ${isToday ? 'today' : ''}">Day ${p.dayIndex + 1}</div>
        ${isToday ? '<span class="row-today-tag">오늘</span>' : ''}
      </div>
      <div>
        <div class="row-title"></div>
        <div class="row-meta">인용 ${p.citedBy.toLocaleString()} · ${p.year}</div>
      </div>
      <div class="row-star">${isFav ? '★' : ''}</div>
    `;
    row.querySelector('.row-title').textContent = p.title;
    row.onclick = () => {
      state.dayIdx = p.dayIndex;
      showView('today');
      render();
    };
    frag.appendChild(row);
  }
  els.historyList.appendChild(frag);
}

// ---------- favorites persistence ----------
function loadFavorites() {
  try {
    const raw = localStorage.getItem(PREFS_KEY_FAVORITES);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return new Set(Array.isArray(arr) ? arr : []);
  } catch {
    return new Set();
  }
}

function saveFavorites() {
  localStorage.setItem(PREFS_KEY_FAVORITES, JSON.stringify([...state.favorites]));
}

// ---------- service worker ----------
function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return;
  if (location.protocol !== 'https:' && location.hostname !== 'localhost' &&
      location.hostname !== '127.0.0.1') return;
  navigator.serviceWorker.register('sw.js').catch((e) => {
    console.warn('SW register failed:', e);
  });
}

init();
