# gstack 사용 가이드 (oh-my-lawyer 프로젝트)

## gstack이란?

gstack은 Claude Code에 설치하는 skill 모음으로, AI 에이전트가 웹 브라우징, QA 테스트, 코드 리뷰, 배포, 설계 검토 등을 자동화할 수 있게 해주는 도구다.

핵심은 **headless Chromium 브라우저**를 Claude Code가 직접 제어할 수 있다는 점. 페이지를 열고, 클릭하고, 폼을 채우고, 스크린샷을 찍고, 상태를 검증하는 것을 전부 CLI 명령어로 처리한다.

## 설치 위치

```
~/.claude/skills/gstack/
```

## 주요 Skill 목록

### 브라우징 & QA
| Skill | 설명 |
|-------|------|
| `/browse` | 헤드리스 브라우저로 웹 페이지 탐색. 모든 웹 브라우징은 이것으로 |
| `/qa` | 사이트 전체 QA 테스트 수행 |
| `/qa-only` | 코드 변경 없이 QA만 수행 |
| `/connect-chrome` | 실제 Chrome 브라우저 연결 |

### 개발 워크플로우
| Skill | 설명 |
|-------|------|
| `/ship` | PR 생성, 배포까지 자동화 |
| `/land-and-deploy` | PR 머지 후 배포 |
| `/review` | 코드 리뷰 |
| `/canary` | 카나리 배포 |
| `/benchmark` | 성능 벤치마크 |

### 기획 & 설계 검토
| Skill | 설명 |
|-------|------|
| `/office-hours` | 아이디어 브레인스토밍, "이거 만들 가치 있나?" |
| `/plan-ceo-review` | 전략/스코프 관점 리뷰 |
| `/plan-eng-review` | 아키텍처/기술 관점 리뷰 |
| `/plan-design-review` | UI/UX 관점 리뷰 |
| `/plan-devex-review` | 개발자 경험 관점 리뷰 |
| `/autoplan` | 위 리뷰를 자동으로 전부 수행 |

### 디자인
| Skill | 설명 |
|-------|------|
| `/design-consultation` | 디자인 시스템, 브랜드 상담 |
| `/design-shotgun` | 빠른 디자인 프로토타입 |
| `/design-html` | HTML/CSS 디자인 생성 |
| `/design-review` | 라이브 사이트 비주얼 감사 |

### 운영 & 기타
| Skill | 설명 |
|-------|------|
| `/investigate` | 버그/에러 원인 추적 |
| `/retro` | 주간 회고 |
| `/document-release` | 릴리즈 문서화 |
| `/codex` | 독립적인 2nd opinion (Codex 모델) |
| `/cso` | 보안 관점 리뷰 |
| `/learn` | 세션에서 배운 것 저장 |
| `/careful` | 안전 모드 (신중한 작업) |
| `/freeze` / `/unfreeze` | 특정 디렉토리 편집 제한/해제 |
| `/guard` | 보호 모드 |
| `/gstack-upgrade` | gstack 자체 업그레이드 |
| `/setup-browser-cookies` | 브라우저 쿠키 설정 |
| `/setup-deploy` | 배포 설정 |
| `/devex-review` | 개발자 경험 리뷰 |

## browse 핵심 사용법

`/browse`는 가장 자주 쓰게 될 skill이다. headless Chromium을 ~100ms 단위로 제어한다.

### 페이지 열기 & 읽기
```bash
$B goto https://example.com       # 페이지 이동
$B text                            # 페이지 텍스트 읽기
$B screenshot /tmp/page.png        # 스크린샷
$B snapshot -i                     # 인터랙티브 요소 목록 (@e1, @e2...)
```

### 요소 조작
```bash
$B snapshot -i                     # 먼저 요소 확인
$B click @e3                       # 클릭
$B fill @e4 "검색어"               # 입력
$B select @e5 "옵션값"             # 드롭다운 선택
$B upload @e6 /path/to/file.pdf    # 파일 업로드
```

