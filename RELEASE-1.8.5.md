# QA Manager 1.8.5

14/11/2026

- Cabeçalho UL alterado para Projeto nos relatórios Excel/PDF e no comentário enviado ao Jira.
- Cabeçalho Bitrix alterado para Chamado, incluindo os campos visíveis da interface.
- Cabeçalho Evidência (Vimeo / Drive) simplificado para Evidência.
- Dados, campos internos e comportamento das evidências preservados, incluindo prints intercalados e vídeo no corpo do comentário.
- Resultados Retorno e Concluída passam a ser exibidos como Falhou e Aprovado. Identificadores internos e dados existentes preservados.
- Comentários novos no Jira mostram Falhou em vermelho escuro (#AE2E24) e Aprovado em verde escuro (#216E4E), ambos em negrito.

As alterações valem para novos relatórios e novos envios ao Jira. Comentários já publicados não são editados automaticamente.

Validação: 320 testes Java aprovados e build TypeScript/Vite concluído. O teste do comentário confere os cabeçalhos, textos e as marcas de cor/negrito do ADF. Nenhum comentário foi publicado no Jira real durante esta validação.
