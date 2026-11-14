"use strict";
const api = window.capturePicker;
const grid = document.getElementById("grid");
const message = document.getElementById("message");
const share = document.getElementById("share");
const refresh = document.getElementById("refresh");
const selection = document.getElementById("selection");
const tabs = [...document.querySelectorAll("[role=tab]")];
let sources = [], kind = "screen", selected = null, loading = false;
function choose(source) {
  selected = source;
  for (const card of grid.children) card.setAttribute("aria-pressed", String(card.dataset.id === source.id));
  share.disabled = false; selection.textContent = source.name;
}
function render() {
  grid.replaceChildren(); selected = null; share.disabled = true;
  selection.textContent = kind === "screen" ? "Selecione um monitor para continuar." : "Selecione uma janela para continuar.";
  grid.classList.toggle("windows", kind === "window");
  const visible = sources.filter(source => source.kind === kind);
  message.hidden = visible.length > 0;
  message.textContent = loading ? "Carregando telas e janelas…" : kind === "screen" ? "Nenhum monitor disponível. Clique em Atualizar para tentar novamente." : "Nenhuma janela disponível. Abra a janela que deseja gravar e clique em Atualizar.";
  for (const source of visible) {
    const card = document.createElement("button"); card.className = "source"; card.dataset.id = source.id;
    card.setAttribute("aria-pressed", "false"); card.setAttribute("aria-label", source.name + ", " + source.detail);
    const preview = document.createElement("div"); preview.className = "preview";
    if (source.thumbnail) { const img = document.createElement("img"); img.src = source.thumbnail; img.alt = ""; preview.append(img); }
    else { const fallback = document.createElement("span"); fallback.className = "placeholder"; fallback.textContent = "Prévia indisponível"; preview.append(fallback); }
    const check = document.createElement("span"); check.className = "check"; check.textContent = "✓"; check.setAttribute("aria-hidden", "true"); preview.append(check);
    const caption = document.createElement("div"); caption.className = "caption";
    const row = document.createElement("div"); row.className = "name-row";
    if (source.icon) { const icon = document.createElement("img"); icon.className = "app-icon"; icon.src = source.icon; icon.alt = ""; row.append(icon); }
    const name = document.createElement("span"); name.className = "name"; name.textContent = source.name; name.title = source.name; row.append(name);
    const detail = document.createElement("span"); detail.className = "detail"; detail.textContent = source.detail;
    caption.append(row, detail); card.append(preview, caption); card.addEventListener("click", () => choose(source)); grid.append(card);
  }
}
function switchTab(tab) {
  kind = tab.dataset.kind;
  for (const button of tabs) { button.setAttribute("aria-selected", String(button === tab)); button.tabIndex = button === tab ? 0 : -1; }
  document.getElementById("sources").setAttribute("aria-labelledby", tab.id); render();
}
for (const tab of tabs) {
  tab.addEventListener("click", () => switchTab(tab));
  tab.addEventListener("keydown", event => { if (["ArrowLeft","ArrowRight","Home","End"].includes(event.key)) { event.preventDefault(); const next = event.key === "Home" ? tabs[0] : event.key === "End" ? tabs[1] : tabs.find(item => item !== tab); switchTab(next); next.focus(); } });
}
async function load() {
  if (loading) return;
  loading = true; sources = []; refresh.disabled = true; render();
  try {
    const result = await api.list();
    if (result.error) throw new Error(result.error);
    sources = result.sources; loading = false;
    for (const type of ["screen", "window"]) document.getElementById("count-" + type).textContent = String(sources.filter(source => source.kind === type).length);
    document.getElementById("audio").textContent = result.audioRequested ? "Áudio do sistema ativado · os sons do computador também serão gravados." : "Sem áudio · para incluir os sons do computador, ative o áudio antes de iniciar o REC.";
    render();
  } catch (error) { loading = false; render(); message.hidden = false; message.textContent = error.message || "Não foi possível carregar as telas e janelas."; }
  finally { loading = false; refresh.disabled = false; }
}
refresh.addEventListener("click", load);
document.getElementById("cancel").addEventListener("click", () => api.cancel());
share.addEventListener("click", () => { if (selected && !loading) { share.disabled = true; api.select(selected.id); } });
document.addEventListener("keydown", event => { if (event.key === "Escape") api.cancel(); });
void load();
