const byId = (id) => document.getElementById(id);
const messages = byId("messages");
const demoPasswords = { user1: "user1-pass", user2: "user2-pass", admin: "admin-pass" };
let toastTimer;

function authHeader() {
  return `Basic ${btoa(`${byId("username").value}:${byId("password").value}`)}`;
}

function notify(message, kind = "") {
  const toast = byId("toast");
  toast.textContent = message;
  toast.className = `toast ${kind}`.trim();
  toast.style.opacity = "1";
  toast.style.transform = "translateY(0)";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toast.style.opacity = ".22";
    toast.style.transform = "translateY(5px)";
  }, 3600);
}

async function apiJson(path, options = {}) {
  if (window.HelpDeskDemo?.enabled) {
    return window.HelpDeskDemo.request(path, options, byId("username").value);
  }
  const headers = { Authorization: authHeader(), ...(options.headers || {}) };
  const request = { ...options, headers };
  if (options.body && typeof options.body !== "string") {
    headers["Content-Type"] = "application/json";
    request.body = JSON.stringify(options.body);
  }
  const response = await fetch(path, request);
  const text = await response.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }
  if (!response.ok) {
    const detail = data?.detail || data?.error || (typeof data === "string" ? data : "요청 실패");
    throw new Error(`${response.status} · ${detail}`);
  }
  return data;
}

function setBusy(button, busy, busyText) {
  if (!button.dataset.label) button.dataset.label = button.textContent;
  button.disabled = busy;
  button.textContent = busy ? busyText : button.dataset.label;
}

function addMessage(role, text = "", label) {
  const article = document.createElement("article");
  article.className = `message ${role}`;
  const badge = document.createElement("span");
  badge.textContent = role === "user" ? "ME" : "AI";
  const body = document.createElement("div");
  const name = document.createElement("small");
  name.textContent = label || (role === "user" ? byId("username").value : "HelpDesk");
  const content = document.createElement("p");
  content.textContent = text;
  body.append(name, content);
  article.append(badge, body);
  messages.append(article);
  messages.scrollTop = messages.scrollHeight;
  return content;
}

function resetConversation(message = "새 세션이 준비되었습니다. 질문을 입력해 주세요.") {
  messages.replaceChildren();
  addMessage("assistant", message);
  renderMeta([], false, "-");
}

function renderMeta(sources = [], toolUsed = false, traceId = "-") {
  byId("sources").textContent = sources.length ? sources.join(", ") : "근거 없음";
  byId("toolUsed").textContent = toolUsed ? "사용" : "미사용";
  byId("traceId").textContent = traceId || "-";
}

async function sendSync(question) {
  const result = await apiJson("/api/chat", {
    method: "POST",
    body: { message: question, sessionId: byId("sessionId").value }
  });
  addMessage("assistant", result.answer || "응답이 없습니다.");
  renderMeta(result.sources || [], Boolean(result.toolUsed), result.traceId);
}

async function submitQuestion(question) {
  addMessage("user", question);
  byId("question").value = "";
  const button = byId("send");
  setBusy(button, true, "응답 중…");
  notify("동기 JSON 응답을 기다리는 중…");
  try {
    await sendSync(question);
    notify("응답이 완료되었습니다.", "success");
  } catch (error) {
    addMessage("assistant", `오류: ${error.message}`);
    notify(`응답 실패: ${error.message}`, "error");
  } finally {
    setBusy(button, false);
    byId("question").focus();
  }
}

byId("chatForm").addEventListener("submit", (event) => {
  event.preventDefault();
  const question = byId("question").value.trim();
  if (question) submitQuestion(question);
});

document.querySelectorAll(".scenario").forEach((button) => {
  button.addEventListener("click", () => submitQuestion(button.dataset.question));
});

document.querySelectorAll(".tab").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((tab) => tab.classList.toggle("active", tab === button));
    document.querySelectorAll(".panel").forEach((panel) => panel.classList.toggle("active", panel.id === button.dataset.tab));
  });
});

