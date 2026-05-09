# 교육심리 매일 — Web (PWA)

iPhone 사용자도 같은 콘텐츠를 보게 하기 위한 정적 웹 버전.
Android 네이티브 앱과 같은 `papers.json` 데이터를 공유합니다.

## 기능

- 매일 1편 (하단 epoch day mod 365) — Android 와 동일 알고리즘
- 즐겨찾기 (★) — `localStorage` 에 저장
- 히스토리 (전체 365 / 즐겨찾기 필터)
- 키워드 검색 (제목 + 저자)
- 외부 번역: Papago / Google 새 창
- 다크 모드: 시스템 자동 추종 (`prefers-color-scheme`)
- 오프라인: Service Worker 가 셸 + 데이터 캐시
- 앱화: iOS Safari "홈 화면에 추가" / Android "홈 화면에 추가"

## 로컬 실행

```sh
cd docs/
python -m http.server 8000
# 브라우저에서 http://localhost:8000 접속
```

> Service Worker 는 `localhost` 또는 HTTPS 에서만 동작합니다.

## GitHub Pages 로 배포

1. 이 repo 루트에서:
   ```sh
   git add docs/
   git commit -m "PWA web mirror"
   git push
   ```

2. GitHub repo → Settings → Pages
   - Source: `Deploy from a branch`
   - Branch: `main` / `/` (혹은 `gh-pages` 브랜치)
   - Folder: `/web`  (또는 `/docs` 로 옮겨 `/docs` 선택)

3. 발행 후 URL 예시 (사용자 ID 가 `108nevermind` 이고 repo 이름이 `cherin` 이면):
   ```
   https://108nevermind.github.io/cherin/
   ```

   `docs/` 폴더를 그대로 두면 위 경로 끝에 `docs/` 을 붙여야 할 수도 있습니다.
   가장 간단한 방법은 GitHub Pages 의 `Folder` 설정을 `/web` 으로 지정하는 것.

4. 가족·친구에게 위 URL 만 카톡으로 보내면 끝.

## iPhone 사용자 안내 (1회용)

> 다음 링크를 Safari 에서 열어주세요:
> https://… (위에서 발행된 URL)
>
> Safari 하단 공유(↑) 버튼 → "홈 화면에 추가" 를 누르면 앱처럼 사용할 수 있어요.
> 매일 그 아이콘을 누르면 오늘의 논문 한 편이 표시됩니다.
> 번역이 필요하면 화면의 "번역 (Papago)" 또는 "Google 번역" 버튼을 누르세요.

## Android 네이티브와의 차이

| 기능 | Android | PWA |
|---|---|---|
| 오늘의 논문 회전 | ✓ | ✓ |
| 즐겨찾기 | SharedPreferences | localStorage |
| 히스토리 / 검색 | ✓ | ✓ |
| 다크 모드 | Material You | prefers-color-scheme |
| 한국어 번역 | ML Kit on-device | 외부 사이트 새 창 (Papago/Google) |
| 홈 위젯 | Glance | ❌ (iOS PWA 미지원) |
| 매일 알림 | WorkManager | ❌ (백엔드 필요) |
| 오프라인 | bundled assets | Service Worker 캐시 |

## 데이터 갱신

`assets/papers.json` 또는 `app/src/main/assets/papers.json` 이 새로 만들어질 때 마다
`docs/papers.json` 으로 같은 파일을 복사해 push 합니다.

`scripts/build_assets.py` 가 두 위치 모두에 쓰도록 한 줄 수정 가능
(`OUT_PATHS` 에 `docs/papers.json` 추가). 현재는 수동 복사:

```sh
cp app/src/main/assets/papers.json docs/papers.json
git add docs/papers.json && git commit -m "data refresh $(date +%Y-%m)" && git push
```

## 파일 구조

```
docs/
├── README.md           ← 이 문서
├── index.html          ← 앱 셸
├── app.js              ← 모든 로직 (~250줄)
├── style.css           ← Material 풍 + 다크 자동
├── manifest.json       ← PWA 메타 (홈 추가 시 사용)
├── sw.js               ← 서비스 워커 (오프라인 캐시)
├── papers.json         ← 365편 (Android 와 동일 파일)
└── icons/
    ├── icon-192.png    ← Android mipmap 재사용
    └── icon-512.png    ← 동일 아이콘 업스케일
```
