export const today = () => new Date().toLocaleDateString("pt-BR");
export const toApiDate = (value: string) =>
  value ? value.split("-").reverse().join("/") : "";
export const toInputDate = (value?: string) =>
  value ? value.split("/").reverse().join("-") : "";
