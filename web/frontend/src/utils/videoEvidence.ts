export function videoEvidence(value: string): { provider: "Vimeo" | "Google Drive"; embed: string; url: string } | null {
  try {
    const u = new URL(value.trim());
    if (u.protocol !== "https:" || u.username || u.password || u.port) return null;
    if (["vimeo.com", "www.vimeo.com", "player.vimeo.com"].includes(u.hostname)) {
      const m = u.pathname.match(/^\/(?:video\/|manage\/videos\/)?(\d+)(?:\/([a-zA-Z0-9]+))?\/?$/);
      if (!m) return null;
      const hash = u.searchParams.get("h") || m[2];
      return { provider: "Vimeo", url: u.href, embed: "https://player.vimeo.com/video/" + m[1] + (hash && /^[a-zA-Z0-9]+$/.test(hash) ? "?h=" + hash : "") };
    }
    if (u.hostname !== "drive.google.com") return null;
    const m = u.pathname.match(/^\/file\/d\/([a-zA-Z0-9_-]+)(?:\/(?:view|preview|edit))?\/?$/);
    const id = m?.[1] || (["/open", "/uc"].includes(u.pathname) ? u.searchParams.get("id") : null);
    if (!id || !/^[a-zA-Z0-9_-]+$/.test(id)) return null;
    const key = u.searchParams.get("resourcekey");
    return { provider: "Google Drive", url: u.href, embed: "https://drive.google.com/file/d/" + id + "/preview" + (key ? "?resourcekey=" + encodeURIComponent(key) : "") };
  } catch { return null; }
}
