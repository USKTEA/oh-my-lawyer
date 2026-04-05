# oh-my-lawyer

## 무엇을 위한 프로젝트인가

아파트 도메인 서비스 회사의 PO(Product Owner)들이 약관 변경, 서비스 개편, 입주민 컴플레인 대응 시 **법적 리스크를 사전 검토**할 수 있는 AI 도구.

현재 프로세스: "인터넷 검색 → 괜찮겠지 → 개발 → 로펌 자문 → 사후 수정"

목표 프로세스: **"AI 검토 → 근거 기반 판단 → 개발"**

## 어디에 사용되나

사내 PO 조직에서 서비스 개발/약관 변경 시 1차 법률 리스크 필터로 사용. 로펌 자문 전 단계에서 리스크 등급(상/중/하)과 근거 조문/판례를 즉시 제공한다.

## 어떻게 사용되나

```
PO 질문 입력 (자연어)
    │
    ▼
┌─────────────────────────────────────────────────┐
│  1. Query Rewriting                             │
│     Gemini가 업무 용어를 법률 검색 쿼리로 변환    │
│     "입주민 정보 경찰에 줘도 되나?"               │
│      → "수사기관 사실조회 개인정보 제3자 제공"     │
│                                                 │
│  2. Hybrid Search (pgvector + tsvector)          │
│     벡터 유사도 검색 + 키워드 정확 매칭            │
│     → 관련 조문, 판례, 해석례 후보 retrieve       │
│                                                 │
│  3. LLM 추론 (Iterative Retrieval, 최대 3회)     │
│     1회차: 검색 결과 기반 초안 작성               │
│     2~3회차: 부족한 근거 추가 검색 → 보완         │
│                                                 │
│  4. 인용 검증 후처리                              │
│     LLM이 인용한 조문/판례가 DB에 실재하는지 검증  │
│     검증 실패 시 해당 인용 제거 + 경고 표시        │
└─────────────────────────────────────────────────┘
    │
    ▼
구조화된 답변 출력
  - 리스크 등급 (상/중/하)
  - 관련 조문 + 판례 근거
  - 법률 분석 의견
  - 면책 조항: "AI 참고 자료이며, 법률적 조언이 아닙니다"
```

## 데이터 파이프라인

### 데이터 출처

법제처 국가법령정보센터 Open API (`http://www.law.go.kr/DRF/`)에서 5종 법률 데이터를 수집한다.

| 데이터 | API target | 건수 | 설명 |
|--------|------------|------|------|
| 법령 | `eflaw` | ~5,500 (현행만) | 법률, 시행령, 시행규칙 전문 |
| 판례 | `prec` | ~171,000 | 대법원, 하급심 판례 |
| 헌재결정 | `detc` | ~37,500 | 헌법재판소 결정례 |
| 해석례 | `expc` | ~8,600 | 법제처 법령해석례 |
| 행정규칙 | `admrul` | ~23,800 | 고시, 훈령 (개인정보보호위원회 등) |

### 수집 → 파싱 → 저장

```
법제처 API 목록 조회 (페이징, 100건/페이지)
    │
    ▼
항목별 상세 API 호출 (3건 병렬, 코루틴)
    │
    ▼
Parser가 JSON 응답을 도메인 객체로 변환
    │  - LawParser: 조 단위 chunking (항/호 텍스트 포함)
    │  - CaseParser: 판시사항/판결요지(SUMMARY) + 전문(HOLDING) 분리
    │  - ConstitutionalParser: 결정요지(SUMMARY) + 전문(HOLDING) 분리
    │  - InterpretationParser: 질의요지/회답/이유 통합
    │  - AdministrativeRuleParser: 조문 단위 chunking
    │
    ▼
PostgreSQL 저장
    ├── law_documents: 원본 문서 (제목, 전문, 메타데이터)
    ├── law_chunks: 검색 단위 (조문/판시사항/전문 등)
    │   ├── search_vector (tsvector): 키워드 검색용
    │   └── embedding (vector 1536d): 의미 검색용 (gemini-embedding-001)
    └── citations: 문서 간 인용 관계
```

