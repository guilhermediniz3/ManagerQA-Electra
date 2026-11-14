const key = "manager.session";
export const session = {
  get: (): { name: string; token: string } | null => {
    try {
      return JSON.parse(sessionStorage.getItem(key) || "null");
    } catch {
      return null;
    }
  },
  save: (value: { name: string; token: string }) => {
    sessionStorage.setItem(key, JSON.stringify(value));
    window.dispatchEvent(new Event("sessionchange"));
  },
  replaceToken: (expected: string, value: {name: string; token: string}) => {
    if(session.get()?.token === expected) sessionStorage.setItem(key, JSON.stringify(value));
  },
  clearIfToken: (expected?: string) => {
    if(expected && session.get()?.token === expected) session.clear();
  },
  clear: () => {
    sessionStorage.removeItem(key);
    window.dispatchEvent(new Event("sessionchange"));
  },
};
