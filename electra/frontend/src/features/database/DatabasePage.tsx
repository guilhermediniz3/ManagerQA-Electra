import { useRef, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Group,
  List,
  Modal,
  Paper,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { useResource } from "../../hooks/useResource";
import { databaseApi } from "../../services/api/databaseApi";
import { PageHeading } from "../../components/common/PageHeading";
import { ResourceState } from "../../components/feedback/ResourceState";

/** O que cada nome de tabela quer dizer para quem não mexe no banco. */
const nomes: Record<string, string> = {
  manager_demand: "Demandas importadas",
  manager_integration: "Integrações",
  manager_audit_log: "Histórico de alterações",
  tb_user: "Usuários",
  tb_developer: "Desenvolvedores",
  tb_testerQA: "Testers",
  tb_technology: "Tecnologias",
  tb_module: "Módulos",
  tb_test_plan: "Planos de teste",
  tb_test_suite: "Suítes",
  tb_test_case: "Casos de teste",
  tb_gherkin_feature: "Gherkin salvo",
  tb_suite_template: "Templates de suíte",
  tb_template_case: "Casos dos templates",
  tb_regression: "Rodadas de regressão",
  tb_regression_item: "Módulos das rodadas",
  tb_doc_card: "Cards de documentação",
  tb_run_system: "RunManager · sistemas",
  tb_run_environment: "RunManager · ambientes",
  tb_run_version: "RunManager · versões",
  tb_run_tag: "RunManager · marcações",
  tb_run_container: "RunManager · blocos",
  tb_run_card: "RunManager · cards",
  tb_run_execution: "RunManager · execuções",
  tb_run_failure: "RunManager · falhas",
  tb_run_project_snapshot: "RunManager · leitura do projeto",
  tb_run_manual_case: "RunManager · casos manuais",
};

const legivel = (tabela: string) => nomes[tabela] ?? tabela;

/**
 * Manutenção do banco: levar os dados daqui para outra instalação, trazê-los de
 * volta, ou recomeçar um ciclo sem reconfigurar o sistema inteiro.
 *
 * As duas ações que destroem dado pedem confirmação, e a de zerar pede a
 * palavra escrita: um clique errado aqui custa o trabalho de semanas, e o botão
 * fica ao lado de um que só lê.
 */
export function DatabasePage() {
  const resumo = useResource(["database", "resumo"], databaseApi.resumo);
  const [zerando, setZerando] = useState(false);
  const [apagandoTudo, setApagandoTudo] = useState(false);
  const [confirmacao, setConfirmacao] = useState("");
  const [arquivo, setArquivo] = useState<{ nome: string; conteudo: string } | null>(null);
  const escolher = useRef<HTMLInputElement>(null);

  const exportar = useMutation({
    mutationFn: databaseApi.exportar,
    onSuccess: ({ arquivo: blob, nome }) => {
      // O download é do navegador: o servidor manda o conteúdo e o nome, e aqui
      // só se cria o clique que o browser sabe tratar.
      const endereco = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = endereco;
      link.download = nome;
      link.click();
      URL.revokeObjectURL(endereco);
      notifications.show({ color: "green", message: "Backup salvo em " + nome });
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível exportar", message: erro.message }),
  });

  const importar = useMutation({
    mutationFn: (conteudo: string) => databaseApi.importar(conteudo),
    onSuccess: ({ comandos }) => {
      setArquivo(null);
      void resumo.refetch();
      notifications.show({
        color: "green",
        message: comandos + " comando(s) aplicado(s). O banco agora é o do arquivo.",
      });
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível importar", message: erro.message }),
  });

  const zerar = useMutation({
    mutationFn: databaseApi.zerar,
    onSuccess: (apagadas) => {
      setZerando(false);
      setConfirmacao("");
      void resumo.refetch();
      const total = Object.values(apagadas).reduce((soma, n) => soma + n, 0);
      notifications.show({ color: "green", message: total + " registro(s) apagado(s)." });
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível zerar", message: erro.message }),
  });

  /**
   * A limpeza total leva as contas junto, e a sessão desta tela morre com elas.
   * Por isso a página é recarregada em vez de continuar: seguir navegando com
   * um token de um usuário que não existe mais só produziria erro atrás de erro.
   */
  const apagarTudo = useMutation({
    mutationFn: databaseApi.apagarTudo,
    onSuccess: (apagadas) => {
      const total = Object.values(apagadas).reduce((soma, n) => soma + n, 0);
      notifications.show({
        color: "green",
        message:
          total +
          " registro(s) apagado(s). Feche e abra o aplicativo: o administrador padrão é recriado ao iniciar.",
        autoClose: false,
      });
      setApagandoTudo(false);
      setConfirmacao("");
      window.setTimeout(() => window.location.reload(), 2500);
    },
    onError: (erro: Error) =>
      notifications.show({ color: "red", title: "Não foi possível apagar", message: erro.message }),
  });

  const ler = (entrada: HTMLInputElement) => {
    const escolhido = entrada.files?.[0];
    if (!escolhido) return;
    const leitor = new FileReader();
    leitor.onload = () =>
      setArquivo({ nome: escolhido.name, conteudo: String(leitor.result ?? "") });
    leitor.readAsText(escolhido, "utf-8");
    entrada.value = "";
  };

  const tabelas = resumo.data ?? [];
  const aApagar = tabelas.filter((t) => t.apagadaAoZerar && t.linhas > 0);
  const registrosAApagar = aApagar.reduce((soma, t) => soma + t.linhas, 0);
  const total = tabelas.reduce((soma, t) => soma + t.linhas, 0);

  return (
    <>
      <PageHeading
        title="Banco de dados"
        subtitle="Guardar uma cópia, trazer de volta, ou recomeçar do zero"
      >
        <Button variant="default" onClick={() => resumo.refetch()}>
          Atualizar
        </Button>
      </PageHeading>

      <SimpleGrid cols={{ base: 1, md: 2, lg: 4 }} spacing="lg" mb="lg">
        <Paper p="lg" className="surface">
          <Stack gap="sm" h="100%" justify="space-between">
            <div>
              <Title order={4}>🗄 Exportar</Title>
              <Text size="sm" c="dimmed" mt={6}>
                Baixa um arquivo <strong>.sql</strong> com tudo o que está no banco. Serve para
                guardar antes de uma mudança grande ou para levar os dados a outra máquina.
              </Text>
            </div>
            <Button loading={exportar.isPending} onClick={() => exportar.mutate()}>
              Exportar backup
            </Button>
          </Stack>
        </Paper>

        <Paper p="lg" className="surface">
          <Stack gap="sm" h="100%" justify="space-between">
            <div>
              <Title order={4}>🗄 Importar</Title>
              <Text size="sm" c="dimmed" mt={6}>
                Aplica um arquivo gerado aqui. <strong>Substitui</strong> o conteúdo atual pelo do
                arquivo — o que existe hoje e não está nele se perde.
              </Text>
              {arquivo && (
                <Badge mt="sm" variant="light">
                  {arquivo.nome}
                </Badge>
              )}
            </div>
            <Group gap="sm">
              <input
                ref={escolher}
                type="file"
                accept=".sql,text/plain"
                style={{ display: "none" }}
                onChange={(e) => ler(e.currentTarget)}
              />
              <Button variant="default" onClick={() => escolher.current?.click()}>
                Escolher arquivo…
              </Button>
              <Button
                color="orange"
                disabled={!arquivo}
                loading={importar.isPending}
                onClick={() => arquivo && importar.mutate(arquivo.conteudo)}
              >
                Importar
              </Button>
            </Group>
          </Stack>
        </Paper>

        <Paper p="lg" className="surface">
          <Stack gap="sm" h="100%" justify="space-between">
            <div>
              <Title order={4}>🗄 Zerar</Title>
              <Text size="sm" c="dimmed" mt={6}>
                Apaga o trabalho registrado e mantém usuários, integrações e cadastros. Você
                continua logado e com o Jira e a IA já configurados.
              </Text>
            </div>
            <Button color="red" variant="light" onClick={() => setZerando(true)}>
              Zerar dados
            </Button>
          </Stack>
        </Paper>

        <Paper p="lg" className="surface" style={{ borderColor: "var(--mantine-color-red-6)" }} withBorder>
          <Stack gap="sm" h="100%" justify="space-between">
            <div>
              <Title order={4} c="red">
                🗄 Apagar tudo
              </Title>
              <Text size="sm" c="dimmed" mt={6}>
                Limpa o banco inteiro: também usuários, integrações e cadastros. O sistema volta ao
                estado de instalação nova e você é desconectado.
              </Text>
            </div>
            <Button color="red" onClick={() => setApagandoTudo(true)}>
              Apagar o banco inteiro
            </Button>
          </Stack>
        </Paper>
      </SimpleGrid>

      <Alert color="gray" variant="light" mb="lg">
        O arquivo guarda os <strong>dados</strong>, não o banco inteiro: não leva usuários do
        PostgreSQL nem permissões, e o esquema das tabelas é criado pelo próprio sistema ao subir.
        Ele também carrega as credenciais das integrações, cifradas — guarde-o como material
        sigiloso.
      </Alert>

      <Paper p="lg" className="surface">
        <Group justify="space-between" mb="md" wrap="wrap">
          <Title order={4}>O que há no banco</Title>
          <Text size="sm" c="dimmed">
            {total} registro(s) em {tabelas.length} tabela(s)
          </Text>
        </Group>
        <ResourceState
          loading={resumo.isLoading}
          error={resumo.error}
          empty={!tabelas.length}
          retry={() => resumo.refetch()}
        />
        {tabelas.length > 0 && (
          <Table.ScrollContainer minWidth={420} type="native">
            <Table striped highlightOnHover verticalSpacing="xs">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Conteúdo</Table.Th>
                  <Table.Th style={{ width: 120 }}>Registros</Table.Th>
                  <Table.Th style={{ width: 150 }}>Ao zerar</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {tabelas.map((linha) => (
                  <Table.Tr key={linha.tabela}>
                    <Table.Td>
                      <Text size="sm">{legivel(linha.tabela)}</Text>
                      <Text size="xs" c="dimmed">
                        {linha.tabela}
                      </Text>
                    </Table.Td>
                    <Table.Td>{linha.linhas}</Table.Td>
                    <Table.Td>
                      {linha.apagadaAoZerar ? (
                        <Badge color="red" variant="light">
                          apagado
                        </Badge>
                      ) : (
                        <Text size="xs" c="dimmed">
                          preservado
                        </Text>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Paper>

      <Modal
        opened={apagandoTudo}
        onClose={() => {
          setApagandoTudo(false);
          setConfirmacao("");
        }}
        title="Apagar o banco inteiro"
      >
        <Stack gap="md">
          <Text size="sm">
            Serão apagados <strong>{total} registro(s)</strong> — tudo o que existe no banco,
            incluindo:
          </Text>
          <List size="sm" spacing={4}>
            <List.Item>as contas de acesso, inclusive a sua;</List.Item>
            <List.Item>as credenciais do Jira, do Vimeo e das IAs;</List.Item>
            <List.Item>desenvolvedores, testers, tecnologias e módulos;</List.Item>
            <List.Item>todo o trabalho registrado.</List.Item>
          </List>
          <Alert color="red" variant="light">
            Isto não tem volta, e um backup é a única forma de recuperar. Você será desconectado;
            ao reabrir o aplicativo, o administrador padrão é recriado e você entra com ele.
          </Alert>
          <TextInput
            label="Para confirmar, escreva APAGAR TUDO"
            value={confirmacao}
            onChange={(e) => setConfirmacao(e.currentTarget.value)}
          />
          <Group justify="flex-end" gap="sm">
            <Button
              variant="subtle"
              onClick={() => {
                setApagandoTudo(false);
                setConfirmacao("");
              }}
            >
              Cancelar
            </Button>
            <Button
              variant="default"
              onClick={() => {
                setApagandoTudo(false);
                setConfirmacao("");
                exportar.mutate();
              }}
            >
              Exportar backup antes
            </Button>
            <Button
              color="red"
              disabled={confirmacao.trim().toUpperCase() !== "APAGAR TUDO"}
              loading={apagarTudo.isPending}
              onClick={() => apagarTudo.mutate()}
            >
              Apagar tudo
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={zerando}
        onClose={() => {
          setZerando(false);
          setConfirmacao("");
        }}
        title="Zerar os dados de trabalho"
      >
        <Stack gap="md">
          <Text size="sm">
            Serão apagados <strong>{registrosAApagar} registro(s)</strong>:
          </Text>
          {aApagar.length > 0 ? (
            <List size="sm" spacing={4}>
              {aApagar.map((linha) => (
                <List.Item key={linha.tabela}>
                  {legivel(linha.tabela)} — {linha.linhas}
                </List.Item>
              ))}
            </List>
          ) : (
            <Text size="sm" c="dimmed">
              Não há nada a apagar.
            </Text>
          )}
          <Alert color="red" variant="light">
            Isto não tem volta. Exporte um backup antes se houver qualquer dúvida.
          </Alert>
          <TextInput
            label="Para confirmar, escreva ZERAR"
            value={confirmacao}
            onChange={(e) => setConfirmacao(e.currentTarget.value)}
          />
          <Group justify="flex-end" gap="sm">
            <Button
              variant="subtle"
              onClick={() => {
                setZerando(false);
                setConfirmacao("");
              }}
            >
              Cancelar
            </Button>
            <Button
              color="red"
              disabled={confirmacao.trim().toUpperCase() !== "ZERAR"}
              loading={zerar.isPending}
              onClick={() => zerar.mutate()}
            >
              Zerar
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
