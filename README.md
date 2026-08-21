# SKALA HelpDesk AI — 종합 실습

`day01`, `day02`, `day03`에서 배운 구조화 응답, RAG, Tool, Memory, Safety,
Advisor, 관찰성을 하나의 상담 어시스턴트로 조립한 독립 Spring Boot 프로젝트다.

## 핵심 흐름

```text
정적 웹 UI → /api/chat 또는 /api/chat/stream
            → HelpDeskChatService → ChatClient
            → Audit → Safety → JDBC Memory → RAG → Meter
                                          ├─ OrderTools
                                          └─ TicketTools → PENDING 승인 게이트
```

| 요구사항 | 구현 | 확인 |
|---|---|---|
| 규정 근거 + 출처 | `QuestionAnswerAdvisor`, `source/version/chunk` 메타데이터 | `POST /api/chat` |
| 재색인 | 소스별 안정 ID로 기존 청크 삭제 후 add | `POST /api/admin/ingest` |
| 인제스트 품질 | 출처·버전·점수·미리보기 | `GET /api/admin/chunks` |
| 주문 조회 | 인증 Principal → `ToolContext`; repository에서 소유자 조건 검증 | `OrderTools` |
| 교환·환불 | 도구는 `PENDING` 티켓만 생성, ADMIN API만 승인 | `TicketTools` |
| 멀티턴 메모리 | `tenant:user:session` 대화 ID + 파일 HSQLDB + 20개 윈도우 | history API |
| 안전 | 인젝션·민감정보를 Memory 전에 차단 | `SafetyAdvisor` |
| 감사·관찰 | 마스킹된 도구 감사, traceId, 토큰·지연·성공/실패 지표 | `/api/admin/*`, Actuator |
| SSE | `token` 이벤트 후 `sources` 이벤트 | `POST /api/chat/stream` |
| 폴백 | 모델 오류·60초 timeout 시 안전한 축소 응답 계속 | `ai.fallback.activations` |

## 실행

구독 서비스에서 발급한 API 키를 환경변수로 넣는다. 키는 YAML이나 Git에 저장하지 않는다.

```bash
cd final
export OPENAI_API_KEY="sk-..."
./gradlew bootRun
```

브라우저에서 <http://localhost:8080>을 연다. 처음에는 `admin / admin-pass`로
로그인 입력을 바꿔 `규정 재색인`을 누른 뒤, `user1 / user1-pass`로 상담한다.

웹 화면은 추가 가산점용 통합 UI로 구성했다.

- 고객 상담: 안정적인 동기 JSON 응답, RAG·Tool·Memory·승인·Safety 빠른 시나리오
- 세션 관리: 새 세션 생성, JDBC 대화 기록 조회 및 삭제
- 운영 대시보드: 문서 재색인, 유사도/메타데이터 청크 검색
- Human-in-the-loop: PENDING 교환·환불 티켓 확인 및 사람 승인
- 관찰성: 토큰·모델·도구·차단·폴백 지표와 최근 Tool 감사 로그

| 계정 | 기본 비밀번호 | 소유 주문 | 역할 |
|---|---|---|---|
| `user1` | `user1-pass` | `12345`, `12346` | USER |
| `user2` | `user2-pass` | `99999` | USER |
| `admin` | `admin-pass` | - | USER, ADMIN |

비밀번호는 `HELPDESK_USER1_PASSWORD`, `HELPDESK_USER2_PASSWORD`,
`HELPDESK_ADMIN_PASSWORD` 환경변수로 바꾸는다. Swagger UI는
<http://localhost:8080/swagger-ui.html>, 전체 호출 예시는 [`http/final.http`](http/final.http)에 있다.

## 설정

`application.yml`에서 모델, RAG `top-k`/`threshold`, 청크 크기, 메모리 윈도우를
조정한다. 기본 대화 모델은 스트리밍과 function calling을 지원하는 `gpt-4o-mini`,
임베딩은 `text-embedding-3-small`이다.

## 검증

```bash
./gradlew test
```

외부 모델을 호출하지 않는 단위 테스트가 본인/타인 주문 격리, 신원 주입 무효화,
PENDING 승인 게이트, 도구 호출 상한, Advisor 순서와 SSE 정책 적용을 검증한다.

> 실습용 SimpleVectorStore와 주문·티켓 repository는 인메모리다. 운영에서는 pgvector,
> 영속 업무 DB, 조직 OIDC/JWT로 교체해야 한다.
