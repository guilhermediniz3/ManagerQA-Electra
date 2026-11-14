import { Button, TextInput } from "@mantine/core";

/**
 * Acesso ao seletor de pastas do aplicativo instalado.
 *
 * Existe só quando a interface roda dentro do QA Manager desktop; no navegador
 * a função não é exposta, porque navegador nenhum entrega o caminho absoluto de
 * uma pasta — por segurança, e com razão.
 */
declare global {
  interface Window {
    managerDesktop?: {
      escolherPasta: (inicial?: string | null) => Promise<string | null>;
      abrirArquivo?: (caminho: string) => Promise<boolean>;
    };
  }
}

export const temSeletorDePasta = () =>
  typeof window !== "undefined" && typeof window.managerDesktop?.escolherPasta === "function";

/**
 * O aplicativo consegue abrir um arquivo do disco no programa padrão.
 *
 * É o que permite assistir ao vídeo da falha sem depender do Vimeo — ele está
 * gravado desde que o teste rodou.
 */
export const podeAbrirArquivo = () =>
  typeof window !== "undefined" && typeof window.managerDesktop?.abrirArquivo === "function";

export const abrirArquivo = (caminho: string) =>
  window.managerDesktop?.abrirArquivo?.(caminho) ?? Promise.resolve(false);

/**
 * Campo de caminho com "Procurar".
 *
 * Digitar caminho à mão é onde a configuração costuma falhar: uma barra a menos
 * e a leitura do projeto não acha nada, sem que fique claro por quê. No
 * aplicativo instalado o botão abre o seletor do sistema; no navegador ele não
 * aparece e o campo continua sendo digitado, com a explicação do porquê.
 */
export function SeletorDePasta({
  label,
  description,
  placeholder,
  value,
  onChange,
}: {
  label: string;
  description?: string;
  placeholder?: string;
  value: string;
  onChange: (caminho: string) => void;
}) {
  const disponivel = temSeletorDePasta();

  return (
    <TextInput
      label={label}
      description={
        disponivel
          ? description
          : (description ? description + " " : "") +
            "No navegador o caminho é digitado; o botão de procurar existe no aplicativo instalado."
      }
      placeholder={placeholder}
      value={value}
      onChange={(e) => onChange(e.currentTarget.value)}
      rightSectionWidth={disponivel ? 104 : undefined}
      rightSection={
        disponivel ? (
          <Button
            size="compact-sm"
            variant="light"
            onClick={async () => {
              const escolhido = await window.managerDesktop!.escolherPasta(value || null);
              if (escolhido) onChange(escolhido);
            }}
          >
            Procurar…
          </Button>
        ) : undefined
      }
    />
  );
}