### Chunking 전략

데이터별 특성에 맞는 chunking + **겹침 분할(overlap chunking)**으로 토큰 제한 내 의미 보존.

| 데이터 | chunk_type | 분할 기준 |
|--------|-----------|----------|
| 법령 | `ARTICLE` | 조문 단위 (항/호 텍스트 포함) |
| 판례 | `SUMMARY` | 판시사항 + 판결요지 |
| 판례 | `HOLDING` | 전문 — 【섹션】헤더로 1차 분할 → TextChunker로 2차 분할 |
| 헌재결정 | `SUMMARY` | 판시사항 + 결정요지 |
| 헌재결정 | `HOLDING` | 전문 — TextChunker로 분할 |
| 해석례 | `INTERPRETATION_BODY` | 질의요지/회답/이유 통합 |
| 행정규칙 | `ARTICLE` | 조문 단위 |

**TextChunker 파라미터** (gemini-embedding-001 입력 제한 2,048 토큰 기준):
- 최대 chunk 크기: 2,500자 (~1,825 토큰)
- 겹침(overlap): 500자
- 문장 경계에서 분할 (한국어 종결어미 패턴)
- fallback: 문장 경계 없으면 강제 분할 + overlap

고도화 기준: 인용 recall 80% 미달 시 구조 기반 분할(structure-aware chunking)로 전환.

### 검색 시스템

Gemini Embedding API(`gemini-embedding-001`, 1536d)로 임베딩 생성 후, **RRF(Reciprocal Rank Fusion)** 기반 하이브리드 검색.

```
사용자 질의
    │
    ▼
Gemini Embedding (RETRIEVAL_QUERY, 1536d)
    │
    ▼
┌───────────────────────────────────────┐
│  Vector Search (pgvector, HNSW)       │ → 순위 기반 점수
│  Keyword Search (tsvector, GIN)       │ → 순위 기반 점수
│                                       │
│  RRF Score = 1/(k+rank_v) + 1/(k+rank_k)  (k=60)
└───────────────────────────────────────┘
    │
    ▼
Top-K chunks → 관련 law_documents 확장 → LLM 컨텍스트
```

- 문서 인덱싱: `RETRIEVAL_DOCUMENT` task type
- 검색 쿼리: `RETRIEVAL_QUERY` task type (비대칭 쌍)
- RRF가 가중치 합산 대비 유리한 이유: cosine 유사도(0~1)와 ts_rank(스케일 무관)의 점수 스케일 차이를 순위 기반으로 해소

## Tech Stack

- Kotlin 2.3.20 + Spring Boot 4.0.5
- PostgreSQL 17 + pgvector (벡터 검색) + tsvector (키워드 검색)
- Gemini API (LLM + Embedding)
- Flyway (DB 마이그레이션)
- Spring Retry + 코루틴 (병렬 수집)

## 실행

```bash
# 1. 환경 변수 설정
cp .env.example .env
# .env 파일에 LAW_API_OC, GEMINI_API_KEY 입력

# 2. DB 실행
docker compose up -d

# 3. 서버 실행
./gradlew bootRun
```

## API

### 수집

```bash
POST /api/collect/all                    # 전체 수집 시작
POST /api/collect/{dataType}             # 개별 수집
GET  /api/collect/status                 # 진행 상태 조회
POST /api/collect/reset/{dataType}       # 진행 초기화
```

### 임베딩

```bash
POST /api/embedding/start/{dataType}     # 개별 임베딩 시작
POST /api/embedding/start-all            # 전체 임베딩 시작
GET  /api/embedding/progress             # 전체 진행률 조회
GET  /api/embedding/progress/{dataType}  # 개별 진행률 조회
```

### 검색

```bash
GET  /api/search?query=개인정보+제3자+제공&topK=10&types=LAW,CASE
POST /api/search                         # JSON body로 검색
```

