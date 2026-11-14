package com.tester.runmanager;

import com.tester.runmanager.entity.RunExecution;
import com.tester.runmanager.entity.RunSystem;

/**
 * Quem de fato roda a execução.
 *
 * Existe como interface porque o documento deixa a decisão em aberto e as duas
 * respostas são legítimas: rodar na máquina de quem clicou — terminal ao vivo,
 * só no aplicativo instalado — ou disparar a pipeline do repositório, que
 * funciona também na versão web. A tela é a mesma nos dois casos; o que muda é
 * quem executa.
 */
public interface RunEngine {

    /** "local" ou "pipeline". */
    String nome();

    /** Este motor consegue rodar o que este sistema pede? */
    boolean atende(RunSystem sistema);

    /**
     * Começa a execução e devolve imediatamente. O andamento aparece pelo log,
     * que a tela lê em pedaços — esperar aqui prenderia a requisição por minutos.
     */
    /**
     * @param variaveis o que entra no ambiente do processo — é por aqui que o
     *                  projeto de automação descobre se roda em homologação ou
     *                  em produção
     */
    void disparar(RunExecution execucao, RunSystem sistema,
                  java.util.Map<String, String> variaveis, Registro registro);

    /**
     * Interrompe o que estiver em andamento.
     *
     * @return verdadeiro se havia processo vivo para matar. Quem chama precisa
     *         saber: havendo processo, quem fecha a execução no banco é o fim
     *         dele, e escrever aqui também criaria duas gravações disputando a
     *         mesma linha.
     */
    boolean cancelar(Long execucaoId);

    /**
     * Por onde o motor devolve o que vai acontecendo.
     *
     * É o serviço que decide onde guardar — o motor não conhece banco nem
     * transação, e é isso que deixa o motor local testável sem subir o Spring.
     */
    interface Registro {
        void saida(Long execucaoId, String trecho);
        void encerrou(Long execucaoId, int codigo);
        void falhou(Long execucaoId, String motivo);
    }
}
