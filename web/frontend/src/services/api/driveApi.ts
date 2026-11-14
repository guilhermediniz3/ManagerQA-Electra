import { httpClient as h } from "./httpClient";
export interface DriveSettings {
  clientId: string; hasClientSecret: boolean; folder: string; redirectUri: string; connected: boolean; authorizing: boolean; account: string;
}
export const driveApi = {
  settings: async () => (await h.get<DriveSettings>("/api/drive/settings")).data,
  save: async (input: { clientId: string; clientSecret: string; folder: string; redirectUri: string }) =>
    (await h.put<DriveSettings>("/api/drive/settings", input)).data,
  connect: async () => (await h.post<{url: string}>("/api/drive/connect")).data,
  disconnect: async () => { await h.post("/api/drive/disconnect"); },
  test: async () => (await h.post<{folderName: string}>("/api/drive/test", {}, {timeout: 150000})).data,
  upload: async (id: number, blob: Blob, progress: (value: number) => void) => {
    const data = new FormData();
    const type = blob.type.startsWith("video/mp4") ? "video/mp4" : "video/webm";
    data.append("file", new Blob([blob], {type}), "gravacao." + (type === "video/mp4" ? "mp4" : "webm"));
    return (await h.post<{url: string}>("/api/drive/cases/" + id + "/recording", data, {
      headers: {"Content-Type": undefined}, timeout: 35 * 60 * 1000, silentError: true,
      onUploadProgress: e => progress(e.total ? Math.round(e.loaded / e.total * 100) : 0),
    })).data;
  },
  evidence: async (id: number, url: string) => {
    await h.patch("/testcases/" + id + "/evidence", {url}, {silentError: true});
  },
};
