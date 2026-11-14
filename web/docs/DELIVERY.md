# Manager QA — entrega local

Projeto: `F:\manager`. Implementação feita sobre o projeto existente, sem restaurar o checkout nem extrair o RAR por cima dos arquivos. Alterações locais preexistentes foram preservadas. Não houve push, PR ou deploy.

## 1. Mantido

A hierarquia `TestPlan → TestSuite → TestCaseEntity`, cadastros de desenvolvedores, testers, tecnologias e módulos, usuários, clone de suíte, evidências Vimeo e os serviços de exportação XLSX/PDF. Os casos manuais e de IA usam a mesma entidade. O Excel mantém títulos, células mescladas, colunas, estilos e bordas do relatório anterior.

## 2. Refatorado

Autenticação JWT com endpoints privados protegidos, BCrypt para senhas de usuários, tratamento global de erros, contratos de DTOs, validação de relacionamentos e edição de planos/suítes sem mover filhos entre pais inadvertidamente. O frontend ativo foi substituído por uma estrutura React/TypeScript/Mantine; o código antigo continua como referência, fora do ponto de entrada ativo.

## 3. Criado

AppShell com tema claro/escuro persistente; demandas e detalhe; integrações configuráveis; importação de usuários Jira; geração de CT por quatro providers de IA com prévia editável e seleção; dashboard e mapa de módulos; histórico de criação, alteração e exclusão com usuário, e-mail, tipo, ID e horário. O log acompanha a transação e não armazena senhas, tokens, descrições ou snapshots dos registros. Operações sem sessão autenticada, como cadastro público, aparecem como “Sistema / cadastro público”.

## 4. Banco

Foi preparado um PostgreSQL portátil isolado em `.runtime`, com banco `manager` em `127.0.0.1:5432`. Nenhum banco empresarial foi restaurado ou alterado. `.env` e `.runtime` são ignorados pelo Git.

As migrações aditivas em `docs/migrations/001-manager-integrations.sql` e `002-manager-audit.sql` acrescentam identificadores de usuários Jira, configurações criptografadas, snapshots locais de demandas e auditoria. A hierarquia de testes permanece nas tabelas existentes. Para um banco antigo, faça backup e aplique as migrações em ordem antes de usar `DB_DDL_AUTO=validate`. O banco novo de desenvolvimento usa `update`; não use essa configuração indiscriminadamente em banco empresarial.

Credenciais de integrações e senhas de ambientes de teste são criptografadas com AES-GCM usando `INTEGRATION_MASTER_KEY`. Guarde essa chave com o backup: trocá-la sem migração impede decifrar registros anteriores. Credenciais antigas de planos ainda podem ser lidas; são criptografadas quando o plano é salvo. Senhas antigas de usuários que estavam em texto puro precisam de redefinição via cadastro de usuários para usar BCrypt. Os relatórios preservam os campos de credenciais de ambiente presentes no layout legado; os JSONs não devolvem as senhas.

## 5. Principais arquivos criados

- `backend/src/main/java/com/tester/integration/`: configuração, criptografia, transporte HTTP, Jira e IA.
- `backend/src/main/java/com/tester/demand/`: vínculo com Jira e metadados locais.
- `backend/src/main/java/com/tester/dashboard/`: indicadores e filtros.
- `backend/src/main/java/com/tester/audit/`: persistência transacional e consulta de eventos.
- `backend/src/test/`: testes de autenticação, hierarquia, Excel, criptografia, auditoria, Jira, IA e datas.
- `frontend/src/app`, `components`, `features`, `types`, `services/api`, `services/storage`, `hooks`, `utils`.
- `.env.example`, `scripts/` e documentação de migrações.

## 6. Principais arquivos modificados

`backend/pom.xml`, `application.properties`, segurança, `AuthController`, `UserService`, serviços e DTOs de plano/suíte/caso, entidades relacionadas e proteções de nulos nos relatórios. No frontend: `package.json`, lockfile npm, `main.tsx`, `tsconfig.json`, `vite.config.ts` e `index.html`. Arquivos antigos que já estavam modificados antes do trabalho não foram revertidos.

## 7. Frontend

`main.tsx → ManagerApp → ManagerLayout → features`. Mantine cuida da apresentação; TanStack Query, das consultas e invalidação; React Router, da navegação com páginas carregadas sob demanda. Tipos ficam em `types`; chamadas HTTP ficam em `services/api`. A instância Axios central aplica base URL, timeout, JWT e tratamento de 401. A sessão usa `sessionStorage`; o logout limpa sessão e cache. Não há `fetch` ou chamadas Axios espalhadas nas páginas novas.

## 8. Jira

