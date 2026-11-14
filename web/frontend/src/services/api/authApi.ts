import { httpClient as h } from "./httpClient";
export const authApi = {
  resetLocalPassword: async (email:string,password:string,confirmation:string) =>
    (await h.post<{message:string}>("/auth/reset-local-password",{email,password,confirmation})).data,
  login: async (email: string, password: string) =>
    (
      await h.post<{ name: string; token: string }>("/auth/login", {
        email,
        password,
      })
    ).data,
  register: async (name: string, email: string, password: string) =>
    (
      await h.post<{ name: string; token: string }>("/auth/register", {
        name,
        email,
        password,
      })
    ).data,
};
