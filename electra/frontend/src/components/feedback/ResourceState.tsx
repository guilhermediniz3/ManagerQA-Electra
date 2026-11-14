import { Alert, Button, Center, Loader, Text } from "@mantine/core";
export function ResourceState({
  loading,
  error,
  empty,
  retry,
}: {
  loading?: boolean;
  error?: Error | null;
  empty?: boolean;
  retry?: () => void;
}) {
  if (loading)
    return (
      <Center p="xl">
        <Loader size="sm" />
      </Center>
    );
  if (error)
    return (
      <Alert color="red" title="Não foi possível carregar">
        {error.message}
        {retry && (
          <Button variant="subtle" onClick={retry}>
            Tentar novamente
          </Button>
        )}
      </Alert>
    );
  if (empty)
    return (
      <Center p="xl">
        <Text c="dimmed">Nenhum registro encontrado.</Text>
      </Center>
    );
  return null;
}
