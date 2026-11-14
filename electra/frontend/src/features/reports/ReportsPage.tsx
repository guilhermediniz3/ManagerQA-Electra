import { useState } from "react";
import {
  Button,
  Group,
  Paper,
  Stack,
  Text,
  TextInput,
  SimpleGrid,
} from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { exportApi } from "../../services/api/exportApi";
import { toApiDate } from "../../utils/dates";
import { PageHeading } from "../../components/common/PageHeading";
export function ReportsPage() {
  const [data, setData] = useState(new Date().toISOString().slice(0, 10)),
    [tester, setTester] = useState(""),
    [start, setStart] = useState(""),
    [end, setEnd] = useState(""),
    [technology, setTechnology] = useState(""),
    [developer, setDeveloper] = useState(""),
    [status, setStatus] = useState("");
  const report = useMutation({
    mutationFn: (format: "excel" | "pdf") =>
      exportApi.report(format, toApiDate(data), tester),
  });
  const sprint = useMutation({
    mutationFn: (format: "excel" | "pdf") =>
      exportApi.sprint(
        format,
        Object.fromEntries(
          Object.entries({
            dataInicio: start,
            dataFim: end,
            tecnologia: technology,
            desenvolvedor: developer,
            status,
          }).filter(([, v]) => v),
        ),
      ),
  });
  return (
    <>
      <PageHeading
        title="Relatórios"
        subtitle="Exportações compatíveis com os relatórios existentes"
      />
      <SimpleGrid cols={{ base: 1, lg: 2 }}>
        <Paper p="lg">
          <Stack>
            <Text fw={700}>Relatório diário</Text>
            <TextInput
              label="Data"
              type="date"
              value={data}
              onChange={(e) => setData(e.target.value)}
            />
            <TextInput
              label="Tester"
              value={tester}
              onChange={(e) => setTester(e.target.value)}
            />
            <Group>
              <Button
                disabled={!data}
                loading={report.isPending}
                onClick={() => report.mutate("excel")}
              >
                Excel
              </Button>
              <Button
                disabled={!data}
                variant="default"
                loading={report.isPending}
                onClick={() => report.mutate("pdf")}
              >
                PDF
              </Button>
            </Group>
          </Stack>
        </Paper>
        <Paper p="lg">
          <Stack>
            <Text fw={700}>Relatório de sprint</Text>
            <TextInput
              type="date"
              label="Data inicial"
              value={start}
              onChange={(e) => setStart(e.target.value)}
            />
            <TextInput
              type="date"
              label="Data final"
              value={end}
              onChange={(e) => setEnd(e.target.value)}
            />
            <TextInput
              label="Tecnologia"
              value={technology}
              onChange={(e) => setTechnology(e.target.value)}
            />
            <TextInput
              label="Desenvolvedor"
              value={developer}
              onChange={(e) => setDeveloper(e.target.value)}
            />
            <TextInput
              label="Status de execução local"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            />
            <Group>
              <Button
                loading={sprint.isPending}
                onClick={() => sprint.mutate("excel")}
              >
                Excel
              </Button>
              <Button
                variant="default"
                loading={sprint.isPending}
                onClick={() => sprint.mutate("pdf")}
              >
                PDF
              </Button>
            </Group>
          </Stack>
        </Paper>
      </SimpleGrid>
    </>
  );
}
