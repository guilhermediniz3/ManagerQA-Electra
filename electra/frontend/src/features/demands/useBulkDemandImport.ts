import { useEffect, useRef, useState } from "react";
import { notifications } from "@mantine/notifications";
import type { BulkImportProgress } from "../../types/bulkImport.types";
import { importJiraQuery } from "../../services/imports/importJiraQuery";
import { jiraApi } from "../../services/api/jiraApi";
import { demandApi } from "../../services/api/demandApi";
import { useRefresh } from "../../hooks/useResource";

export function useBulkDemandImport() {
  const [progress, setProgress] = useState<BulkImportProgress | null>(null);
  const [stopping, setStopping] = useState(false);
  const active = useRef(false),
    cancelled = useRef(false),
    mounted = useRef(true);
  const refresh = useRefresh();
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      cancelled.current = true;
    };
  }, []);
  const start = async (query: string, statusIds: readonly string[] = []) => {
    if (active.current) return;
    active.current = true;
    cancelled.current = false;
    setStopping(false);
    try {
      const result = await importJiraQuery(
        query,
        {
          search: jiraApi.searchQuiet,
          sync: demandApi.syncQuiet,
        },
        () => cancelled.current,
        (value) => {
          if (mounted.current) setProgress(value);
        },
        statusIds,
      );
      await refresh();
      if (mounted.current)
        notifications.show({
          color:
            result.phase === "completed" && !result.failed ? "teal" : "yellow",
          title:
            result.phase === "completed"
              ? "Importação finalizada"
              : "Importação interrompida",
          message:
            result.imported +
            " demandas importadas/sincronizadas; " +
            result.failed +
            " falhas.",
        });
    } finally {
      active.current = false;
    }
  };
  return {
    progress,
    stopping,
    start,
    running: progress?.phase === "searching" || progress?.phase === "importing",
    cancel: () => {
      cancelled.current = true;
      setStopping(true);
    },
    reset: () => {
      if (!active.current) setProgress(null);
    },
  };
}
