# QA Manager — Electron e Web

Fontes da versão **1.8.8** do QA Manager, com as duas edições do projeto:

| Pasta | Conteúdo |
| --- | --- |
| `electra/backend` | API Spring Boot / Java 21 usada pelo desktop |
| `electra/frontend` | Interface React / TypeScript / Vite usada pelo desktop |
| `electra/desktop` | Aplicativo Electron, captura de tela e scripts do instalador |
| `web/backend` | Backend da edição web |
| `web/frontend` | Frontend da edição web |

As duas cópias são mantidas separadas para preservar a organização dos projetos originais. Consulte [as notas da versão](RELEASE-1.8.8.md).

## Funcionalidades

- Planos, suítes e cenários de teste; resultados **Falhou** e **Aprovado**.
- Texto e prints intercalados nos resultados esperado e obtido.
- Gravação de tela e upload de vídeo no PostgreSQL ou no Google Drive.
- Biblioteca de mídia com visualização e limpeza das evidências locais.
- Integração com Jira: relatórios no comentário, imagens na ordem do texto e vídeo no corpo.
- Integrações de IA e documentação dos testes.

## Configuração local

Pré-requisitos: Java 21, Maven 3.9+ (ou o wrapper incluído), Node.js 22 e PostgreSQL.

1. Escolha `web` ou `electra` e copie `.env.example` para `.env` nessa pasta.
2. Crie um banco PostgreSQL vazio e configure `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` e `INTEGRATION_MASTER_KEY`. Para um banco novo, mantenha `DB_DDL_AUTO=update`.
3. Gere os segredos localmente. Por exemplo, execute duas vezes:

   ```sh
   node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
   ```

   Use um valor para `JWT_SECRET` e outro para `INTEGRATION_MASTER_KEY`. A chave de integração precisa de 32 bytes em Base64.
4. Na pasta `backend`, execute `mvn spring-boot:run`. A configuração carrega o `.env` da pasta do projeto.
5. Na pasta `frontend`, copie `.env.example` para `.env`, execute `npm ci` e `npm run dev`.
6. Acesse `http://localhost:5173` e use **Criar conta local**.

Não existe conta pessoal nem senha inicial gravada nesta publicação. Opcionalmente, configure `SEED_ADMIN_EMAIL` e `SEED_ADMIN_PASSWORD` no ambiente para criar uma conta inicial. Credenciais de Jira, Drive e IA são configuradas na aplicação e não estão no repositório.

Para o frontend compilado, use `npm run build`. Configure `VITE_API_BASE_URL` antes do build se a API estiver em outro endereço. O backend aceita as origens definidas em `CORS_ORIGINS`.

## Web com Docker

Dentro de `web`, configure o `.env` e execute:

```sh
docker compose up --build
```

Frontend: `http://localhost:5173`. API: `http://localhost:8081`. O PostgreSQL usa volume persistente e as portas do exemplo ficam vinculadas a `127.0.0.1`.

A recuperação simplificada de senha é destinada ao Electron ou ao backend acessado diretamente por localhost. Acesso pela rede/proxy não utiliza esse fluxo local.

## Instalador Electron

1. Execute `npm ci` em `electra/frontend` e em `electra/desktop`.
2. Prepare `electra/desktop/resources` localmente com `jre`, `postgres` e um cluster PostgreSQL vazio em `pgdata-seed`.
3. Copie `desktop/config/app-config.example.json` para `desktop/resources/app-config.json` e preencha os valores locais.
4. Os recursos de IA local (`ollama` e `ollama-models`) são opcionais. Nenhum modelo, banco ou runtime foi incluído no Git.
5. No PowerShell, execute:

   ```powershell
   cd electra/desktop/scripts
   ./build-all.ps1
   ```

O instalador é gerado em `electra/installer`. Para preparar sem instalador, use `./build-all.ps1 -NoInstaller`.

Use um banco seed vazio para gerar uma distribuição. Bancos reais e arquivos locais de configuração não devem ser versionados.

## Validação

```sh
cd web/backend
mvn test
cd ../frontend
npm ci
npm run build
```

Os mesmos comandos podem ser usados dentro de `electra`. Os scripts de interface em `electra/desktop/scripts` usam Electron no Windows e dados sintéticos. As configurações Docker foram preparadas sem credenciais; a execução dos contêineres precisa de Docker instalado.
