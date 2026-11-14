import { useEffect, useRef, useState } from "react";
import { Alert, Anchor, Button, Group, Modal, Progress, Stack, Switch, Text } from "@mantine/core";
import { caseMediaApi } from "../../services/api/caseMediaApi";
import { driveApi } from "../../services/api/driveApi";
import { requestScreenCapture, screenCaptureError } from "../../utils/screenCapture";

type Phase = "idle" | "starting" | "recording" | "uploading" | "saving" | "done" | "error";
export function ScenarioRecorder({caseId, scenario, previousUrl, onSaved, onClose}: {
  caseId: number; scenario: string; previousUrl?: string; onSaved: (url: string) => void; onClose: () => void;
}) {
  const [useDrive, setUseDrive] = useState(false);
  const [ready, setReady] = useState(false);
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState("");
  const [seconds, setSeconds] = useState(0);
  const [progress, setProgress] = useState(0);
  const [sound, setSound] = useState(false);
  const [blob, setBlob] = useState<Blob>();
  const [download, setDownload] = useState("");
  const [url, setUrl] = useState("");
  const [captureName, setCaptureName] = useState("");
  const [captureAudio, setCaptureAudio] = useState(false);
  const alive = useRef(true);
  const stream = useRef<MediaStream>();
  const recorder = useRef<MediaRecorder>();
  const sending = useRef(false);
  const busy = ["starting", "recording", "uploading", "saving"].includes(phase);
  useEffect(() => {
    driveApi.settings().then(s => {
      if (!alive.current) return;
      if (s.connected && s.folder) setReady(true);
      
    }).catch(() => { /* Database recording does not depend on Drive. */ });
    alive.current = true;
    return () => { alive.current = false; if (recorder.current?.state === "recording") recorder.current.stop(); stream.current?.getTracks().forEach(t => t.stop()); };
  }, []);
  useEffect(() => {
    if (!blob) return;
    const local = URL.createObjectURL(blob); setDownload(local);
    return () => URL.revokeObjectURL(local);
  }, [blob]);
  useEffect(() => {
    if (phase !== "recording") return;
    const timer = window.setInterval(() => setSeconds(s => s + 1), 1000);
    return () => clearInterval(timer);
  }, [phase]);
  useEffect(() => {
    if (!busy && !(blob && phase !== "done")) return;
    const guard = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [busy, blob, phase]);
  const send = async (video: Blob, uploaded = "") => {
    if (sending.current) return;
    sending.current = true; setError("");
    try {
      if (!useDrive) {
        setPhase("saving");
        await caseMediaApi.upload(caseId, "video", video);
        if (alive.current) { setPhase("done"); onSaved(previousUrl || ""); }
        return;
      }
      let link = uploaded;
      if (!link) {
        setPhase("uploading"); setProgress(0);
        link = (await driveApi.upload(caseId, video, setProgress)).url;
        if (!alive.current) return;
        setUrl(link);
      }
      setPhase("saving");
      await driveApi.evidence(caseId, link);
      if (!alive.current) return;
      setPhase("done"); onSaved(link);
    } catch(e) { if (alive.current) { setPhase("error"); setError(e instanceof Error ? e.message : "Não foi possível enviar a gravação."); } }
    finally { sending.current = false; }
  };
  const start = async () => {
    setError(""); setPhase("starting");
    try {
      if (useDrive && !ready) throw new Error("Conecte o Google Drive em Configurações antes de gravar.");
      const capture = await requestScreenCapture(sound);
      if (!alive.current) { capture.getTracks().forEach(t => t.stop()); return; }
      stream.current = capture;
      const videoTrack = capture.getVideoTracks()[0];
      if (!videoTrack) throw new Error("A fonte escolhida não forneceu imagem. Selecione outra tela ou janela.");
      setCaptureName(videoTrack.label || "Conteúdo selecionado");
      setCaptureAudio(capture.getAudioTracks().length > 0);
      const mimeType = ["video/webm;codecs=vp9,opus", "video/webm;codecs=vp8,opus", "video/webm", "video/mp4"].find(t => MediaRecorder.isTypeSupported(t));
      if (!mimeType) throw new Error("Este navegador não oferece um formato de gravação compatível.");
      const rec = new MediaRecorder(capture, {mimeType, videoBitsPerSecond: 1200000, audioBitsPerSecond: 64000});
      recorder.current = rec;
      const chunks: Blob[] = []; let size = 0; const started = Date.now();
      rec.ondataavailable = event => {
        if (event.data.size) { chunks.push(event.data); size += event.data.size; }
        if ((size >= 480 * 1024 * 1024 || Date.now() - started >= 30 * 60 * 1000) && rec.state === "recording") rec.stop();
      };
      rec.onerror = () => { setError("A captura foi interrompida. O trecho disponível será enviado."); if (rec.state === "recording") rec.stop(); };
      rec.onstop = () => {
        capture.getTracks().forEach(t => t.stop());
        if (!alive.current) return;
        const video = new Blob(chunks, {type: mimeType});
        if (!video.size) { setPhase("error"); setError("Nenhuma imagem foi gravada. Inicie uma nova gravação."); return; }
        setBlob(video); void send(video);
      };
      capture.getVideoTracks()[0].onended = () => { if (rec.state === "recording") rec.stop(); };
      rec.start(1000); setSeconds(0); setPhase("recording");
    } catch(e) {
      stream.current?.getTracks().forEach(t => t.stop());
      if (alive.current) { setPhase("error"); setError(screenCaptureError(e)); }
    }
  };
  return <Modal opened title={"Gravar evidência · CT " + caseId} size="lg" onClose={() => { if (!busy) onClose(); }} closeOnClickOutside={false} closeOnEscape={!busy} withCloseButton={!busy}>
    <Stack>
      <Text fw={600}>{scenario}</Text>
      {previousUrl && !blob && <Text size="sm" c="dimmed">A evidência atual será substituída somente depois do envio concluído. Você continua podendo usar URLs do Vimeo ou do Drive.</Text>}
      {!blob && !busy && <><Switch label="Salvar no Google Drive (opcional)" checked={useDrive} onChange={e => setUseDrive(e.currentTarget.checked)} />
        {useDrive && !ready && <Alert color="yellow">Conecte sua conta e configure a pasta em Configurações / Integrações / Google Drive.</Alert>}
        <Text size="sm">{useDrive ? "Destino: Google Drive" : "Destino: banco de dados · vídeo comprimido"}</Text><Switch label="Incluir áudio compartilhado (quando disponível)" checked={sound} onChange={e => setSound(e.currentTarget.checked)} />
        <Text size="sm">Ao clicar em REC, escolha Tela inteira e o monitor desejado, ou uma janela, no seletor de compartilhamento. Na web, o navegador também pode oferecer uma aba. Confirme em Compartilhar para iniciar.</Text></>}
      {error && <Alert color="red">{error}</Alert>}
      {phase === "recording" && <>
        <Alert color="red">● REC · {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, "0")} — gravação em andamento</Alert>
        <Text size="sm" fw={600}>Gravando: {captureName}</Text>
        <video aria-label="Prévia da tela em gravação" autoPlay muted playsInline ref={node => { if (node && node.srcObject !== stream.current) node.srcObject = stream.current ?? null; }} style={{width: "100%", maxHeight: 260, objectFit: "contain", background: "#101115", borderRadius: 8}} />
        <Text size="xs" c={sound && !captureAudio ? "yellow" : "dimmed"}>{captureAudio ? "Áudio compartilhado incluído." : sound ? "A fonte escolhida não disponibilizou áudio. O vídeo está sendo gravado sem som." : "Gravação sem áudio."} Ao parar, o vídeo será salvo no destino selecionado. Limite: 30 minutos ou aproximadamente 480 MB.</Text>
      </>}
      {phase === "uploading" && <><Text>Enviando gravação… {progress < 100 ? progress + "%" : "Processando no Google Drive"}</Text><Progress value={progress} animated /></>}
      {phase === "saving" && <Text>Salvando evidência no cenário…</Text>}
      {phase === "done" && <Alert color="green">Gravação salva no cenário. Se você escolheu Drive, a reprodução pode levar alguns instantes para ficar disponível.</Alert>}
      {url && <Anchor href={url} target="_blank" rel="noopener noreferrer">Abrir vídeo no Google Drive</Anchor>}
      <Group>
        {!blob && !busy && <Button color="red" disabled={useDrive && !ready} onClick={start}>● REC · Iniciar gravação</Button>}
        {phase === "starting" && <Button loading>Escolha a tela no seletor de compartilhamento</Button>}
        {phase === "recording" && <Button color="red" onClick={() => recorder.current?.stop()}>■ Parar e enviar</Button>}
        {phase === "error" && blob && <Button onClick={() => send(blob, url)}>{url ? "Tentar salvar URL novamente" : "Tentar enviar novamente"}</Button>}
        {download && <Button component="a" href={download} download={"QA-CT-" + caseId + (blob?.type.startsWith("video/mp4") ? ".mp4" : ".webm")} variant="default">Baixar gravação</Button>}
        {!busy && <Button variant="subtle" onClick={onClose}>Fechar</Button>}
      </Group>
    </Stack>
  </Modal>;
}