### 상태 검증
```bash
$B is visible ".success-toast"     # 요소 보이는지
$B is enabled "#submit-btn"        # 버튼 활성화 상태
$B console                         # JS 에러 확인
$B network                         # 네트워크 요청 확인
$B snapshot -D                     # 이전 상태와 diff
```

### 반응형 테스트
```bash
$B responsive /tmp/layout          # 모바일/태블릿/데스크톱 한번에
$B viewport 375x812                # 특정 뷰포트
```

## oh-my-lawyer 프로젝트에서 활용

### 현재 상황 정리

- **목표**: 법률자문 AI Agent 구축
- **기술 스택**: Kotlin SpringBoot + PostgreSQL (Vector DB, RAG) + React (SSR)
- **배포**: React는 서버에서 빌드된 페이지 전달
- **에이전트**: 로컬 Claude Code로 질의
- **참고 데이터**: 법률사무소와의 실제 자문 대화 (example.md - 개인정보보호법 관련)

### gstack으로 할 수 있는 작업들

#### 1단계: 기획 - `/office-hours`
아이디어 구체화에 사용. "법률자문 AI Agent를 만들고 싶다"는 막연한 상태에서 시작 가능.

```
/office-hours
```
Claude가 질문을 던지면서 요구사항을 구체화해준다.

#### 2단계: 아키텍처 리뷰 - `/plan-eng-review`
기술 스택(SpringBoot + PostgreSQL + React)에 대한 아키텍처 검토.

```
/plan-eng-review
```

#### 3단계: 개발 중 QA - `/qa`
React 화면을 만든 후 자동 QA 테스트. headless 브라우저로 실제 페이지를 열어서 검증.

```
/qa
```
- 폼 입력이 되는지
- API 응답이 오는지
- 에러 상태 처리가 되는지
- 반응형이 깨지지 않는지

#### 4단계: 코드 리뷰 - `/review`
PR 올리기 전 자동 코드 리뷰.

```
/review
```

#### 5단계: 배포 - `/ship`
PR 생성부터 배포까지.

```
/ship
```

#### 버그 발생 시 - `/investigate`
에러 추적, 로그 분석.

```
/investigate
```

#### 법률 리서치 - `/browse`
법률 관련 웹사이트를 브라우징하며 판례, 법령 정보 수집.

```
/browse
```
예: 개인정보보호법 조문, 판례 검색, 법령정보센터 탐색 등

### 추천 작업 순서

현재 "무엇을 해야 하는지 모르는 상태"이므로:

1. **`/office-hours`** 실행 - 요구사항 정리, MVP 스코프 정의
2. **`/autoplan`** 실행 - CEO/엔지니어/디자인 관점에서 종합 리뷰
3. 계획이 확정되면 개발 시작
4. 개발 중 **`/qa`**, **`/review`** 반복
5. 배포 시 **`/ship`**

## 설정

### proactive 모드 (기본: on)
gstack이 대화 맥락을 보고 자동으로 적절한 skill을 제안한다.
```bash
~/.claude/skills/gstack/bin/gstack-config set proactive true   # 켜기
~/.claude/skills/gstack/bin/gstack-config set proactive false  # 끄기
```

### 업그레이드
```
/gstack-upgrade
```

## 핵심 원칙

gstack은 **"Boil the Lake"** 원칙을 따른다. AI가 한계비용을 거의 0으로 만들어주니, 할 수 있는 것은 완전하게 하라는 철학이다.

- 코드 리뷰? 전체를 본다
- QA? 모든 플로우를 테스트한다
- 배포? PR 생성부터 모니터링까지 한번에

## 참고

- gstack GitHub: https://github.com/garrytan/gstack
- 설치 경로: `~/.claude/skills/gstack/`
- 프로젝트 CLAUDE.md에 skill routing이 설정되어 있으면, Claude가 자동으로 적절한 skill을 호출한다
