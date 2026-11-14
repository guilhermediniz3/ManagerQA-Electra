import { useState } from "react";
export const PAGE_SIZES = [10, 25, 50, 100];
export function usePaged<T>(items: T[], initialSize = 10) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(initialSize);
  const total = items.length;
  const pages = Math.max(1, Math.ceil(total / size));
  const current = Math.min(Math.max(1, page), pages);
  return {
    page: current,
    setPage,
    size,
    setSize: (value: number) => {
      setSize(value);
      setPage(1);
    },
    pages,
    total,
    from: total ? (current - 1) * size + 1 : 0,
    to: Math.min(current * size, total),
    slice: items.slice((current - 1) * size, current * size),
  };
}
