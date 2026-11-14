package com.tester.runmanager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tester.runmanager.RunPanelDTOs.*;
import com.tester.runmanager.entity.*;
import com.tester.runmanager.repository.*;

/**
 * O painel de disparo: containers, cards e as execuções que eles geram.
 *
 * O log de quem está rodando fica em memória e vai para o banco quando a
 * execução termina. Gravar linha a linha transformaria um teste de vinte
 * minutos em milhares de escritas, e a tela lê o trecho novo direto daqui
 * enquanto o processo vive.
 */
@Service
public class RunPanelService {

    private final RunContainerRepository containers;
    private final RunCardRepository cards;
    private final RunExecutionRepository execucoes;
    private final RunSystemRepository sistemas;
    private final RunEnvironmentRepository ambientes;
    private final RunVersionRepository versoes;
    private final RunTagRepository tags;
    private final List<RunEngine> motores;
    private final RunExecutionWriter escritor;

    /** Log do que está rodando agora, por execução. */
    private final Map<Long, StringBuilder> emAndamento = new ConcurrentHashMap<>();

    /**
     * Quem foi interrompido de propósito.
     *
     * O fim do processo chega igual para quem foi morto e para quem falhou
     * sozinho — código de saída diferente de zero nos dois casos. Sem esta
     * marca, uma execução interrompida por alguém seria registrada como falha
     * do teste, e o painel passaria a contar defeito onde houve decisão.
     */
    private final java.util.Set<Long> interrompidas = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public RunPanelService(RunContainerRepository containers, RunCardRepository cards,
                           RunExecutionRepository execucoes, RunSystemRepository sistemas,
                           RunEnvironmentRepository ambientes, RunVersionRepository versoes,
                           RunTagRepository tags, List<RunEngine> motores,
                           RunExecutionWriter escritor) {
        this.containers = containers;
        this.cards = cards;
        this.execucoes = execucoes;
        this.sistemas = sistemas;
        this.ambientes = ambientes;
        this.versoes = versoes;
        this.tags = tags;
        this.motores = motores;
        this.escritor = escritor;
    }

    // ------------------------------------------------------------- containers

    @Transactional(readOnly = true)
    public List<ContainerDTO> listar(boolean somenteAtivos) {
        var lista = somenteAtivos ? containers.findByActiveTrueOrderByPositionAscIdAsc()
                : containers.findAllByOrderByPositionAscIdAsc();
        return lista.stream()
                .map(c -> somenteAtivos ? semCardsInativos(c) : ContainerDTO.de(c))
                .toList();
    }

    /** Na tela de disparo, card inativo não aparece — é o que "inativar" quer dizer. */
    private static ContainerDTO semCardsInativos(RunContainer c) {
        var dto = ContainerDTO.de(c);
        return new ContainerDTO(dto.id(), dto.title(), dto.description(), dto.active(),
                dto.position(), dto.cards().stream().filter(CardDTO::active).toList());
    }

    @Transactional
    public ContainerDTO salvarContainer(ContainerDTO entrada) {
        RunContainer c = entrada.id() == null ? new RunContainer()
                : containers.findById(entrada.id()).orElseThrow(() -> naoEncontrado("Container"));
        if (entrada.title() == null || entrada.title().isBlank())
            throw new IllegalArgumentException("Dê um título ao container.");
        c.setTitle(entrada.title().trim());
        c.setDescription(entrada.description() == null || entrada.description().isBlank()
                ? null : entrada.description().trim());
        c.setActive(entrada.active());
        c.setPosition(entrada.position());
        return ContainerDTO.de(containers.save(c));
    }

    /**
     * Excluir só vale enquanto ninguém rodou.
     *
     * Depois que há execução no histórico, apagar o card levaria junto o
     * registro do que rodou naquele dia — ou deixaria o histórico apontando
     * para o vazio. É para isso que existe inativar: some da tela de disparo e
     * o passado continua de pé.
     */
    @Transactional
    public void excluirContainer(Long id) {
        RunContainer container = containers.findById(id).orElseThrow(() -> naoEncontrado("Container"));
        for (RunCard card : container.getCards()) exigirSemHistorico(card, "Este container");
        containers.delete(container);
    }

    // ------------------------------------------------------------------ cards

