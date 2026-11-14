import { useMemo, useState } from "react";
import {
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import Card from "../../components/Card/Card";
import { Pager } from "../../components/common/Pager";
import { usePaged } from "../../hooks/usePaged";
import { docCardApi } from "../../services/api/docCardApi";
import { DocChat } from "./DocChat";

/**
 * Documentação para quem vai ler.
 *
 * Só cards, sem botão de editar e sem selo de situação: o que está aqui está no
 * ar, e quem chega a esta tela quer consultar, não administrar. Criar, editar e
 * desativar ficam na tela de cadastro, em lista — formato que serve melhor a
 * quem precisa varrer tudo e mexer.
 */
export function DocPage() {
  const navegar = useNavigate();
  const [busca, setBusca] = useState("");

  const cards = useQuery({ queryKey: ["doc-cards"], queryFn: docCardApi.list });

  const visiveis = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    const lista = (cards.data ?? []).filter((card) => card.active);
    if (!termo) return lista;
    return lista.filter(
      (card) =>
        card.title.toLowerCase().includes(termo) ||
        (card.description ?? "").toLowerCase().includes(termo) ||
        (card.systemModuleName ?? "").toLowerCase().includes(termo),
    );
  }, [cards.data, busca]);

  const paginado = usePaged(visiveis, 12);

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Title order={3}>Doc</Title>
          <Text size="sm" c="dimmed">
            O que o time já documentou. Clique num card para ler.
          </Text>
        </div>
        <TextInput
          placeholder="Buscar por título, descrição ou módulo…"
          value={busca}
          onChange={(e) => setBusca(e.currentTarget.value)}
          w={300}
        />
      </Group>

      <DocChat />

      <Paper p="lg" className="surface">
        {cards.isLoading ? (
          <Text size="sm" c="dimmed">
            Carregando documentação…
          </Text>
        ) : visiveis.length === 0 ? (
          <Text size="sm" c="dimmed">
            {busca
              ? "Nenhum card combina com a busca."
              : "Nenhuma documentação publicada. Crie um card no cadastro, ou gere a partir de uma suíte de teste pelo botão “Criar doc”."}
          </Text>
        ) : (
          <>
            <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }} spacing="lg">
              {paginado.slice.map((card) => (
                <div
                  key={card.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => navegar("/doc/" + card.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") navegar("/doc/" + card.id);
                  }}
                >
                  <Card
                    title={card.title}
                    description={card.description ?? ""}
                    icon={card.icon}
                  />
                </div>
              ))}
            </SimpleGrid>
            <Pager
              page={paginado.page}
              pages={paginado.pages}
              size={paginado.size}
              total={paginado.total}
              from={paginado.from}
              to={paginado.to}
              onPage={paginado.setPage}
              onSize={paginado.setSize}
              unit="cards"
            />
          </>
        )}
      </Paper>
    </Stack>
  );
}