byId("username").addEventListener("change", (event) => {
  byId("password").value = demoPasswords[event.target.value] || "";
  notify(`${event.target.value} 계정으로 변경했습니다.`);
});

byId("newSession").addEventListener("click", () => {
  byId("sessionId").value = `web-${Date.now().toString(36)}`;
  resetConversation();
  notify("새 대화 세션을 만들었습니다.", "success");
});

byId("loadHistory").addEventListener("click", async () => {
  try {
    const history = await apiJson(`/api/chat/history?sessionId=${encodeURIComponent(byId("sessionId").value)}`);
    messages.replaceChildren();
    if (!history?.length) addMessage("assistant", "저장된 대화 기록이 없습니다.");
    else history.forEach((item) => addMessage(item.role === "USER" ? "user" : "assistant", item.text, item.role));
    notify(`${history?.length || 0}개 메시지를 불러왔습니다.`, "success");
  } catch (error) {
    notify(`기록 조회 실패: ${error.message}`, "error");
  }
});

byId("clearHistory").addEventListener("click", async () => {
  try {
    await apiJson(`/api/chat/history?sessionId=${encodeURIComponent(byId("sessionId").value)}`, { method: "DELETE" });
    resetConversation("대화 기록을 삭제했습니다.");
    notify("현재 세션의 메모리를 삭제했습니다.", "success");
  } catch (error) {
    notify(`기록 삭제 실패: ${error.message}`, "error");
  }
});

byId("ingest").addEventListener("click", async () => {
  const button = byId("ingest");
  setBusy(button, true, "인제스트 중…");
  try {
    const result = await apiJson("/api/admin/ingest", { method: "POST" });
    byId("ingestResult").classList.remove("empty");
    byId("ingestResult").textContent = result.map((item) =>
      `${item.source} · ${item.chunks} chunks · ${item.version}`).join("\n");
    notify(`${result.length}개 규정 문서를 재색인했습니다.`, "success");
  } catch (error) {
    notify(`인제스트 실패: ${error.message}`, "error");
  } finally {
    setBusy(button, false);
  }
});

function renderChunks(chunks) {
  const container = byId("chunkResults");
  container.replaceChildren();
  container.classList.toggle("empty", !chunks.length);
  if (!chunks.length) {
    container.textContent = "검색 결과가 없습니다.";
    return;
  }
  chunks.forEach((chunk) => {
    const item = document.createElement("article");
    item.className = "chunk";
    const head = document.createElement("div");
    head.className = "chunk-head";
    const source = document.createElement("strong");
    source.textContent = `${chunk.source} · #${chunk.chunk} · ${chunk.version}`;
    const score = document.createElement("span");
    score.textContent = Number(chunk.score || 0).toFixed(4);
    const preview = document.createElement("p");
    preview.textContent = chunk.preview;
    head.append(source, score);
    item.append(head, preview);
    container.append(item);
  });
}

byId("chunkForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const params = new URLSearchParams({ q: byId("chunkQuery").value, topK: byId("chunkTopK").value });
    const chunks = await apiJson(`/api/admin/chunks?${params}`);
    renderChunks(chunks || []);
    notify(`${chunks?.length || 0}개 청크를 찾았습니다.`, "success");
  } catch (error) {
    notify(`청크 검색 실패: ${error.message}`, "error");
  }
});

function appendCell(row, value, className = "") {
  const cell = document.createElement("td");
  cell.textContent = value ?? "-";
  if (className) cell.className = className;
  row.append(cell);
  return cell;
}