    @Transactional
    public CardDTO salvarCard(CardDTO entrada) {
        RunCard card = entrada.id() == null ? new RunCard()
                : cards.findById(entrada.id()).orElseThrow(() -> naoEncontrado("Card"));
        if (entrada.containerId() == null)
            throw new IllegalArgumentException("Escolha o container do card.");
        if (entrada.title() == null || entrada.title().isBlank())
            throw new IllegalArgumentException("Dê um título ao card.");
        if (entrada.script() == null || entrada.script().isBlank())
            throw new IllegalArgumentException("Informe o script que o card executa.");

        card.setContainer(containers.findById(entrada.containerId())
                .orElseThrow(() -> naoEncontrado("Container")));
        card.setTitle(entrada.title().trim());
        card.setScript(entrada.script().trim());
        card.setEnvironment(entrada.environmentId() == null ? null
                : ambientes.findById(entrada.environmentId()).orElseThrow(() -> naoEncontrado("Ambiente")));
        card.setSystem(entrada.systemId() == null ? null
                : sistemas.findById(entrada.systemId()).orElseThrow(() -> naoEncontrado("Sistema")));
        card.setVersion(entrada.versionId() == null ? null
                : versoes.findById(entrada.versionId()).orElseThrow(() -> naoEncontrado("Versão")));
        card.setTag(entrada.tagId() == null ? null
                : tags.findById(entrada.tagId()).orElseThrow(() -> naoEncontrado("Marcação")));
        card.setActive(entrada.active());
        card.setPosition(entrada.position());
        return CardDTO.de(cards.save(card));
    }

    @Transactional
    public void excluirCard(Long id) {
        RunCard card = cards.findById(id).orElseThrow(() -> naoEncontrado("Card"));
        exigirSemHistorico(card, "Este card");
        cards.delete(card);
    }

    private void exigirSemHistorico(RunCard card, String quem) {
        long quantas = execucoes.countByCardId(card.getId());
        if (quantas > 0)
            throw new IllegalArgumentException(quem + " já foi executado " + quantas
                    + " vez(es) e o histórico aponta para ele. Use inativar: some da tela de "
                    + "disparo e o histórico continua de pé.");
    }

    // -------------------------------------------------------------- execução

    @Transactional
    public ExecutionDTO dispararCard(Long cardId, String quem) {
        RunCard card = cards.findById(cardId).orElseThrow(() -> naoEncontrado("Card"));
        if (!card.isActive())
            throw new IllegalArgumentException("Este card está inativo.");

        RunExecution e = new RunExecution();
        e.setCard(card);
        e.setTitle(nomeDaExecucao(card));
        e.setScript(comVersao(card.getScript(), card.getVersion(),
                card.getSystem() == null ? null : card.getSystem().getVersionFlag()));
        e.setEnvironmentKey(card.getEnvironment() == null ? null : card.getEnvironment().getKey());
        e.setSystemKey(card.getSystem() == null ? null : card.getSystem().getKey());
        return iniciar(e, card.getSystem(), card.getEnvironment(), quem);
    }

    @Transactional
    public ExecutionDTO dispararAvulso(DisparoDTO pedido, String quem) {
        if (pedido.script() == null || pedido.script().isBlank())
            throw new IllegalArgumentException("Informe o script a executar.");
        RunSystem sistema = pedido.systemId() == null ? null
                : sistemas.findById(pedido.systemId()).orElseThrow(() -> naoEncontrado("Sistema"));
        RunEnvironment ambiente = pedido.environmentId() == null ? null
                : ambientes.findById(pedido.environmentId()).orElseThrow(() -> naoEncontrado("Ambiente"));

        RunExecution e = new RunExecution();
        e.setTitle(pedido.title() == null || pedido.title().isBlank() ? "Execução avulsa" : pedido.title().trim());
        e.setScript(pedido.script().trim());
        e.setEnvironmentKey(ambiente == null ? null : ambiente.getKey());
        e.setSystemKey(sistema == null ? null : sistema.getKey());
        return iniciar(e, sistema, ambiente, quem);
    }

    /**
     * Acrescenta a versão ao comando, quando ela não estiver escrita nele.
     *
     * No Playwright, versão é "project", e é o comando que a escolhe:
     * {@code --project=v2}. Guardar a versão no card sem levá-la ao comando
     * faria o card dizer V3 e rodar tudo — o mesmo erro que o ambiente já
     * cometeu antes de passar a virar variável.
     *
     * Quem já escreveu --project no script manda nele: script é o lugar onde
     * se resolve o caso que a tela não previu.
     */
    /** Como o Playwright escolhe o alvo; vale quando o sistema não diz outra coisa. */
    static final String FORMATO_PADRAO = "--project={{versao}}";

    public static String comVersao(String script, RunVersion versao, String formato) {
        if (versao == null || versao.getKey() == null || versao.getKey().isBlank()) return script;
        if (script == null || script.isBlank()) return script;

        String modelo = formato == null || formato.isBlank() ? FORMATO_PADRAO : formato.trim();
        // "nenhum" é a saída para quem resolve a versão dentro do próprio script
        // — há ferramenta que não tem parâmetro equivalente.
        if (modelo.equalsIgnoreCase("nenhum")) return script;

        String trecho = modelo.contains("{{versao}}")
                ? modelo.replace("{{versao}}", versao.getKey())
                : modelo + " " + versao.getKey();

        // O que já está escrito no script manda: é lá que se resolve o caso que
        // a tela não previu.
        String parametro = trecho.split("[=\s]")[0];
        if (!parametro.isBlank() && script.contains(parametro)) return script;
        return script.trim() + " " + trecho;
    }

