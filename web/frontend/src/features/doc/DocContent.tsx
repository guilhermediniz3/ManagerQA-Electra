import { videoEvidence } from "../../utils/videoEvidence";
import { useMemo } from "react";

/**
 * Mostra o conteúdo de um card fora do editor.
 *
 * Toda gravação vira player aqui, na exibição — não só na geração. O editor
 * guarda vídeo como `div[data-resizable-video]` vazio, e material escrito antes
 * (ou colado à mão) guarda o endereço como link comum; nos dois casos o leitor
 * quer assistir na página, não ser mandado para outra aba. Como a conversão
 * acontece na hora de exibir, card antigo passa a mostrar o vídeo sem precisar
 * ser gerado de novo.
 */
export function DocContent({ html }: { html: string }) {
  const preparado = useMemo(() => montarPlayers(html), [html]);
  return (
    <div
      className="doc-preview"
      dangerouslySetInnerHTML={{ __html: preparado }}
    />
  );
}

/** Endereço do player do Vimeo a partir de qualquer URL do Vimeo. */
export function playerDoVimeo(valor: string): string | null {
  try {
    const url = new URL(valor.trim());
    if (url.protocol !== "https:") return null;
    if (
      !["vimeo.com", "www.vimeo.com", "player.vimeo.com"].includes(url.hostname)
    )
      return null;

    const partes = url.pathname.split("/").filter(Boolean);
    const indice = partes[0] === "video" ? 1 : 0;
    const id = partes[indice];
    if (!/^\d+$/.test(id ?? "")) return null;

    const hash = url.searchParams.get("h") ?? partes[indice + 1];
    return (
      "https://player.vimeo.com/video/" +
      id +
      (hash && /^[a-zA-Z0-9]+$/.test(hash) ? "?h=" + hash : "")
    );
  } catch {
    return null;
  }
}

function criarPlayer(documento: Document, player: string, largura?: string) {
  const bloco = documento.createElement("div");
  bloco.setAttribute("data-resizable-video", "");
  bloco.setAttribute("data-src", player);

  const iframe = documento.createElement("iframe");
  iframe.className = "vimeo-player";
  iframe.src = player;
  iframe.title = "Evidência de teste no Vimeo";
  iframe.setAttribute("allow", "fullscreen; picture-in-picture");
  iframe.setAttribute("allowfullscreen", "");
  if (largura) iframe.style.maxWidth = largura + "px";
  bloco.appendChild(iframe);
  return bloco;
}

export function montarPlayers(html: string): string {
  if (!html) return "";
  const documento = new DOMParser().parseFromString(html, "text/html");

  // Restore images saved by older versions as empty editor placeholders.
  documento.querySelectorAll("div[data-resizable-image]").forEach((bloco) => {
    const src = bloco.getAttribute("data-src") ?? "";
    if (!/^(data:image\/(png|jpe?g|gif|webp);base64,|https?:\/\/)/i.test(src)) return;
    const imagem = bloco.querySelector("img") ?? documento.createElement("img");
    imagem.src = src;
    imagem.alt = imagem.alt || "";
    const largura = Number(bloco.getAttribute("data-width"));
    imagem.style.width = `${Number.isFinite(largura) && largura > 0 ? Math.min(largura, 1200) : 350}px`;
    imagem.style.maxWidth = "100%";
    imagem.style.height = "auto";
    const alinhamento = bloco.getAttribute("data-align");
    (bloco as HTMLElement).style.textAlign = alinhamento === "left" || alinhamento === "right" ? alinhamento : "center";
    if (!imagem.parentElement) bloco.appendChild(imagem);
  });

  // 1. O nó do editor: vem vazio, porque quem desenha o player ali é o editor.
  documento.querySelectorAll("div[data-resizable-video]").forEach((bloco) => {
    const src = bloco.getAttribute("data-src") ?? "";
    const video = videoEvidence(src);
    if (video?.provider === "Google Drive") {
      const link = documento.createElement("a");
      link.href = video.url; link.target = "_blank"; link.rel = "noopener noreferrer";
      link.textContent = "Abrir gravação no Google Drive (conta autorizada)";
      bloco.replaceChildren(link); return;
    }
    const player = playerDoVimeo(src);
    if (!player) {
      // Endereço que não é do Vimeo não vira iframe: o card não pode virar
      // porta para embutir qualquer coisa.
      bloco.remove();
      return;
    }
    if (bloco.querySelector("iframe")) return; // já veio pronto
    bloco.appendChild(
      criarPlayer(documento, player, bloco.getAttribute("data-width") ?? undefined)
        .firstChild as Node,
    );
  });

  // 2. Link de gravação — é o formato de material antigo e do que a IA devolve
  //    quando não segue o pedido. Vira player no lugar do link.
  documento.querySelectorAll("a[href]").forEach((link) => {
    const player = playerDoVimeo(link.getAttribute("href") ?? "");
    if (!player) return;
    const bloco = criarPlayer(documento, player);
    // Um link dentro de parágrafo com texto: o player substitui o parágrafo
    // inteiro só quando o link é tudo o que há nele.
    const pai = link.parentElement;
    if (pai && pai.tagName === "P" && (pai.textContent ?? "").trim() === (link.textContent ?? "").trim()) {
      pai.replaceWith(bloco);
    } else {
      link.replaceWith(bloco);
    }
  });

  // 3. Endereço solto, sem ser link.
  documento.querySelectorAll("p, li, td, div").forEach((elemento) => {
    if (elemento.querySelector("iframe, a, div[data-resizable-video]")) return;
    const texto = (elemento.textContent ?? "").trim();
    const video = videoEvidence(texto);
    if (video?.provider === "Google Drive") {
      const link = documento.createElement("a"); link.href = video.url;
      link.target = "_blank"; link.rel = "noopener noreferrer"; link.textContent = "Abrir gravação no Google Drive";
      elemento.replaceChildren(link); return;
    }
    const player = playerDoVimeo(texto);
    if (player) elemento.replaceWith(criarPlayer(documento, player));
  });

  return documento.body.innerHTML;
}
