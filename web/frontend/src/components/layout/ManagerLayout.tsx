import { useEffect, useState } from "react";
import {
  AppShell,
  Group,
  Text,
  Button,
  ActionIcon,
  Burger,
  Collapse,
  useMantineColorScheme,
  useComputedColorScheme,
  Badge,
  Avatar,
  Tooltip,
  UnstyledButton,
} from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { NavLink as RouterLink, Outlet, useLocation } from "react-router-dom";
import { session } from "../../services/storage/session";
interface NavGroup {
  title: string;
  glyph: string;
  links: [string, string][];
}
const groups: NavGroup[] = [
  {
    title: "Fluxo de QA",
    glyph: "◈",
    links: [
      ["/", "Visão geral"],
      ["/demands", "Demandas"],
      ["/test-plans", "Planos de teste"],
      ["/test-plans/templates", "Templates"],
      ["/regressao", "Regressão"],
      ["/runmanager", "RunManager"],
      ["/doc", "Doc"],
      ["/reports", "Relatórios"],
    ],
  },
  {
    title: "Cadastros",
    glyph: "☰",
    links: [
      ["/developers", "Desenvolvedores"],
      ["/testers", "Testers"],
      ["/technologies", "Tecnologias"],
      ["/modules", "Módulos"],
      ["/doc/cadastro", "Cards de documentação"],
    ],
  },
  {
    title: "Administração",
    glyph: "⚙",
    links: [
      ["/users", "Usuários"],
      ["/audit", "Histórico de alterações"],
      ["/integrations", "Integrações"],
      ["/media", "Gerenciamento de mídia"],
      ["/database", "Banco de dados"],
    ],
  },
];
const groupOf = (path: string) =>
  groups.find((group) =>
    group.links.some(([to]) =>
      to === "/" ? path === "/" : path.startsWith(to),
    ),
  )?.title;
export function ManagerLayout() {
  const [mobile, { toggle: toggleMobile, close: closeMobile }] =
    useDisclosure();
  const [desktopOpen, setDesktopOpen] = useState(true);
  const { pathname } = useLocation();
  const [open, setOpen] = useState<string[]>(() => {
    const active = groupOf(pathname);
    return active ? [active] : [groups[0].title];
  });
  useEffect(() => {
    const active = groupOf(pathname);
    if (active)
      setOpen((current) =>
        current.includes(active) ? current : [...current, active],
      );
  }, [pathname]);
  const { setColorScheme } = useMantineColorScheme();
  const scheme = useComputedColorScheme("light");
  const user = session.get();
  const toggleGroup = (title: string) =>
    setOpen((current) =>
      current.includes(title)
        ? current.filter((value) => value !== title)
        : [...current, title],
    );
  return (
    <AppShell
      header={{ height: 64 }}
      navbar={{
        width: 264,
        breakpoint: "md",
        collapsed: { mobile: !mobile, desktop: !desktopOpen },
      }}
      padding={{ base: "sm", sm: "md", lg: "xl" }}
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger
              opened={mobile}
              onClick={toggleMobile}
              hiddenFrom="md"
              size="sm"
              aria-label="Abrir menu"
            />
            <Tooltip label={desktopOpen ? "Recolher menu" : "Expandir menu"}>
              <ActionIcon
                variant="subtle"
                size="lg"
                visibleFrom="md"
                aria-label={desktopOpen ? "Recolher menu" : "Expandir menu"}
                aria-expanded={desktopOpen}
                onClick={() => setDesktopOpen((value) => !value)}
              >
                <span className="glyph">{desktopOpen ? "⤡" : "☰"}</span>
              </ActionIcon>
            </Tooltip>
            <div className="brand-mark">QA</div>
            <Text className="brand" visibleFrom="xs">
              QA Manager
            </Text>
            <Badge variant="light" visibleFrom="lg">
              Quality workspace
            </Badge>
          </Group>
          <Group gap="xs" wrap="nowrap">
            <Group gap={8} visibleFrom="sm" wrap="nowrap">
              <Avatar size={30} radius="xl" color="indigo" variant="light">
                {(user?.name || "?").slice(0, 1).toUpperCase()}
              </Avatar>
              <Text size="sm" fw={600} lineClamp={1} maw={160}>
                {user?.name}
              </Text>
            </Group>
            <Tooltip label={scheme === "dark" ? "Tema claro" : "Tema escuro"}>
              <ActionIcon
                variant="default"
                size="lg"
                aria-label="Alternar tema"
                onClick={() =>
                  setColorScheme(scheme === "dark" ? "light" : "dark")
                }
              >
                <span className="glyph">{scheme === "dark" ? "☀" : "☾"}</span>
              </ActionIcon>
            </Tooltip>
            <Button variant="subtle" size="xs" onClick={() => session.clear()}>
              Sair
            </Button>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="sm">
        <nav aria-label="Menu principal" className="nav-scroll">
          <ul className="nav-tree">
            {groups.map((group) => {
              const expanded = open.includes(group.title);
              return (
                <li key={group.title} className="nav-branch">
                  <UnstyledButton
                    className="nav-parent"
                    aria-expanded={expanded}
                    onClick={() => toggleGroup(group.title)}
                  >
                    <span className="nav-parent-glyph">{group.glyph}</span>
                    <span className="nav-parent-label">{group.title}</span>
                    <span
                      className={"nav-chevron" + (expanded ? " expanded" : "")}
                      aria-hidden
                    >
                      ›
                    </span>
                  </UnstyledButton>
                  <Collapse in={expanded}>
                    <ul className="nav-children">
                      {group.links.map(([to, label]) => (
                        <li key={to}>
                          <RouterLink
                            to={to}
                            end
                            onClick={closeMobile}
                            className={({ isActive }) =>
                              "nav-leaf" + (isActive ? " active" : "")
                            }
                          >
                            <span className="nav-bullet" aria-hidden />
                            <span>{label}</span>
                          </RouterLink>
                        </li>
                      ))}
                    </ul>
                  </Collapse>
                </li>
              );
            })}
          </ul>
        </nav>
        <Text mt="auto" pt="sm" size="xs" c="dimmed">
          QA Manager · Local
        </Text>
      </AppShell.Navbar>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
