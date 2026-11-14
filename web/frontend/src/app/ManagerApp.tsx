import {
  Component,
  useEffect,
  useState,
  type ReactNode,
  lazy,
  Suspense,
} from "react";
import { MantineProvider, Alert, Button } from "@mantine/core";
import { Notifications } from "@mantine/notifications";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { theme } from "./theme/theme";
import { renewSession } from "../services/api/httpClient";
import { session } from "../services/storage/session";
import { ManagerLayout } from "../components/layout/ManagerLayout";
import { LoginPage } from "../features/auth/LoginPage";
const DocAdminPage = lazy(() =>
  import("../features/doc/DocAdminPage").then((m) => ({
    default: m.DocAdminPage,
  })),
);
const DocReaderPage = lazy(() =>
  import("../features/doc/DocReaderPage").then((m) => ({
    default: m.DocReaderPage,
  })),
);
const DocPage = lazy(() =>
  import("../features/doc/DocPage").then((m) => ({ default: m.DocPage })),
);
const RunManagerPage = lazy(() =>
  import("../features/runmanager/RunManagerPage").then((m) => ({
    default: m.RunManagerPage,
  })),
);
const RegressionPage = lazy(() =>
  import("../features/regression/RegressionPage").then((m) => ({
    default: m.RegressionPage,
  })),
);
const RegressionDetailPage = lazy(() =>
  import("../features/regression/RegressionDetailPage").then((m) => ({
    default: m.RegressionDetailPage,
  })),
);
const TemplatesPage = lazy(() =>
  import("../features/suiteTemplates/TemplatesPage").then((m) => ({
    default: m.TemplatesPage,
  })),
);
const DatabasePage = lazy(() =>
  import("../features/database/DatabasePage").then((m) => ({
    default: m.DatabasePage,
  })),
);
const AuditPage = lazy(() =>
  import("../features/audit/AuditPage").then((m) => ({ default: m.AuditPage })),
);
const UsersPage = lazy(() =>
  import("../features/users/UsersPage").then((m) => ({ default: m.UsersPage })),
);
const DashboardPage = lazy(() =>
  import("../features/dashboard/DashboardPage").then((m) => ({
    default: m.DashboardPage,
  })),
);
const CatalogPage = lazy(() =>
  import("../features/catalog/CatalogPage").then((m) => ({
    default: m.CatalogPage,
  })),
);
const IntegrationsPage = lazy(() =>
  import("../features/integrations/IntegrationsPage").then((m) => ({
    default: m.IntegrationsPage,
  })),
);
const MediaLibrary = lazy(() => import("../features/integrations/MediaLibrary").then(m=>({default:m.MediaLibrary})));
const DemandsPage = lazy(() =>
  import("../features/demands/DemandsPage").then((m) => ({
    default: m.DemandsPage,
  })),
);
const DemandDetailsPage = lazy(() =>
  import("../features/demands/DemandDetailsPage").then((m) => ({
    default: m.DemandDetailsPage,
  })),
);
const TestPlansPage = lazy(() =>
  import("../features/testPlans/TestPlansPage").then((m) => ({
    default: m.TestPlansPage,
  })),
);
const TestPlanDetailsPage = lazy(() =>
  import("../features/testPlans/TestPlanDetailsPage").then((m) => ({
    default: m.TestPlanDetailsPage,
  })),
);
const ReportsPage = lazy(() =>
  import("../features/reports/ReportsPage").then((m) => ({
    default: m.ReportsPage,
  })),
);
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false, staleTime: 15000 },
  },
});
class ErrorBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <Alert color="red" m="xl" title="Não foi possível exibir esta tela">
        <Button onClick={() => window.location.reload()}>Recarregar</Button>
      </Alert>
    ) : (
      this.props.children
    );
  }
}
export function ManagerApp() {
  const [user, setUser] = useState(session.get());
  useEffect(() => {
    // No app desktop a janela minimizada fica "hidden": sem esta exceção a
    // renovação para e a sessão expira enquanto o app segue aberto. Numa aba de
    // navegador o guard continua valendo, para não renovar em aba de fundo.
    const desktop = /\bElectron\//.test(navigator.userAgent);
    const renew = () => {
      if (desktop || document.visibilityState === "visible")
        void renewSession();
    };
    const timer = window.setInterval(renew, 60000);
    window.addEventListener("focus", renew);
    document.addEventListener("visibilitychange", renew);
    renew();
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", renew);
      document.removeEventListener("visibilitychange", renew);
    };
  }, []);
  useEffect(() => {
    const change = () => {
      queryClient.clear();
      setUser(session.get());
    };
    window.addEventListener("sessionchange", change);
    return () => window.removeEventListener("sessionchange", change);
  }, []);
  return (
    <MantineProvider theme={theme} defaultColorScheme="auto">
      <Notifications position="top-right" />
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          {!user ? (
            <LoginPage />
          ) : (
            <BrowserRouter>
              <Suspense fallback={<Alert m="lg">Carregando página…</Alert>}>
                <Routes>
                  <Route element={<ManagerLayout />}>
                    <Route index element={<DashboardPage />} />
                    <Route path="demands" element={<DemandsPage />} />
                    <Route
                      path="demands/:key"
                      element={<DemandDetailsPage />}
                    />
                    <Route path="test-plans" element={<TestPlansPage />} />
                    <Route
                      path="test-plans/templates"
                      element={<TemplatesPage />}
                    />
                    <Route
                      path="test-plans/:id"
                      element={<TestPlanDetailsPage />}
                    />
                    <Route
                      path="developers"
                      element={
                        <CatalogPage
                          key="developers"
                          kind="developers"
                          title="Desenvolvedores"
                        />
                      }
                    />
                    <Route
                      path="testers"
                      element={
                        <CatalogPage
                          key="testers"
                          kind="testers"
                          title="Testers"
                        />
                      }
                    />
                    <Route
                      path="technologies"
                      element={
                        <CatalogPage
                          key="technologies"
                          kind="technologies"
                          title="Tecnologias"
                        />
                      }
                    />
                    <Route
                      path="modules"
                      element={
                        <CatalogPage
                          key="modules"
                          kind="modules"
                          title="Módulos"
                        />
                      }
                    />
                    <Route path="runmanager" element={<RunManagerPage />} />
                    <Route path="regressao" element={<RegressionPage />} />
                    <Route
                      path="regressao/:id"
                      element={<RegressionDetailPage />}
                    />
                    <Route path="integrations" element={<IntegrationsPage />} />
                    <Route path="media" element={<MediaLibrary />} />
                    <Route path="doc" element={<DocPage />} />
                    <Route
                      path="doc/cadastro"
                      element={<DocAdminPage />}
                    />
                    <Route path="doc/:id" element={<DocReaderPage />} />
                    <Route path="reports" element={<ReportsPage />} />
                    <Route path="users" element={<UsersPage />} />
                    <Route path="audit" element={<AuditPage />} />
                    <Route path="database" element={<DatabasePage />} />
                    <Route
                      path="*"
                      element={
                        <Alert title="Página não encontrada">
                          Use o menu para continuar.
                        </Alert>
                      }
                    />
                  </Route>
                </Routes>
              </Suspense>
            </BrowserRouter>
          )}
        </QueryClientProvider>
      </ErrorBoundary>
    </MantineProvider>
  );
}
