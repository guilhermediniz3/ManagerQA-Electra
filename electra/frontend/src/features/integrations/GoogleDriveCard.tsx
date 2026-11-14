import { useEffect, useState } from "react";
import { Alert, Anchor, Badge, Button, Card, Group, PasswordInput, Stack, Text, TextInput } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { driveApi, type DriveSettings } from "../../services/api/driveApi";
import { httpClient } from "../../services/api/httpClient";

export function GoogleDriveCard() {
  const [settings, setSettings] = useState<DriveSettings>();
  const [form, setForm] = useState({clientId: "", clientSecret: "", folder: "", redirectUri: ""});
  const [busy, setBusy] = useState(false);
  const [authorizationUrl, setAuthorizationUrl] = useState("");
  const [connecting, setConnecting] = useState(false);
  const [failure, setFailure] = useState("");
  const load = async (fill = false) => {
    const s = await driveApi.settings(); setSettings(s);
    if (fill) setForm({clientId: s.clientId, clientSecret: "", folder: s.folder,
      redirectUri: s.redirectUri || new URL("/api/drive/callback", new URL(httpClient.defaults.baseURL || "/", window.location.origin)).href});
    if (s.connected && !s.authorizing) { setConnecting(false); setAuthorizationUrl(""); }
  };
  useEffect(() => { load(true).catch(e => setFailure(e.message)); }, []);
  useEffect(() => {
    if (!connecting) return;
    const until = Date.now() + 10 * 60 * 1000;
    const timer = window.setInterval(() => {
      if (Date.now() > until) { setConnecting(false); return; }
      load().catch(() => setConnecting(false));
    }, 4000);
    return () => clearInterval(timer);
  }, [connecting]);
  const action = async (fn: () => Promise<void>) => {
    setBusy(true); setFailure("");
    try { await fn(); } catch(e) { setFailure(e instanceof Error ? e.message : "Não foi possível concluir."); }
    finally { setBusy(false); }
  };
  const save = async () => {
    const s = await driveApi.save(form); setSettings(s);
    setForm(f => ({...f, folder: s.folder, clientSecret: ""}));
  };
  return <Card withBorder radius="md" p="lg">
    <Stack>
      <Group justify="space-between"><Text fw={700}>Google Drive · Gravações</Text><Badge color={settings?.connected ? "green" : "gray"}>{settings?.connected ? "Conectado" : "Não conectado"}</Badge></Group>
      <Text size="sm">As gravações dos cenários serão enviadas para esta pasta. A conexão pertence ao seu usuário do QA Manager.</Text>
      <TextInput label="Pasta do Google Drive" placeholder="https://drive.google.com/drive/folders/..." value={form.folder} onChange={e => setForm({...form, folder: e.currentTarget.value})} disabled={busy} />
      <Text size="xs" c="dimmed">Compartilhe a pasta com a equipe como leitores. Os vídeos herdam o acesso da pasta; o ManagerQA não os torna públicos.</Text>
      <details><summary>Configurar acesso Google (primeira conexão)</summary><Stack mt="sm">
        <Text size="sm">No Google Cloud da empresa, habilite a API Google Drive e crie um cliente OAuth do tipo Aplicativo da Web. Cadastre a URL de retorno abaixo. Use público interno da organização quando disponível.</Text>
        <Anchor href="https://console.cloud.google.com/apis/credentials" target="_blank" rel="noopener noreferrer">Abrir credenciais do Google Cloud</Anchor>
        <TextInput label="Client ID OAuth" value={form.clientId} onChange={e => setForm({...form, clientId: e.currentTarget.value})} disabled={busy} />
        <PasswordInput label="Client secret" placeholder={settings?.hasClientSecret ? "Já salvo. Deixe vazio para manter." : "Segredo do cliente OAuth"} value={form.clientSecret} onChange={e => setForm({...form, clientSecret: e.currentTarget.value})} disabled={busy} autoComplete="new-password" />
        <TextInput label="URL de retorno autorizada" value={form.redirectUri} onChange={e => setForm({...form, redirectUri: e.currentTarget.value})} disabled={busy} />
        <Text size="xs">O Google pede acesso ao Drive para enviar a uma pasta existente indicada pelo link. O administrador do Workspace pode precisar liberar o aplicativo. Na versão web, a gravação exige HTTPS ou localhost.</Text>
      </Stack></details>
      {settings?.account && <Text size="sm">Conta: {settings.account}</Text>}
      {failure && <Alert color="red">{failure}</Alert>}
      {connecting && <Alert>Conclua a autorização na aba do Google e volte aqui. A conexão será atualizada automaticamente.</Alert>}
      {authorizationUrl && <Anchor href={authorizationUrl} target="_blank" rel="noopener noreferrer">Continuar autorização no Google (se a aba não abriu)</Anchor>}
      <Group>
        <Button variant="light" disabled={busy} onClick={() => action(async () => { await save(); notifications.show({message: "Configuração do Drive salva.", color: "green"}); })}>Salvar pasta e configuração</Button>
        <Button disabled={busy} onClick={() => action(async () => { await save(); const {url} = await driveApi.connect(); setAuthorizationUrl(url); window.open(url, "_blank", "noopener,noreferrer"); setConnecting(true); })}>Conectar conta Google</Button>
        <Button variant="default" disabled={busy || !settings?.connected} onClick={() => action(async () => { await save(); const r = await driveApi.test(); notifications.show({message: "Pasta disponível: " + r.folderName, color: "green"}); })}>Testar pasta</Button>
        {settings?.connected && <Button variant="subtle" color="red" disabled={busy} onClick={() => action(async () => { await driveApi.disconnect(); await load(); })}>Desconectar</Button>}
      </Group>
    </Stack>
  </Card>;
}
