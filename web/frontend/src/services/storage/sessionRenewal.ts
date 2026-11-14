type Session = {name: string; token: string};
type Store = {
  get(): Session | null;
  replaceToken(expected: string, value: Session): void;
  clearIfToken(expected?: string): void;
};

export function expiresSoon(token: string, now = Date.now()): boolean {
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g,"+").replace(/_/g,"/")));
    const remaining = Number(payload.exp) * 1000 - now;
    return remaining > 0 && remaining <= 15 * 60 * 1000;
  } catch {return false;}
}

/** One renewal per token; late responses cannot restore a logged-out session. */
export function createSessionRenewal(store: Store, renew: (token: string) => Promise<Session>) {
  let pending: {token: string; promise: Promise<void>} | null = null;
  return async () => {
    const token = store.get()?.token;
    if(!token || !expiresSoon(token)) return;
    if(pending?.token === token) return pending.promise;
    const promise = renew(token).then(value => {
      store.replaceToken(token, value);
    }).catch(error => {
      // Network failures and provider errors do not invalidate local credentials.
      if(error.response?.status === 401) store.clearIfToken(token);
    }).finally(() => {if(pending?.token === token) pending=null;});
    pending = {token, promise};
    return promise;
  };
}