`JiraController / DemandController → services → JiraClient → JsonHttpTransport`. Consulta issues existentes, campos, statuses, usuários, projetos, boards e sprints. Projetos/boards/sprints percorrem as páginas da API. A busca de issues usa `/rest/api/3/search/jql` e cursor. Squad é um mapeamento escolhido entre campos descobertos, sem presumir que seja Project.

Assignee controla o developer da demanda. Tester permite override local que a sincronização preserva. Status Jira é texto dinâmico, independente do enum de execução do CT. Transições consultam as opções disponíveis, enviam a transição selecionada e consultam novamente a issue. Essa ação altera o Jira e deve ser executada manualmente pelo usuário em uma demanda apropriada.

## 9. IA

`AiController → AiService → TestCaseAiProvider → OpenAI / Claude / Gemini / Groq / Ollama`. Cada provider adapta seu protocolo; a resposta é normalizada e validada. Prévia não grava casos. A importação explícita passa pelo `TestCaseService` e grava `TestCaseEntity` na suíte selecionada, com rollback em falhas. O texto enviado sai para o provider somente ao solicitar a geração.

## 9.1 RunManager

Disparo de automação dentro do QA Manager, no menu **RunManager**. O que o
painel da nuvem fazia com listas escritas no código aqui é cadastro:

- **Cadastro** — sistemas (com o caminho do projeto de automação e o comando da
  seleção), versões, ambientes e marcações. Ambiente marcado como produção fica
  vermelho e pede confirmação antes de rodar.
- **Disparar** — containers com cards. Cada card tem título, ambiente e o script
  que executa. Criar, editar e inativar são da tela; inativar some do disparo e
  preserva o histórico, e por isso excluir é recusado quando já houve execução.
- **Casos de teste** — leitura do projeto (`tests/<versão>/<módulo>/*.spec.ts`,
  com `test.describe` e `CT-000:`), mais casos cadastrados à mão com script
  próprio. A leitura é um botão: varrer o projeto leva segundos e fica guardada
  com a data e o autor.
- **Seleção** — marcar casos de módulos e versões diferentes e rodar tudo numa
  execução. O comando sai de `{{casos}}` no cadastro do sistema; em branco vale
  `npx playwright test {{casos}}`.
- **Histórico e terminal** — o log é lido por pedaços a partir do que a tela já
  tem, e não inteiro a cada segundo.
- **Falhas** — lidas do `test-results/results.json` ao fim da execução e
  classificadas em regra de negócio, resposta do backend, locator, infra e
  outro. Defeito do produto e defeito da automação pedem ações opostas.
- **Vimeo e Jira** — o vídeo da falha sobe para o Vimeo (token em Integrações) e
  vira link dentro da demanda criada no Jira. A criação de issue existe só aqui;
  o restante do sistema continua apenas consultando e comentando.

O motor de execução é local: o script roda na máquina, pelo interpretador de
comandos, na pasta do sistema. Isso vale no aplicativo instalado; num servidor
web "a máquina" seria o servidor, e por isso o motor só se oferece quando há
caminho local cadastrado. Quem cadastra um card manda um comando para a máquina
de quem clicar — o cadastro fica atrás de sessão autenticada por causa disso.

Não há verificação de documentação nem recorte por usuário: a visão é do time.

## 10. Configurar Jira

Abra **Integrações → Jira → Configurar**. Informe `https://ultracar.atlassian.net`, o e-mail de integração e o token. Salve e use **Testar conexão**. Depois use a descoberta de campos para escolher Squad, Sprint e Tester conforme a configuração real do Jira. A credencial volta mascarada; deixar o campo de token vazio numa edição preserva o token salvo. Em **Demandas**, consulte por JQL, importe uma issue existente e sincronize quando necessário.

## 11. Configurar IAs

### IA local

O provider `ollama` roda na própria máquina e não tem credencial: o campo de
API key não aparece para ele, e a tela lista os modelos baixados, deixa baixar
outros e escolher qual fica em uso. No aplicativo desktop o serviço e um modelo
já vêm no instalador, e o Electron sobe o serviço junto com o sistema; se a
porta padrão já estiver ocupada por um Ollama instalado por fora, esse é
aproveitado em vez de concorrer com ele.

O endereço é resolvido do mais específico para o mais genérico: o campo
**Endereço do serviço** em Integrações, depois a variável `OLLAMA_BASE_URL`, e
por fim `http://127.0.0.1:11434`. A variável existe porque o desktop escolhe a
porta em tempo de execução e precisa avisar o backend sem exigir configuração
na tela. Deixar tudo em branco é o caso normal.

Os modelos ficam no perfil do usuário (`%APPDATA%\<perfil>\ollama-models`) e não
na pasta do programa, que não tem permissão de escrita — sem isso não haveria
como baixar modelo novo pela tela. O modelo que vem no instalador é copiado
para lá na primeira execução.

