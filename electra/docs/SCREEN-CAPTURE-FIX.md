# Captura de tela no Electron — correção de 26/10/2026

## Causa confirmada

O Electron 33.4.11 informa `securityOrigin` como `http://127.0.0.1:porta/`.
A comparação literal com o endereço sem a barra final rejeitava a própria janela,
antes de qualquer seleção, com `AbortError: Error starting capture`.
A reprodução anterior está em `drive-validation/capture-native/probe.json`.

A autorização agora compara origens normalizadas com `URL.origin`, mantendo a
checagem da janela principal, do gesto de usuário e da solicitação de vídeo.
As recusas, escolhas e falhas são registradas com o prefixo `[capture]` no
`manager.log` do perfil do aplicativo. A seleção continua sendo obrigatória.

## Seletor visual

O REC abre uma janela modal com abas Tela inteira e Janela, miniaturas, identificação
do monitor principal, resolução, atualização da lista e confirmação explícita.
O preload isolado expõe apenas listar, selecionar e cancelar; o processo principal
valida o remetente e aceita apenas fontes efetivamente oferecidas naquele seletor.
Fechar a janela, Cancelar ou Escape cancela a solicitação.

## Validação

- `node --test desktop/scripts/test-screen-capture.cjs`: 7 testes de autorização,
  cancelamento, erros, mudança de origem e solicitações concorrentes.
- `desktop/node_modules/electron/dist/electron.exe desktop/scripts/test-native-screen-capture.cjs`:
  getDisplayMedia e MediaRecorder reais do Electron, gravando somente uma página de teste.
- `desktop/node_modules/electron/dist/electron.exe desktop/scripts/test-capture-picker.cjs`:
  11 verificações do seletor com duas telas e três janelas simuladas, incluindo títulos
  tratados como texto, cancelamento, recuperação de erro, lista vazia e seleção inválida.
  O segundo argumento opcional é um app.asar para validar o pacote instalado.
- Testes de gravação/reenvio existentes em `desktop/scripts/test-drive-recording.cjs`.
- Capturas de layout com dados fictícios: `drive-validation/capture-picker/`.

## Aplicação local

O app.asar da instalação local foi atualizado com backup e verificação SHA-256.
Os detalhes estão em `installer/capture-picker-1.7.4/patch.json`.
O banco, as configurações e o runtime não foram alterados.
O código-fonte e a lista de arquivos do electron-builder também foram atualizados.
A atualização 1.7.5 inclui o seletor visual e as melhorias web no instalador completo.
Instaladores anteriores à 1.7.5 não contêm esta funcionalidade.