## 프로젝트 구조

```
com.ohmylawyer/
├── collection/              # 데이터 수집 기능
│   ├── controller/          # REST API 엔드포인트
│   ├── service/             # 수집 오케스트레이션, 스케줄링
│   ├── collector/           # 수집 구현체 (AbstractCollector + 5종)
│   ├── client/              # 법제처 Open API HTTP 클라이언트
│   ├── parser/              # JSON 응답 파서 + TextChunker
│   ├── dto/                 # 응답 DTO
│   └── config/              # Retry 설정
├── embedding/               # 임베딩 생성
│   ├── client/              # Gemini Embedding API 클라이언트
│   ├── service/             # 배치 임베딩 서비스
│   └── controller/          # 임베딩 트리거 API
├── search/                  # 하이브리드 검색
│   ├── controller/          # 검색 API
│   ├── service/             # 검색 서비스
│   ├── repository/          # RRF 네이티브 쿼리
│   └── dto/                 # 검색 요청/응답 DTO
├── domain/                  # 공유 도메인
│   ├── entity/              # LawDocument, LawChunk, Citation, CollectionProgress
│   └── repository/          # Spring Data JPA Repository
```

## 진행 상황

### Step 1: 프로젝트 초기화 — DONE
- Spring Boot + PostgreSQL(pgvector) + Flyway
- Domain entities, DB 스키마 (vector, tsvector, HNSW 인덱스)

### Step 2: 데이터 수집 파이프라인 — DONE
- 법제처 Open API 연동 (5종: 법령/판례/헌재결정/해석례/행정규칙)
- 기능 기반 패키지 구조 (collection/{controller,service,collector,client,parser,dto,config})
- Parser 분리 (LawApiParser 인터페이스 + 5개 구현체 + 41개 테스트)
- CollectionService 오케스트레이션 (OCP — 새 Collector 추가 시 Service/Controller 수정 불필요)
- CollectionStatus/DocumentType enum + ResponseDTO (타입 안전한 상태 관리)
- @Scheduled 큐 기반 수집 — 요청(enqueue)과 처리(processQueue)를 분리, 서버 재시작에도 큐 유지
- 코루틴 병렬 처리 (supervisorScope + Semaphore, 최대 3개 collector 동시 실행)
- @Retryable + 커스텀 RetryPolicy (429/5xx만 재시도, maxInterval 10초)
- REQUIRES_NEW 트랜잭션 격리 (개별 아이템 실패가 JPA 세션에 영향 없음)
- DB unique index 기반 중복 방어 + DataIntegrityViolationException 명시적 처리
- WebClient 30초 timeout (search + getDetail 양쪽)
- DB count 기반 정확한 진행률 추적
- Graceful shutdown (@PreDestroy) + 서버 재시작 시 RUNNING → QUEUED 자동 복구 (@PostConstruct)

### Step 3: 임베딩 + 검색 인프라 — DONE
- Gemini Embedding API 연동 (gemini-embedding-001, 1536d)
- 배치 임베딩 서비스 (batchEmbedContents, 진행률 추적, 재시작 가능)
- RRF 하이브리드 검색 (pgvector cosine + tsvector rank, k=60)
- 비대칭 task type: RETRIEVAL_DOCUMENT(인덱싱) / RETRIEVAL_QUERY(검색)
- DB 마이그레이션: vector(768) → vector(1536)
- 법령 수집 개선: 현행만 필터, sourceId를 법령일련번호로 변경
- TextChunker: 문장 경계 겹침 분할 (2500자, 500자 overlap)
- 파일 로깅 (50MB rolling, 30일 보관)

### Step 4: Query Rewriting — TODO
- Gemini를 이용한 업무 용어 → 법률 검색 쿼리 변환

### Step 5: LLM 통합 + 챗 UI — TODO
- Gemini API 연동, iterative retrieval
- 인용 검증 후처리
- Thymeleaf 챗 인터페이스
