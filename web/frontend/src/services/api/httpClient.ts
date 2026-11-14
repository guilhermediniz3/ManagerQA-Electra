import axios from "axios";
import { createSessionRenewal } from "../storage/sessionRenewal";
import { session } from "../storage/session";
import { notifications } from "@mantine/notifications";
declare module "axios" {
  interface AxiosRequestConfig {
    silentError?: boolean;
  }
}
export const httpClient = axios.create({
  // Sem endereço configurado, fala com a própria origem: em produção o mesmo
  // servidor entrega a interface e a API, e o app desktop escolhe a porta ao
  // iniciar. Um endereço fixo aqui fazia a interface procurar a API numa porta
  // que ninguém estava servindo.
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  timeout: 35000,
  headers: { "Content-Type": "application/json" },
});

/**
 * Quanto se espera por uma resposta de IA. O teto e do provider mais lento: a
 * IA local roda na CPU desta maquina e uma suite de um caso ja leva perto de um
 * minuto. Com o limite geral de 35 s a tela desistia no meio do caminho, dizia
 * "a operacao excedeu o tempo limite" e o servidor seguia gerando um texto que
 * ninguem mais ia receber. O mesmo valor esta no backend para o ollama.
 */
export const TEMPO_DA_IA = 15 * 60 * 1000;
const refreshClient = axios.create({
  baseURL: httpClient.defaults.baseURL,
  timeout: 15000,
});
export const renewSession = createSessionRenewal(
  session,
  async (token) =>
    (
      await refreshClient.post<{ name: string; token: string }>(
        "/auth/refresh",
        {},
        { headers: { Authorization: "Bearer " + token } },
      )
    ).data,
);
httpClient.interceptors.request.use(async (config) => {
  if (!["/auth/login", "/auth/register", "/auth/reset-local-password"].includes(config.url || "")) {
    await renewSession();
    const token = session.get()?.token;
    if (token) config.headers.Authorization = "Bearer " + token;
  }
  return config;
});
httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const authorization = error.config?.headers?.Authorization;
    if (
      status === 401 &&
      typeof authorization === "string" &&
      authorization.startsWith("Bearer ")
    )
      session.clearIfToken(authorization.slice(7));
    const fallback =
      status === 401
        ? "Sessão expirada ou credenciais inválidas."
        : status === 403
          ? "Você não tem acesso a esta operação."
          : status === 404
            ? "Registro não encontrado."
            : status === 409
              ? "Este registro conflita com outro já existente."
              : status === 400
                ? "Dados inválidos para esta operação."
                : error.code === "ECONNABORTED"
                  ? "A operação excedeu o tempo limite."
                  : status
                    ? // Houve resposta do servidor: o problema não é de conexão.
                      "O servidor recusou a operação (HTTP " + status + ")."
                    : "Não foi possível concluir a operação. Verifique a conexão.";
    const message =
      typeof error.response?.data?.message === "string"
        ? error.response.data.message
        : fallback;
    if (!error.config?.silentError)
      notifications.show({
        color: "red",
        title: "Não foi possível concluir",
        message,
      });
    return Promise.reject(new Error(message));
  },
);
