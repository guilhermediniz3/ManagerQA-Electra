# Validação final — 9 de setembro de 2026

## Build backend

Java 21, Maven 3.9.9: `mvn -B -ntp clean package` — **BUILD SUCCESS**.

**44 testes executados; 0 falhas, 0 erros, 0 ignorados.**

| Teste | Casos | Cobertura |
|---|---:|---|
| ManagerFlowTest | 2 | JWT, BCrypt, login, hierarquia, exportações, clone |
| AuditLogTest | 5 | Criar/editar/excluir, ator, rollback, associação, sigilo e autorização |
| JiraClientHttpContractTest | 18 | HTTP local, leitura, única escrita por transição, rejeição de caminhos/IDs inválidos |
| JiraTransitionWorkflowTest | 3 | Disponibilidade, rejeição e ressincronização |
| DemandSynchronizationContractTest | 6 | Assignee, override de tester, atualização de snapshots |
| AiExplicitImportContractTest | 5 | Prévia sem gravação, importação explícita no TestCase normal, entradas inválidas |
| AiValidationTest | 1 | Normalização de JSON da IA |
| DashboardDateFilterTest | 2 | Datas ISO e formato de offset do Jira |
| ExcelLayoutTest | 1 | Títulos, cabeçalhos, merges, bordas, estilos, larguras |
| SecretCipherTest | 1 | Criptografia, nonce e chave incorreta |

Relatórios: `backend/target/surefire-reports`. Log da compilação: `.runtime/final-backend-build.log`.

Uma rodada anterior encontrou limitação do seletor NIO do Windows ao iniciar o servidor HTTP de teste. A implementação do simulador foi ajustada para um socket bloqueante com timeout, e a rodada completa acima passou. O backend usa Tomcat NIO2 e transporte HTTP bloqueante com limites de tempo e tamanho de resposta.

## Build frontend

`npm run build` — **sucesso**, TypeScript e Vite, 1.530 módulos. Páginas carregadas sob demanda; nenhum chunk excedeu o aviso padrão de 500 kB. `npm audit --omit=dev`: **0 vulnerabilidades reportadas** no momento da verificação. Isso não substitui revisão contínua das dependências.

## Execução com PostgreSQL

JAR final iniciado em `http://localhost:8081`, PostgreSQL 18.3 local, health `UP`. Vite iniciado em `http://127.0.0.1:5173`.

Validação por API local do JAR final: login; conexão ao PostgreSQL; senha do ambiente de teste criptografada na coluna física e ausente do JSON; plano/suíte/caso; execução do CT; edição de suíte preservando o CT; XLSX e PDF com assinaturas de arquivo válidas; clone com um caso; auditoria com usuário autenticado. Foi mantido um plano “Validação local do Manager” para inspeção manual. Sua referência `LOCAL-9001` é apenas texto local e não foi consultada ou criada no Jira.

## Interface

Verificados no navegador: login; navegação Mantine; temas claro e escuro; tela com os cinco cards de integração não configurados; criação, edição e exclusão de um tester local; histórico mostrando os três eventos com nome/e-mail do usuário, ação, ID e horário. O erro de conexão durante reinicialização apareceu com opção de tentar novamente, sem tela branca.

## Integrações externas

**Nenhuma issue do Jira empresarial foi criada, excluída ou alterada.** Testes Jira ficaram em mocks e `127.0.0.1`. Não foram usados tokens reais, nem realizadas gerações em provedores reais de IA. A confirmação de campos, permissões, transições e modelos em contas reais fica para o teste manual solicitado pelo usuário. O código Jira não oferece criação ou exclusão de issue; a escrita disponível é exclusivamente a transição de uma issue existente.

Conferência adicional da interface: listagem e detalhe do plano de demonstração, seleção de suíte, CT concluído, barra de progresso em 100%, ações de Excel/PDF e formulário de edição da suíte carregados corretamente.

Correção de inicialização pela IDE: importação opcional de .env adicionada em application.properties. Build aprovado com 44 testes. Inicialização direta do JAR, sem o script de carregamento de variáveis, validada com PostgreSQL e health UP em porta temporária; processo de validação encerrado.

Correção da tela branca no Vite: declarações lazy foram movidas para depois dos imports de React. Reproduzido o erro Cannot access lazy before initialization na abertura inicial; corrigido e validado em aba nova, com formulário de login visível e sem erros de console. Build TypeScript/Vite aprovado. O servidor de desenvolvimento foi reiniciado com cache refeito.
