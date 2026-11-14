import { httpClient as h } from "./httpClient";
import type {
  Integration,
  IntegrationInput,
  Provider,
} from "../../types/integration.types";
export const integrationApi = {
  list: async () => (await h.get<Integration[]>("/api/integrations")).data,
  save: async (provider: Provider, input: IntegrationInput) =>
    (await h.put<Integration>("/api/integrations/" + provider, input)).data,
  test: async (provider: Provider) =>
    (
      await h.post<Integration>(
        "/api/integrations/" + provider + "/test",
        {},
        { timeout: 100000 },
      )
    ).data,
};
