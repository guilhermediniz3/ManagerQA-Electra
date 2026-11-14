# QA Manager 1.7.5 — seleção visual e prévia de gravação

- Electra: REC abre um seletor visual com prévias, abas Tela inteira e Janela,
  identificação dos monitores e confirmação Compartilhar e gravar.
- Corrigida a rejeição da própria janela do Electron causada pela barra final na origem.
- Web: REC abre o seletor nativo do navegador, com preferência por Tela inteira,
  incluindo seleção de monitor, janela ou aba quando suportado pelo navegador.
- Web e Electra: prévia ao vivo, nome da fonte escolhida e indicação do áudio efetivamente capturado.
- Mensagens em português para cancelamento, falta de permissão, tela indisponível,
  aba sem foco e acesso web em HTTP inseguro.
- Envio automático ao Drive e persistência da URL no cenário continuam após Parar e enviar.
- Falhas de upload e de persistência mantêm a gravação disponível para nova tentativa.
- A instalação continua usando o mesmo perfil e banco da versão 1.7.4.

## Instalar ou atualizar o Electra

1. Salve seu trabalho e feche o QA Manager.
2. Execute `installer/QA-Manager-Setup-1.7.5.exe` sobre a instalação existente.
3. Abra o app, entre na suíte e clique em REC para escolher o monitor ou a janela.

O instalador inclui o seletor visual completo. Não depende da correção local em app.asar.

## Web

Use o frontend atualizado e o backend que o acompanha. O pacote web compilado fica
em `F:/manager/releases/1.7.5/`. O JAR inclui a interface React na mesma origem da API.
Na web, a captura exige HTTPS ou localhost. HTTP por IP/nome da rede é bloqueado
pelo próprio navegador. O monitor específico é escolhido pelo usuário no seletor.

## Validação

Builds TypeScript/Vite dos dois projetos; autorização e cancelamento da captura;
seletor visual com múltiplos monitores e janelas simulados; captura nativa real de
uma página de teste no Electron; gravação MediaRecorder, prévia, seleção de monitor,
cancelamento, HTTP inseguro, falha de acesso à tela e reenvio com Google Drive simulado.
O upload real continua dependendo da autorização Google e da pasta configuradas.

Referências da API web:
- https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getDisplayMedia
- https://developer.chrome.com/docs/web-platform/screen-sharing-controls
