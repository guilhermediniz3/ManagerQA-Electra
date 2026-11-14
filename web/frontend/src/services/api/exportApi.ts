import { httpClient as h } from "./httpClient";
async function download(
  url: string,
  params: Record<string, string | number>,
  filename: string,
) {
  const response = await h.get<Blob>(url, { params, responseType: "blob" });
  const link = document.createElement("a");
  const objectUrl = URL.createObjectURL(response.data);
  link.href = objectUrl;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}
export const exportApi = {
  suite: (id: number, format: "excel" | "pdf") =>
    download(
      "/relatorio-jira/" + format,
      { testSuiteId: id },
      "suite-" + id + (format === "excel" ? ".xlsx" : ".pdf"),
    ),
  report: (format: "excel" | "pdf", data: string, testerName: string) =>
    download(
      "/relatorios/" + format,
      { data, testerName },
      "relatorio." + (format === "excel" ? "xlsx" : "pdf"),
    ),
  sprint: (format: "excel" | "pdf", params: Record<string, string>) =>
    download(
      "/relatorios/sprint-" + format,
      params,
      "sprint." + (format === "excel" ? "xlsx" : "pdf"),
    ),
};
