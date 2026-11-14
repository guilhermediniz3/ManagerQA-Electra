/** Keep this call synchronous with the REC click so browser activation is preserved. */
export function requestScreenCapture(audio: boolean): Promise<MediaStream> {
  if (!window.isSecureContext) {
    throw new Error("Para gravar a tela na web, abra o sistema por HTTPS ou localhost. O navegador bloqueia gravações em endereços HTTP da rede.");
  }
  if (!navigator.mediaDevices?.getDisplayMedia || !window.MediaRecorder) {
    throw new Error("Este navegador não oferece gravação de tela. Abra o sistema em um navegador compatível ou use o aplicativo instalado.");
  }
  const options: DisplayMediaStreamOptions & {
    monitorTypeSurfaces: "include";
    systemAudio: "include" | "exclude";
    surfaceSwitching: "include";
  } = {
    video: { frameRate: 20, displaySurface: "monitor" },
    audio,
    monitorTypeSurfaces: "include",
    systemAudio: audio ? "include" : "exclude",
    surfaceSwitching: "include",
  };
  return navigator.mediaDevices.getDisplayMedia(options);
}

export function screenCaptureError(error: unknown): string {
  if (error instanceof DOMException) {
    switch (error.name) {
      case "NotAllowedError":
        return "Captura cancelada ou sem permissão. Clique em REC e escolha a tela, janela ou aba no seletor de compartilhamento.";
      case "AbortError":
        return "Captura cancelada ou indisponível. Clique em REC para escolher a tela novamente.";
      case "NotReadableError":
        return "Não foi possível acessar a tela escolhida. Verifique a permissão de gravação de tela do navegador no sistema e tente selecionar outra tela ou janela.";
      case "InvalidStateError":
        return "Volte para esta aba e clique em REC novamente para abrir o seletor de compartilhamento.";
      case "NotFoundError":
        return "Nenhuma tela ou janela disponível para gravar. Abra o conteúdo do teste e tente novamente.";
      case "SecurityError":
        return "O compartilhamento de tela está bloqueado neste contexto. Abra o sistema diretamente em uma aba por HTTPS ou localhost.";
    }
  }
  return error instanceof Error ? error.message : "Não foi possível iniciar a gravação.";
}
