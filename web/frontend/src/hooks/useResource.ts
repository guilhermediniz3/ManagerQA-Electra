import { useQuery, useQueryClient } from "@tanstack/react-query";
export function useResource<T>(
  key: readonly unknown[],
  loader: () => Promise<T>,
  enabled = true,
) {
  return useQuery({ queryKey: key, queryFn: loader, retry: false, enabled });
}
export function useRefresh() {
  const client = useQueryClient();
  return () => client.invalidateQueries();
}
