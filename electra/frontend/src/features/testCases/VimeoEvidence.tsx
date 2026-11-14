import { Alert, Anchor, Stack, Text } from "@mantine/core";
import { videoEvidence } from "../../utils/videoEvidence";
export function vimeoEmbed(value: string): string | null {
  const video = videoEvidence(value);
  return video?.provider === "Vimeo" ? video.embed : null;
}
export function VimeoEvidence({ url }: { url: string }) {
  if (!url) return null;
  const video = videoEvidence(url);
  return video ? (
    <Stack>
      {video.provider === "Vimeo" && <iframe className="vimeo-player" src={video.embed} title="Evidência de teste no Vimeo" allow="fullscreen; picture-in-picture" allowFullScreen />}
      <Anchor href={video.url} target="_blank" rel="noopener noreferrer">Abrir evidência no {video.provider}</Anchor>
      {video.provider === "Google Drive" && <Text size="sm" c="dimmed">O vídeo abre no player do Drive. Entre com uma conta Google autorizada a acessar a pasta.</Text>}
    </Stack>
  ) : <Alert color="yellow">Informe uma URL HTTPS válida do Vimeo ou Google Drive.</Alert>;
}
