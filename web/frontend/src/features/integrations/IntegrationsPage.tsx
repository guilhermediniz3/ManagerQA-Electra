import { SimpleGrid } from "@mantine/core";
import { useResource } from "../../hooks/useResource";
import { integrationApi } from "../../services/api/integrationApi";
import { IntegrationCard } from "./IntegrationCard";
import { PageHeading } from "../../components/common/PageHeading";
import { ResourceState } from "../../components/feedback/ResourceState";
import { GoogleDriveCard } from "./GoogleDriveCard";
import { MediaLibrary } from "./MediaLibrary";
export function IntegrationsPage() {
  const query = useResource(["integrations"], integrationApi.list);
  return (
    <>
      <PageHeading
        title="Integrações"
        subtitle="Configurações / Conexões do workspace"
      />
      <ResourceState
        loading={query.isLoading}
        error={query.error}
        retry={() => query.refetch()}
      />
      <SimpleGrid cols={{ base: 1, md: 2, xl: 3 }}>
        <GoogleDriveCard />
        {query.data?.map((item) => (
          <IntegrationCard
            key={item.provider + item.status + item.maskedSecret}
            value={item}
          />
        ))}
      </SimpleGrid>
      <MediaLibrary />
    </>
  );
}
