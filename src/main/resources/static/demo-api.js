(function () {
  const params = new URLSearchParams(window.location.search);
  const enabled = window.location.hostname.endsWith(".github.io") || params.get("demo") === "true";
  const storageKey = "skala-helpdesk-pages-demo-v1";
  const policyChunks = [
    {
      source: "return-policy.md",
      version: "2026-08-20",
      chunk: 0,
      score: 0.9341,
      preview: "단순 변심 반품은 상품 수령일로부터 7일 이내 신청할 수 있습니다."
    },
    {
      source: "shipping-policy.md",
      version: "2026-08-20",
      chunk: 0,
      score: 0.8617,
      preview: "배송이 시작된 주문은 운송장과 현재 배송 단계를 조회할 수 있습니다."
    },
    {
      source: "membership.md",
      version: "2026-08-20",
      chunk: 0,
      score: 0.7824,
      preview: "회원 등급과 혜택은 최근 구매 실적을 기준으로 산정됩니다."
    }
  ];

  function initialState() {
    return {
      histories: {},
      tickets: [],
      audit: [],
      ticketSequence: 1,
      metrics: {
        promptTokens: 0,
        completionTokens: 0,
        modelCalls: 0,
        toolCalls: 0,
        safetyRejections: 0,
        fallbackActivations: 0
      }
    };
  }

  function loadState() {
    try {
      return { ...initialState(), ...JSON.parse(localStorage.getItem(storageKey) || "{}") };
    } catch {
      return initialState();
    }
  }

  function saveState(state) {
    localStorage.setItem(storageKey, JSON.stringify(state));
  }

  function wait(ms = 320) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  function historyKey(user, sessionId) {
    return `${user}:${sessionId}`;
  }

  function addAudit(state, user, traceId, tool, result) {
    state.audit.unshift({
      at: new Date().toISOString(),
      traceId,
      userId: user,
      tool,
      result,
      elapsedMs: 18 + Math.floor(Math.random() * 45)
    });
    state.audit = state.audit.slice(0, 50);
  }

  function findOrderId(message, history = []) {
    const currentOrderId = message.match(/\b(12345|12346|99999)\b/)?.[1];
    if (currentOrderId) return currentOrderId;

    for (let index = history.length - 1; index >= 0; index -= 1) {
      if (history[index].role !== "USER") continue;
      const previousOrderId = history[index].text.match(/\b(12345|12346|99999)\b/)?.[1];
      if (previousOrderId) return previousOrderId;
    }
    return undefined;
  }

  function answerFor(state, user, message, traceId, history = []) {
    const normalized = message.replace(/\s+/g, " ").trim();
    const lower = normalized.toLowerCase();
    const orderId = findOrderId(normalized, history);
    const isTicketRequest = lower.includes("환불") || lower.includes("교환") || lower.includes("접수");

    if (lower.includes("이전 지시") || lower.includes("시스템 프롬프트") || lower.includes("ignore previous")) {
      state.metrics.safetyRejections += 1;
      return {
        answer: "보안 정책에 따라 시스템 지시나 내부 프롬프트를 공개할 수 없습니다. 주문·배송·반품 관련 질문은 도와드릴게요.",
        sources: [],
        toolUsed: false,
        traceId
      };
    }

    if (orderId) {
      const owner = orderId === "99999" ? "user2" : "user1";
      if (owner !== user) {
        state.metrics.toolCalls += 1;
        addAudit(state, user, traceId, "orderLookup", "DENIED");
        return {
          answer: `주문 ${orderId}은(는) 현재 로그인한 계정의 주문이 아니어서 조회하거나 처리할 수 없습니다.`,
          sources: [],
          toolUsed: true,
          traceId
        };
      }

      if (isTicketRequest) {
        const type = lower.includes("교환") ? "EXCHANGE" : "REFUND";
        const no = `T-${String(state.ticketSequence++).padStart(4, "0")}`;
        state.tickets.push({ no, orderId, userId: user, type, reason: "단순 변심", status: "PENDING" });
        state.metrics.toolCalls += 1;
        addAudit(state, user, traceId, "createTicket", `PENDING:${no}`);
        return {
          answer: `주문 ${orderId}의 ${type === "REFUND" ? "환불" : "교환"} 요청을 ${no}번으로 접수했습니다. 안전을 위해 즉시 처리하지 않고 관리자 승인 대기(PENDING) 상태로 등록했습니다.`,
          sources: ["return-policy.md#0"],
          toolUsed: true,
          traceId
        };
      }

      state.metrics.toolCalls += 1;
      addAudit(state, user, traceId, "orderLookup", "SUCCESS");
      return {
        answer: orderId === "12346"
          ? "주문 12346은 결제 완료 상태이며 아직 출고 전입니다."
          : `주문 ${orderId}은 배송 중이며 오늘 18시 이전 도착 예정입니다.`,
        sources: [],
        toolUsed: true,
        traceId
      };
    }

    if (isTicketRequest) {
      return {
        answer: "환불·교환을 접수할 주문번호를 먼저 알려주세요. 예: 주문 12345를 환불로 접수해 주세요.",
        sources: [],
        toolUsed: false,
        traceId
      };
    }

    if (lower.includes("그거") || lower.includes("반품") || lower.includes("변심") || lower.includes("며칠")) {
      return {
        answer: "단순 변심 반품은 상품 수령일로부터 7일 이내 신청할 수 있습니다. 상품과 포장이 훼손되지 않아야 하며 반품 배송비가 발생할 수 있습니다.",
        sources: ["return-policy.md#0"],
        toolUsed: false,
        traceId
      };
    }

    return {
      answer: "이 공개 페이지는 RAG·Tool·Memory·Safety 흐름을 체험하는 브라우저 데모입니다. 반품 규정이나 주문 12345의 배송 상태를 물어보세요.",
      sources: ["return-policy.md#0"],
      toolUsed: false,
      traceId
    };
  }

  async function request(path, options = {}, user = "user1") {
    await wait();
    const url = new URL(path, window.location.origin);
    const state = loadState();
    const method = (options.method || "GET").toUpperCase();

    if (url.pathname === "/api/chat" && method === "POST") {
      const body = typeof options.body === "string" ? JSON.parse(options.body) : options.body;
      const traceId = `demo-${Date.now().toString(36)}`;
      const key = historyKey(user, body.sessionId);
      state.histories[key] ||= [];
      const response = answerFor(state, user, body.message, traceId, state.histories[key]);
      state.histories[key].push(
        { role: "USER", text: body.message },
        { role: "ASSISTANT", text: response.answer }
      );
      state.metrics.modelCalls += 1;
      state.metrics.promptTokens += Math.max(8, Math.ceil(body.message.length / 2));
      state.metrics.completionTokens += Math.max(12, Math.ceil(response.answer.length / 2));
      saveState(state);
      return response;
    }

    if (url.pathname === "/api/chat/history") {
      const key = historyKey(user, url.searchParams.get("sessionId"));
      if (method === "DELETE") {
        delete state.histories[key];
        saveState(state);
        return null;
      }
      return state.histories[key] || [];
    }

    if (url.pathname.startsWith("/api/admin/") && user !== "admin") {
      throw new Error("403 · 관리자 권한이 필요합니다. admin 계정으로 변경해 주세요.");
    }

    if (url.pathname === "/api/admin/ingest" && method === "POST") {
      return policyChunks.map(({ source, version }) => ({ source, version, chunks: 1 }));
    }

    if (url.pathname === "/api/admin/chunks") {
      const topK = Number(url.searchParams.get("topK") || 5);
      return policyChunks.slice(0, topK);
    }

    if (url.pathname === "/api/admin/tickets/pending") {
      return state.tickets.filter((ticket) => ticket.status === "PENDING");
    }

    const approval = url.pathname.match(/^\/api\/admin\/tickets\/([^/]+)\/approve$/);
    if (approval && method === "POST") {
      const ticket = state.tickets.find((item) => item.no === decodeURIComponent(approval[1]));
      if (!ticket) throw new Error("404 · 티켓을 찾을 수 없습니다.");
      ticket.status = "APPROVED";
      addAudit(state, user, `demo-${Date.now().toString(36)}`, "approveTicket", `APPROVED:${ticket.no}`);
      saveState(state);
      return ticket;
    }

    if (url.pathname === "/api/admin/metrics-summary") return state.metrics;
    if (url.pathname === "/api/admin/audit") return state.audit;
    throw new Error(`404 · 지원하지 않는 데모 경로입니다: ${url.pathname}`);
  }

  window.HelpDeskDemo = { enabled, request };
})();
