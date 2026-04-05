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
| 법령 | `eflaw` | ~166,000 | 법률, 시행령, 시행규칙 전문 |
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
    │   └── embedding (vector 768d): 의미 검색용 ← Step 3에서 채움
    └── citations: 문서 간 인용 관계
```

### Chunking 전략

현재는 **조문 단위 chunking**을 초기 방향으로 채택. design.md에서 "최적 전략은 Phase 1에서 A/B 테스트 후 확정"으로 결정.

| 현재 구현 | 향후 고도화 (검색 품질 미달 시) |
|-----------|-------------------------------|
| 조 단위 chunk (항/호 텍스트 포함) | 짧은 조문(1~2줄) 인접 병합 |
| 편/장/절/관 메타데이터 없음 | 상위 계층 구조 메타데이터 추가 |
| 판례: 판시사항/판결요지 SUMMARY + 전문 HOLDING | - |

고도화 기준: example.md A/B 비교에서 인용 recall 80% 미달 시 chunking 전략 변경.

### 이후 사용 (TODO)

수집된 chunks에 Gemini Embedding API로 768차원 벡터를 생성하여 `law_chunks.embedding`에 저장. 이후 사용자 질의 시 hybrid search(벡터 유사도 + 키워드 매칭)로 관련 법률 데이터를 retrieve하고, LLM이 이를 근거로 법률 의견을 생성한다.

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

## 수집 API

```bash
# 전체 수집 시작
POST /api/collect/all

# 개별 수집
POST /api/collect/laws
POST /api/collect/cases
POST /api/collect/constitutional
POST /api/collect/interpretations
POST /api/collect/administrative-rules

# 진행 상태 조회
GET /api/collect/status

# 진행 초기화
POST /api/collect/reset/{dataType}
```

## 프로젝트 구조

```
com.ohmylawyer/
├── collection/              # 데이터 수집 기능
│   ├── controller/          # REST API 엔드포인트
│   ├── service/             # 수집 오케스트레이션, 스케줄링
│   ├── collector/           # 수집 구현체 (AbstractCollector + 5종)
│   ├── client/              # 법제처 Open API HTTP 클라이언트
│   ├── parser/              # JSON 응답 파서 (LawApiParser 인터페이스 + 41개 테스트)
│   ├── dto/                 # 응답 DTO
│   └── config/              # Retry 설정
├── domain/                  # 공유 도메인
│   ├── entity/              # LawDocument, LawChunk, Citation, CollectionProgress
│   └── repository/          # Spring Data JPA Repository
```

## 진행 상황

### Step 1: 프로젝트 초기화 — DONE
- Spring Boot + PostgreSQL(pgvector) + Flyway
- Domain entities, DB 스키마 (vector, tsvector, HNSW 인덱스)

### Step 2: 데이터 수집 파이프라인 — DONE
- 법제처 Open API 연동 (5종)
- Parser 분리 (LawApiParser 인터페이스 + 41개 테스트)
- 코루틴 병렬 처리 (supervisorScope + Semaphore)
- @Retryable + 커스텀 RetryPolicy (429/5xx만 재시도)
- @Scheduled 큐 기반 수집 (최대 3개 병렬)
- Graceful shutdown + 서버 재시작 시 자동 복구
- REQUIRES_NEW 트랜잭션 격리
- WebClient 30초 timeout + DB count 기반 진행률

### Step 3: 임베딩 파이프라인 — TODO
- Gemini Embedding API 연동
- LawChunk.embedding 벡터 저장

### Step 4: 검색 시스템 — TODO
- Hybrid search (tsvector + pgvector)
- Query rewriting (Gemini)

### Step 5: LLM 통합 + 챗 UI — TODO
- Gemini API 연동, iterative retrieval
- 인용 검증 후처리
- Thymeleaf 챗 인터페이스
