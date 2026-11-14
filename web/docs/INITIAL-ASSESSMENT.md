# Análise inicial

- Preservar entidades TestPlan -> TestSuite -> TestCaseEntity, cadastros, clone e serviços de exportação Excel/PDF.
- Preservar alterações locais preexistentes; não restaurar o checkout nem extrair o arquivo RAR sobre ele.
- Refatorar segurança: endpoints privados expostos com permitAll; Maven suprime testes.
- Substituir a apresentação antiga por frontend React/TypeScript/Mantine organizado, preservando funcionalidades.
- Adicionar integração Jira dedicada, status dinâmicos e armazenamento criptografado de credenciais.
- Casos de IA devem passar por preview e entrar em TestCaseEntity.
- Proibido criar/excluir issues Jira, push, PR e deploy.
- Banco existente: alterações aditivas e validação antes de qualquer alteração destrutiva.
