import { Alert, Button, Group, Progress, Stack, Text } from "@mantine/core";
import type { BulkImportProgress } from "../../types/bulkImport.types";

export function BulkImportStatus({
  progress,
  running,
  stopping,
  onCancel,
}: {
  progress: BulkImportProgress;
  running: boolean;
  stopping: boolean;
  onCancel: () => void;
}) {
  const title = running
    ? "Importando todas as páginas"
    : progress.phase === "cancelled"
      ? "Importação interrompida"
      : progress.phase === "failed"
        ? "Importação parcial"
        : progress.failed
          ? "Importação finalizada com falhas"
          : "Importação concluída";
  return (
    <Alert
      color={
        running
          ? "blue"
          : progress.failed || progress.phase !== "completed"
            ? "yellow"
            : "teal"
      }
      title={title}
    >
      <Stack gap="xs">
        <Text size="sm">
          {progress.imported} importadas/sincronizadas · {progress.failed}{" "}
          falhas · {progress.pages} páginas consultadas
        </Text>
        <Progress
          value={
            progress.found ? (100 * progress.processed) / progress.found : 0
          }
          animated={running}
          aria-label="Progresso nas demandas encontradas"
        />
        <Text size="xs">
          {progress.processed} de {progress.found} demandas que atendem ao
          filtro encontradas até agora processadas.
        </Text>
        {running && (
          <Group justify="space-between">
            <Text size="sm">
              {stopping
                ? "Aguardando a operação atual terminar…"
                : progress.phase === "searching"
                  ? "Consultando próxima página…"
                  : "Importando " + progress.currentKey}
            </Text>
            <Button
              variant="default"
              size="xs"
              disabled={stopping}
              onClick={onCancel}
            >
              Interromper
            </Button>
          </Group>
        )}
        {progress.error && <Text size="sm">{progress.error}</Text>}
        {progress.failures.map((item) => (
          <Text key={item.key} size="xs">
            {item.key}: {item.message}
          </Text>
        ))}
        {progress.failed > progress.failures.length && (
          <Text size="xs">
            Exibindo as primeiras {progress.failures.length} falhas.
          </Text>
        )}
        {!running &&
          (progress.phase !== "completed" || progress.failed > 0) && (
            <Text size="xs">
              Os registros já salvos permanecem no Manager. Você pode executar a
              importação novamente para sincronizar a consulta.
            </Text>
          )}
      </Stack>
    </Alert>
  );
}