async function loadTickets() {
  const tickets = await apiJson("/api/admin/tickets/pending");
  const body = byId("ticketRows");
  body.replaceChildren();
  if (!tickets?.length) {
    const row = document.createElement("tr");
    appendCell(row, "현재 대기 중인 티켓이 없습니다.", "empty-cell").colSpan = 6;
    body.append(row);
    return;
  }
  tickets.forEach((ticket) => {
    const row = document.createElement("tr");
    appendCell(row, ticket.no);
    appendCell(row, ticket.orderId);
    appendCell(row, ticket.userId);
    appendCell(row, ticket.type);
    appendCell(row, ticket.reason);
    const action = document.createElement("td");
    const approve = document.createElement("button");
    approve.className = "approve";
    approve.type = "button";
    approve.textContent = "승인";
    approve.addEventListener("click", async () => {
      setBusy(approve, true, "처리 중");
      try {
        await apiJson(`/api/admin/tickets/${encodeURIComponent(ticket.no)}/approve`, { method: "POST" });
        notify(`${ticket.no} 티켓을 승인했습니다.`, "success");
        await loadTickets();
      } catch (error) {
        notify(`승인 실패: ${error.message}`, "error");
        setBusy(approve, false);
      }
    });
    action.append(approve);
    row.append(action);
    body.append(row);
  });
}

async function loadMetrics() {
  const metrics = await apiJson("/api/admin/metrics-summary");
  const values = [
    ["Prompt tokens", metrics.promptTokens],
    ["Completion tokens", metrics.completionTokens],
    ["Model calls", metrics.modelCalls],
    ["Tool calls", metrics.toolCalls],
    ["Safety blocks", metrics.safetyRejections],
    ["Fallbacks", metrics.fallbackActivations]
  ];
  const container = byId("metricCards");
  container.replaceChildren();
  values.forEach(([label, value]) => {
    const card = document.createElement("div");
    card.className = "metric";
    const name = document.createElement("span");
    name.textContent = label;
    const count = document.createElement("strong");
    count.textContent = Number(value || 0).toLocaleString();
    card.append(name, count);
    container.append(card);
  });
}

async function loadAudit() {
  const events = await apiJson("/api/admin/audit");
  const body = byId("auditRows");
  body.replaceChildren();
  if (!events?.length) {
    const row = document.createElement("tr");
    appendCell(row, "아직 도구 호출 기록이 없습니다.", "empty-cell").colSpan = 6;
    body.append(row);
    return;
  }
  events.slice(0, 30).forEach((event) => {
    const row = document.createElement("tr");
    appendCell(row, new Date(event.at).toLocaleTimeString("ko-KR"));
    appendCell(row, event.traceId);
    appendCell(row, event.userId);
    appendCell(row, event.tool);
    appendCell(row, event.result);
    appendCell(row, `${event.elapsedMs} ms`);
    body.append(row);
  });
}

async function runAdmin(action, successMessage) {
  try {
    await action();
    notify(successMessage, "success");
  } catch (error) {
    notify(`관리자 요청 실패: ${error.message}`, "error");
  }
}

byId("refreshTickets").addEventListener("click", () => runAdmin(loadTickets, "티켓을 새로고침했습니다."));
byId("refreshMetrics").addEventListener("click", () => runAdmin(loadMetrics, "지표를 새로고침했습니다."));
byId("refreshAudit").addEventListener("click", () => runAdmin(loadAudit, "감사 로그를 새로고침했습니다."));
byId("refreshAdmin").addEventListener("click", () => runAdmin(
  () => Promise.all([loadTickets(), loadMetrics(), loadAudit()]),
  "운영 데이터를 모두 새로고침했습니다."
));

const pageParams = new URLSearchParams(window.location.search);
if (window.HelpDeskDemo?.enabled) {
  byId("runtimeLabel").textContent = "GitHub Pages · 브라우저 데모";
  document.querySelector(".live-dot").classList.add("demo");
  notify("GitHub Pages 데모 모드입니다. 데이터는 이 브라우저에만 저장됩니다.", "success");
}
const requestedAccount = pageParams.get("account");
if (requestedAccount && demoPasswords[requestedAccount]) {
  byId("username").value = requestedAccount;
  byId("password").value = demoPasswords[requestedAccount];
}
if (pageParams.get("view") === "admin") {
  document.querySelector('[data-tab="adminPanel"]').click();
  if (requestedAccount === "admin") byId("refreshAdmin").click();
}
