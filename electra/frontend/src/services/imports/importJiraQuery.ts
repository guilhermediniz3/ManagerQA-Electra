import type {
  BulkImportPorts,
  BulkImportProgress,
} from "../../types/bulkImport.types";

/** Reuses the local sync operation. Never executes a Jira transition. */
export async function importJiraQuery(
  jql: string,
  ports: BulkImportPorts,
  shouldStop: () => boolean,
  onProgress: (progress: BulkImportProgress) => void,
  statusIds: readonly string[] = [],
): Promise<BulkImportProgress> {
  let progress: BulkImportProgress = {
    phase: "searching",
    pages: 0,
    found: 0,
    processed: 0,
    imported: 0,
    failed: 0,
    failures: [],
  };
  const emit = (patch: Partial<BulkImportProgress>) => {
    progress = { ...progress, ...patch };
    onProgress({ ...progress, failures: [...progress.failures] });
    return progress;
  };
  const selectedStatuses = new Set(statusIds);
  const keys = new Set<string>();
  const cursors = new Set<string>();
  let cursor: string | undefined;
  let consecutiveFailures = 0;
  try {
    do {
      if (shouldStop())
        return emit({ phase: "cancelled", currentKey: undefined });
      emit({ phase: "searching", currentKey: undefined });
      const page = await ports.search(jql, cursor);
      if (shouldStop()) return emit({ phase: "cancelled" });
      if (!Array.isArray(page.issues))
        throw new Error("O Jira retornou uma página inválida.");
      const pending = page.issues.filter((issue) => {
        if (
          selectedStatuses.size &&
          !selectedStatuses.has(issue.fields?.status?.id)
        )
          return false;
        if (keys.has(issue.key)) return false;
        keys.add(issue.key);
        return true;
      });
      emit({ pages: progress.pages + 1, found: keys.size });
      for (const issue of pending) {
        if (shouldStop())
          return emit({ phase: "cancelled", currentKey: undefined });
        emit({ phase: "importing", currentKey: issue.key });
        try {
          await ports.sync(issue.key);
          consecutiveFailures = 0;
          emit({
            imported: progress.imported + 1,
            processed: progress.processed + 1,
          });
        } catch (error) {
          consecutiveFailures++;
          const message =
            error instanceof Error
              ? error.message
              : "Não foi possível importar a demanda.";
          emit({
            failed: progress.failed + 1,
            processed: progress.processed + 1,
            failures: [...progress.failures, { key: issue.key, message }].slice(
              0,
              10,
            ),
          });
          if (consecutiveFailures >= 3)
            throw new Error(
              "Três importações seguidas falharam. Confira a conexão e tente novamente; os registros já salvos foram preservados.",
            );
        }
      }
      if (shouldStop())
        return emit({ phase: "cancelled", currentKey: undefined });
      const next = page.isLast === true ? undefined : page.nextPageToken;
      if (!next) {
        if (page.isLast === false)
          throw new Error(
            "O Jira não informou como consultar a próxima página. A importação ficou parcial.",
          );
        return emit({ phase: "completed", currentKey: undefined });
      }
      if (cursors.has(next) || page.issues.length === 0)
        throw new Error(
          "A paginação do Jira não avançou. A importação ficou parcial; os registros salvos foram preservados.",
        );
      cursors.add(next);
      cursor = next;
    } while (true);
  } catch (error) {
    return emit({
      phase: shouldStop() ? "cancelled" : "failed",
      currentKey: undefined,
      error:
        error instanceof Error
          ? error.message
          : "A importação foi interrompida.",
    });
  }
}