### IAs de nuvem

Em **Integrações**, configure o card do provider com a API key e o identificador de um modelo disponível na sua conta. Salve, teste conexão e escolha o provider padrão, se desejado. Na suíte, use **Gerar com IA**, revise a descrição, escolha o provider, edite a prévia e importe somente os CTs selecionados. O teste de conexão consulta modelos; o uso do modelo escolhido deve ser confirmado pela geração manual.

## 12. Executar backend

Requer Java 21. O script seleciona `C:\Program Files\Java\jdk-21` quando disponível e carrega o `.env` da raiz.

```powershell
cd F:\manager
powershell -ExecutionPolicy Bypass -File .\scripts\start-database.ps1
# Para recompilar, execute Maven clean package na pasta backend.
powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1
```

O JAR usado pelo script fica em `backend/target/managerqa-1.0.0.jar`. API: `http://localhost:8081`. Saúde: `/actuator/health`. Para Maven nesta máquina, há uma instalação em `%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9\977a63e90f436cd6ade95b4c0e10c20c\bin\mvn.cmd`; o wrapper legado apresenta problema com espaços no caminho do usuário. Em outra máquina, use Maven 3.9+ no PATH.

O processo em primeiro plano termina com Ctrl+C. `scripts/stop-database.ps1` encerra apenas o cluster portátil deste projeto. Não execute duas instâncias do backend na porta 8081.

## 13. Executar frontend

```powershell
cd F:\manager\frontend
npm ci
npm run build
npm run dev
```

Interface: `http://127.0.0.1:5173`. `frontend/.env.local` aponta `VITE_API_BASE_URL` para `http://localhost:8081`. Use npm e `package-lock.json` para a arquitetura nova; os arquivos Yarn herdados não são o lockfile ativo.

## 14. Acesso local

Usuário de teste: `qa.local@example.test`. A senha aleatória está em `F:\manager\.runtime\local-access.json`, fora do Git. Também é possível cadastrar uma conta pela tela inicial. Não há token Jira ou chave de IA incluídos. O banco contém apenas os dados locais usados na validação.

## 15–17. Validação e builds

Consulte `docs/VALIDATION.md` para os resultados finais. Os testes Jira usam mocks e HTTP em `127.0.0.1`, sem chamadas à empresa. Os testes de persistência usam H2 isolado; a execução manual local usa PostgreSQL. O frontend foi compilado e verificado no navegador local.

## 18. Validação manual restante

Configurar credenciais reais; confirmar campos Squad/Sprint/Tester e permissões; consultar as demandas reais; confirmar a transição desejada manualmente; validar os modelos das quatro IAs com suas respectivas contas. O player Vimeo depende da privacidade/permissão de incorporação do vídeo. Nenhuma dessas dependências externas foi apresentada como validada com credenciais reais.

## 19. Limites técnicos

O histórico cobre gravações ORM feitas pela aplicação, incluindo alterações de associações. SQL executado diretamente no banco fica fora desse log. A política de acesso mantém o modelo atual de usuários autenticados; não foi inventada uma hierarquia de papéis. Dashboard e filtros usam os snapshots importados, por isso dependem de sincronização. A regra de aprovação é exclusivamente “Aguardando Deploy”. Filtros de data consideram a atualização da issue no Jira. Para volumes muito grandes, listagens locais poderão precisar de paginação no servidor. A biblioteca PDF legada foi preservada, incluindo sua licença; não houve migração de biblioteca.

## 20. Proibição de criar/excluir issues

Não existem endpoints, métodos ou botões de criação/exclusão de issues Jira. O único envio de escrita do `JiraClient` tem caminho fixo `/rest/api/3/issue/{key}/transitions` e corpo exclusivo de transição. A exclusão de CTs e outros registros locais continua disponível. Nenhum teste realizado criou, excluiu ou alterou qualquer issue do Jira da empresa.

Atalho de build nesta máquina: execute powershell -ExecutionPolicy Bypass -File .\scripts\build-backend.ps1 na raiz. O script localiza Maven no PATH ou no cache do wrapper e seleciona Java 21. Para executar apenas a bateria limpa de testes, acrescente -TestsOnly.


### Execução pela IDE

O backend importa automaticamente o arquivo .env da raiz quando o diretório de trabalho é F:\manager ou F:\manager\backend. Execute com Java 21 e mantenha o PostgreSQL iniciado. Variáveis do ambiente continuam tendo precedência. O .env usa linhas CHAVE=valor sem aspas; mantenha as chaves fora do Git. Para outro diretório de trabalho, configure a IDE para usar a pasta backend. Execute apenas uma instância na porta 8081.

A importação usa o mecanismo oficial spring.config.import do Spring Boot: https://docs.spring.io/spring-boot/3.4/reference/features/external-config.html