    private ExecutionDTO iniciar(RunExecution e, RunSystem sistema,
                                 RunEnvironment ambiente, String quem) {
        RunEngine motor = motores.stream().filter(m -> m.atende(sistema)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nenhum motor de execução atende este sistema. Configure o caminho local do "
                        + "projeto no cadastro do sistema, ou o repositório para disparar pela pipeline."));

        e.setEngine(motor.nome());
        e.setState("RUNNING");
        e.setTriggeredBy(quem);
        e.setStartedAt(LocalDateTime.now());
        RunExecution salva = execucoes.save(e);

        emAndamento.put(salva.getId(), new StringBuilder());
        // É aqui que o ambiente deixa de ser rótulo: as variáveis dele entram
        // no processo e são o que faz o teste apontar para homologação ou para
        // produção.
        var variaveis = RunVariables.montar(sistema, ambiente);
        motor.disparar(salva, sistema, variaveis, registro(sistema));
        return ExecutionDTO.de(salva);
    }

    private static String nomeDaExecucao(RunCard card) {
        String container = card.getContainer() == null ? "" : card.getContainer().getTitle() + " · ";
        return container + card.getTitle();
    }

    /**
     * Onde o motor deposita o que vai saindo.
     *
     * A escrita no banco acontece fora da transação de quem disparou: o motor
     * responde minutos depois, quando aquela transação já terminou há muito.
     */
    private RunEngine.Registro registro(RunSystem sistema) {
        return new RunEngine.Registro() {
            @Override public void saida(Long id, String trecho) {
                emAndamento.computeIfAbsent(id, k -> new StringBuilder()).append(trecho);
            }

            @Override public void encerrou(Long id, int codigo) {
                if (interrompidas.remove(id)) {
                    escritor.encerrar(id, "CANCELLED", codigo, consumir(id),
                            "-- interrompida por quem disparou --", null);
                    return;
                }
                escritor.encerrar(id, codigo == 0 ? "SUCCESS" : "FAILED", codigo,
                        consumir(id), null, raiz(sistema));
            }

            @Override public void falhou(Long id, String motivo) {
                if (interrompidas.remove(id)) {
                    escritor.encerrar(id, "CANCELLED", null, consumir(id),
                            "-- interrompida por quem disparou --", null);
                    return;
                }
                escritor.encerrar(id, "FAILED", null, consumir(id), motivo, raiz(sistema));
            }
        };
    }

    private static String raiz(RunSystem sistema) {
        return sistema == null ? null : sistema.getLocalPath();
    }

    /** Tira o log da memória e devolve o texto: quem terminou não precisa mais dele aqui. */
    private String consumir(Long id) {
        StringBuilder log = emAndamento.remove(id);
        return log == null ? "" : log.toString();
    }

    /**
     * Interrompe a execução.
     *
     * Quem fecha a linha no banco é o fim do processo, e não este método: matar
     * o processo faz o motor avisar em seguida, e gravar aqui também deixaria
     * duas escritas disputando a mesma linha — foi assim que o log de uma
     * execução interrompida chegou a ser apagado pela gravação que veio depois.
     * Só quando não há processo vivo é que o fechamento acontece aqui.
     */
    @Transactional
    public void cancelar(Long id) {
        RunExecution e = execucoes.findById(id).orElseThrow(() -> naoEncontrado("Execução"));
        if (!"RUNNING".equals(e.getState())) return;

        interrompidas.add(id);
        boolean haviaProcesso = false;
        for (RunEngine motor : motores) if (motor.cancelar(id)) haviaProcesso = true;
        if (haviaProcesso) return;

        interrompidas.remove(id);
        escritor.encerrar(id, "CANCELLED", null, consumir(id),
                "-- interrompida por quem disparou --", null);
    }

    @Transactional(readOnly = true)
    public List<ExecutionDTO> historico() {
        return execucoes.findTop100ByOrderByStartedAtDescIdDesc().stream().map(ExecutionDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public ExecutionDTO execucao(Long id) {
        return ExecutionDTO.de(execucoes.findById(id).orElseThrow(() -> naoEncontrado("Execução")));
    }

    /**
     * Trecho do log a partir de onde a tela parou.
     *
     * Enquanto roda, lê da memória; depois de terminar, do banco. O offset é o
     * que evita reenviar um log de megabytes a cada segundo.
     */
    @Transactional(readOnly = true)
    public LogDTO log(Long id, int offset) {
        RunExecution e = execucoes.findById(id).orElseThrow(() -> naoEncontrado("Execução"));
        StringBuilder vivo = emAndamento.get(id);
        String texto = vivo != null ? vivo.toString() : (e.getLog() == null ? "" : e.getLog());
        int inicio = Math.max(0, Math.min(offset, texto.length()));
        return new LogDTO(id, e.getState(), texto.length(), texto.substring(inicio),
                "RUNNING".equals(e.getState()));
    }

    private static IllegalArgumentException naoEncontrado(String o) {
        return new IllegalArgumentException(o + " não encontrado.");
    }
}
