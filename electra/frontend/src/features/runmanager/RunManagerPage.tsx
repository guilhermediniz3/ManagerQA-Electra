import { useState } from "react";
import { Group, Stack, Switch, Tabs, Text, Title } from "@mantine/core";
import { RunPanelTab } from "./RunPanelTab";
import { RunCasesTab } from "./RunCasesTab";
import { RunHistoryTab } from "./RunHistoryTab";
import { RunMetricsTab } from "./RunMetricsTab";
import { RunFailuresTab } from "./RunFailuresTab";
import { RunCatalogTab } from "./RunCatalogTab";
import "./runmanager.css";

/**
 * O RunManager dentro do QA Manager.
 *
 * As abas são as mesmas do painel da nuvem, com duas diferenças que o documento
 * pediu: não há verificação de documentação, e não há recorte por usuário — a
 * visão é do time.
 *
 * O modo de edição é um interruptor em vez de uma tela separada. Quem cadastra
 * um card quer vê-lo no lugar em que ele vai aparecer, e alternar entre duas
 * telas para conferir isso é trabalho que a tela pode poupar.
 */
export function RunManagerPage() {
  const [aba, setAba] = useState<string | null>("disparo");
  const [modoEdicao, setModoEdicao] = useState(false);

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Title order={2}>RunManager</Title>
          <Text size="sm" c="dimmed">
            Disparo de automação, casos por módulo, falhas e métricas.
          </Text>
        </div>
        {aba === "disparo" && (
          <Switch
            label="Modo de edição"
            description="Mostra o que está inativo e libera criar, editar e excluir."
            checked={modoEdicao}
            onChange={(e) => setModoEdicao(e.currentTarget.checked)}
          />
        )}
      </Group>

      <Tabs value={aba} onChange={setAba} keepMounted={false}>
        <Tabs.List>
          <Tabs.Tab value="disparo">Disparar</Tabs.Tab>
          <Tabs.Tab value="casos">Casos de teste</Tabs.Tab>
          <Tabs.Tab value="historico">Histórico</Tabs.Tab>
          <Tabs.Tab value="metricas">Métricas</Tabs.Tab>
          <Tabs.Tab value="falhas">Falhas &amp; Jira</Tabs.Tab>
          <Tabs.Tab value="cadastro">Cadastro</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="disparo" pt="lg">
          <RunPanelTab modoEdicao={modoEdicao} />
        </Tabs.Panel>
        <Tabs.Panel value="casos" pt="lg">
          <RunCasesTab />
        </Tabs.Panel>
        <Tabs.Panel value="historico" pt="lg">
          <RunHistoryTab />
        </Tabs.Panel>
        <Tabs.Panel value="metricas" pt="lg">
          <RunMetricsTab />
        </Tabs.Panel>
        <Tabs.Panel value="falhas" pt="lg">
          <RunFailuresTab />
        </Tabs.Panel>
        <Tabs.Panel value="cadastro" pt="lg">
          <RunCatalogTab />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  );
}
