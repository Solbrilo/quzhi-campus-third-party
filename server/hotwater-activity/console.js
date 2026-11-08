"use strict";
const $ = id => document.getElementById(id);
const base = "/project-qzxy-console";
let csrf = "", stats = null, notices = [], responseNotice = null;
const el = (name, text, cls) => { const node = document.createElement(name); if (text != null) node.textContent = text; if (cls) node.className = cls; return node; };
function status(text) { $("status").textContent = text; clearTimeout(status.timer); status.timer = setTimeout(() => $("status").textContent = "", 5000); }
async function api(path, data) {
  const response = await fetch(base + path, {method: data === undefined ? "GET" : "POST", credentials: "same-origin",
    headers: data === undefined ? {} : {"Content-Type": "application/json", "X-CSRF-Token": csrf}, body: data === undefined ? undefined : JSON.stringify(data)});
  const body = await response.json();
  if (!response.ok) { if (response.status === 401 || response.status === 403) showLogin(); throw new Error(body.error || "请求失败，请稍后重试"); }
  return body;
}
function showLogin() { clearFeedback(); csrf = ""; $("workspace").hidden = true; $("logout").hidden = true; $("login").hidden = false; document.querySelectorAll("dialog[open]").forEach(d => d.close()); }
async function enter() { $("login").hidden = true; $("workspace").hidden = false; $("logout").hidden = false; await refresh(); }
$("login-form").addEventListener("submit", async event => {
  event.preventDefault(); const button = event.submitter; button.disabled = true; $("login-error").textContent = "";
  const password = $("password").value; $("password").value = "";
  try { csrf = (await api("/login", {password})).csrf; await enter(); } catch (error) { $("login-error").textContent = error.message; }
  finally { button.disabled = false; }
});
$("logout").onclick = async () => { try { await api("/logout", {}); showLogin(); } catch (error) { status(error.message); } };
async function refresh() {
  $("refresh").disabled = true;
  try {
    const results = await Promise.all([api("/stats"), api("/notices")]); stats = results[0]; notices = results[1].notices;
    renderStats(); renderNotices();
    if (!$("feedback").hidden) await loadFeedback(true);
  } catch (error) { status(error.message); } finally { $("refresh").disabled = false; }
}
$("refresh").onclick = refresh;
document.querySelectorAll("[data-tab]").forEach(button => button.onclick = () => {
  document.querySelectorAll("[data-tab]").forEach(b => b.classList.toggle("selected", b === button));
  ["overview", "announcements", "feedback"].forEach(id => $(id).hidden = id !== button.dataset.tab);
  if (button.dataset.tab === "overview" && stats) renderStats();
  if (button.dataset.tab === "feedback") loadFeedback(true);
});
function renderStats() {
  $("asof").textContent = stats.today + " · 北京时间 · 匿名安装统计";
  $("dau").textContent = stats.daily.at(-1)?.count || 0;
  $("yesterday").textContent = stats.daily.at(-2)?.count || 0;
  $("mau").textContent = stats.monthly.find(x => x.period === stats.today.slice(0, 7))?.count || 0;
  const daily = stats.daily.slice(-Number($("days").value));
  chart("daily-chart", daily); chart("monthly-chart", stats.monthly);
  table("daily-table", daily); table("monthly-table", stats.monthly);
  bars("versions", stats.versions[$("version-kind").value].map(x => ({label: x.version, count: x.count})));
}
$("days").onchange = () => stats && renderStats(); $("version-kind").onchange = () => stats && renderStats();
function chart(id, data) {
  const host = $(id); host.replaceChildren();
  if (!data.length) { host.append(el("p", "还没有使用数据", "empty")); return; }
  const width = Math.max(280, host.clientWidth), right = width - 10;
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg"); svg.setAttribute("viewBox", "0 0 " + width + " 260"); svg.setAttribute("preserveAspectRatio", "none");
  const make = (name, attrs, text) => { const n = document.createElementNS(svg.namespaceURI, name); Object.entries(attrs).forEach(([k, v]) => n.setAttribute(k, v)); if (text != null) n.textContent = text; svg.append(n); return n; };
  const max = Math.max(4, ...data.map(x => x.count));
  [0, .25, .5, .75, 1].forEach(f => { const y = 220 - 190 * f; make("line", {x1: 40, x2: right, y1: y, y2: y, stroke: "#e3e9df", "stroke-width": 1}); make("text", {x: 30, y: y + 4, "text-anchor": "end"}, String(Math.round(max * f))); });
  const points = data.map((d, i) => [40 + i / Math.max(1, data.length - 1) * (right - 40), 220 - d.count / max * 190]);
  make("path", {d: "M40,220 L" + points.map(p => p.join(",")).join(" L") + " L" + points.at(-1)[0] + ",220 Z", fill: "#dfeadd", opacity: .8});
  make("polyline", {points: points.map(p => p.join(",")).join(" "), fill: "none", stroke: "#527b60", "stroke-width": 2.5, "stroke-linejoin": "round", "vector-effect": "non-scaling-stroke"});
  data.forEach((d, i) => { const n = make("circle", {cx: points[i][0], cy: points[i][1], r: 3, fill: "#527b60"}); const t = document.createElementNS(svg.namespaceURI, "title"); t.textContent = d.period + "：" + d.count; n.append(t); });
  [...new Set([0, Math.floor((data.length - 1) / 2), data.length - 1])].forEach(i => make("text", {x: points[i][0], y: 250, "text-anchor": i === 0 ? "start" : i === data.length - 1 ? "end" : "middle"}, data[i].period));
  host.append(svg);
}
function table(id, rows) { const t = el("table"), header = el("tr"); header.append(el("th", "日期"), el("th", "活跃安装")); t.append(header); rows.slice().reverse().forEach(row => { const tr = el("tr"); tr.append(el("td", row.period), el("td", row.count)); t.append(tr); }); $(id).replaceChildren(t); }
function bars(id, rows) {
  const host = $(id); host.replaceChildren(); if (!rows.length) { host.append(el("p", "暂无数据", "empty")); return; }
  const total = rows.reduce((n, r) => n + r.count, 0);
  rows.forEach(row => { const wrap = el("div"), label = el("div", null, "version-label"), bar = el("div", null, "bar"), fill = el("i"); label.append(el("span", row.label), el("span", row.count + " · " + (total ? Math.round(row.count / total * 100) : 0) + "%")); fill.style.width = (total ? row.count / total * 100 : 0) + "%"; bar.append(fill); wrap.append(label, bar); host.append(wrap); });
}
const formatTime = sec => new Intl.DateTimeFormat("zh-CN", {timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23"}).format(new Date(sec * 1000));
function renderNotices() {
  const host = $("notice-list"); host.replaceChildren();
  if (!notices.length) { host.append(el("div", "还没有公告。写下第一条消息吧。", "panel empty")); return; }
  notices.forEach(n => {
    const card = el("article", null, "panel notice-card"), now = Date.now() / 1000;
    const state = !n.enabled ? "已停用" : n.expires <= now ? "已结束" : n.starts > now ? "待生效" : n.pushes > now ? "已生效 · 待推送" : "已推送";
    card.append(el("span", state + " / v" + n.revision, "tag"), el("h3", n.title), el("p", n.body), el("small", formatTime(n.pushes) + " 推送 · " + formatTime(n.expires) + " 到期", "hint"));
    const actions = el("div", null, "notice-actions"), edit = el("button", "编辑", "quiet"), replies = el("button", "查看回复", "quiet"); edit.onclick = () => openEditor(n); replies.onclick = () => openResponses(n); actions.append(edit, replies); card.append(actions); host.append(card);
  });
}
const inputTime = sec => new Date(sec * 1000 + 8 * 3600000).toISOString().slice(0, 16);
const parseTime = id => Math.floor(Date.parse($(id).value + ":00+08:00") / 1000);
function openEditor(notice = null) {
  $("notice-form").reset(); $("options").replaceChildren(); $("editor-error").textContent = "";
  $("editor-title").textContent = notice ? "编辑公告" : "新建公告";
  $("notice-id").value = notice?.id || ""; $("notice-revision").value = notice?.revision || "";
  $("notice-title").value = notice?.title || ""; $("notice-body").value = notice?.body || "";
  const now = Math.floor(Date.now() / 60000) * 60;
  $("starts").value = inputTime(notice?.starts || now); $("pushes").value = inputTime(notice?.pushes || now + 300); $("expires").value = inputTime(notice?.expires || now + 86400 * 7);
  $("notice-enabled").checked = notice?.enabled ?? true; (notice?.options || []).forEach(addOption);
  $("editor").showModal();
}
$("new-notice").onclick = () => openEditor();
function addOption(option = {}) {
  if ($("options").children.length >= 8) return status("最多添加 8 个选项");
  const row = el("div", null, "option-row"), label = el("input"), check = el("label", null, "check"), input = el("input"), remove = el("button", "移除", "quiet");
  label.type = "text"; label.placeholder = "例如：已了解 / 留下建议"; label.maxLength = 80; label.required = true; label.setAttribute("aria-label", "回复选项文字"); label.value = option.label || "";
  input.type = "checkbox"; input.checked = !!option.input; check.append(input, document.createTextNode("自由输入")); remove.type = "button"; remove.onclick = () => row.remove(); row.append(label, check, remove); $("options").append(row);
}
$("add-option").onclick = () => addOption();
$("notice-form").onsubmit = async event => {
  event.preventDefault(); $("save-notice").disabled = true; $("editor-error").textContent = "";
  const body = {title: $("notice-title").value, body: $("notice-body").value, starts: parseTime("starts"), pushes: parseTime("pushes"), expires: parseTime("expires"), enabled: $("notice-enabled").checked,
    options: [...$("options").children].map(row => ({label: row.querySelector("input[type=text]").value, input: row.querySelector("input[type=checkbox]").checked}))};
  if ($("notice-id").value) { body.id = $("notice-id").value; body.revision = Number($("notice-revision").value); }
  try { await api("/notices", body); $("editor").close(); status("公告已保存"); await refresh(); }
  catch (error) { $("editor-error").textContent = error.message; } finally { $("save-notice").disabled = false; }
};
async function openResponses(notice) {
  responseNotice = notice; $("responses-title").textContent = notice.title;
  $("response-revision").replaceChildren();
  for (let r = notice.revision; r >= 1; r--) { const option = el("option", "第 " + r + " 版" + (r === notice.revision ? " · 当前" : "")); option.value = r; $("response-revision").append(option); }
  $("response-counts").replaceChildren(); $("response-texts").replaceChildren(); $("responses").showModal(); await loadResponses();
}
async function loadResponses() {
  const n = responseNotice, revision = Number($("response-revision").value);
  try {
    const result = await api("/responses/" + n.id + "/" + revision);
    if (responseNotice !== n || Number($("response-revision").value) !== revision) return;
    const label = id => (result.options || n.options).find(o => o.id === id)?.label || "选项 " + id;
    bars("response-counts", result.counts.map(r => ({label: label(r.option), count: r.count})));
    $("response-texts").replaceChildren();
    result.texts.forEach(reply => { const box = el("article", null, "reply"); box.append(el("small", label(reply.option) + " · " + formatTime(reply.submitted)), el("p", reply.text)); $("response-texts").append(box); });
  } catch (error) { status(error.message); }
}
$("response-revision").onchange = loadResponses;
document.querySelectorAll("[data-close]").forEach(b => b.onclick = () => $(b.dataset.close).close());

let feedbackSequence = 0, feedbackCursor = null, feedbackCount = 0;
const feedbackLabels = {bug: "问题反馈", suggestion: "功能建议", experience: "使用体验", other: "其他"};
function clearFeedback() {
  feedbackSequence++; feedbackCursor = null; feedbackCount = 0;
  $("feedback-list").replaceChildren(); $("feedback-summary").textContent = "";
  $("feedback-more").hidden = true;
}
async function loadFeedback(reset) {
  const sequence = ++feedbackSequence;
  if (reset) { feedbackCursor = null; feedbackCount = 0; $("feedback-list").replaceChildren(); }
  const query = new URLSearchParams({month: $("feedback-month").value, platform: $("feedback-platform").value, category: $("feedback-category").value});
  if (feedbackCursor !== null) query.set("cursor", feedbackCursor);
  $("feedback-summary").textContent = "正在读取反馈…"; $("feedback-more").disabled = true;
  try {
    const data = await api("/feedback?" + query);
    if (sequence !== feedbackSequence || !csrf) return;
    const select = $("feedback-month"); select.replaceChildren();
    (data.months.length ? data.months : [""]).forEach(month => { const option = el("option", month || "暂无归档"); option.value = month; select.append(option); });
    select.value = data.month;
    data.items.forEach(item => {
      const card = el("article", null, "panel feedback-card"), heading = el("div", null, "feedback-heading");
      heading.append(el("span", feedbackLabels[item.category] || "其他", "tag"),
        el("small", (item.platform === "android" ? "Android" : "HarmonyOS") + " · v" + item.appVersion));
      const received = Date.parse(item.receivedAt);
      card.append(heading, el("p", item.message, "feedback-message"),
        el("small", Number.isFinite(received) ? formatTime(received / 1000) + " · 北京时间" : "时间未知", "hint"));
      const contact = el("details"), summary = el("summary", "联系信息与反馈编号");
      contact.append(summary, el("p", item.email, "feedback-contact"), el("code", item.id));
      card.append(contact); $("feedback-list").append(card);
    });
    feedbackCount += data.items.length; feedbackCursor = data.nextCursor;
    $("feedback-more").hidden = feedbackCursor === null;
    $("feedback-summary").textContent = feedbackCount ? "已显示 " + feedbackCount + " 条 · 最新反馈在前" : feedbackCursor !== null ? "本段没有匹配反馈，可继续加载更早记录。" : "当前月份和筛选条件下暂无反馈。";
  } catch (error) {
    if (sequence === feedbackSequence) {
      $("feedback-summary").textContent = "暂时无法读取反馈，请重试。";
      $("feedback-more").hidden = false; status(error.message);
    }
  } finally { if (sequence === feedbackSequence) $("feedback-more").disabled = false; }
}
["feedback-month", "feedback-platform", "feedback-category"].forEach(id => $(id).onchange = () => loadFeedback(true));
$("feedback-more").onclick = () => loadFeedback(false);

api("/session").then(async s => { csrf = s.csrf; await enter(); }).catch(() => showLogin());

let resizeTimer; window.addEventListener("resize", () => { clearTimeout(resizeTimer); resizeTimer = setTimeout(() => { if (stats && !$("overview").hidden) renderStats(); }, 100); });
