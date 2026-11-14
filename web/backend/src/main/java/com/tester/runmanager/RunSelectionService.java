package com.tester.runmanager;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tester.runmanager.RunPanelDTOs.DisparoDTO;
import com.tester.runmanager.RunPanelDTOs.ExecutionDTO;

/**
 * Execução de uma seleção livre de casos.
 *
 * É o que o documento chama de execução customizada: marcar casos de módulos e
 * versões diferentes e rodar tudo de uma vez. O comando sai do cadastro do
 * sistema — o campo onde {{casos}} é trocado pelos alvos escolhidos —, e não de
 * uma regra fixa aqui dentro, porque cada ferramenta de automação escreve essa
 * linha de um jeito.
 *
 * Caso cadastrado à mão tem script próprio e entra encadeado depois do comando
 * principal: ele não é um alvo do Playwright, é outro comando.
 */
@Service
public class RunSelectionService {

    /** O que vale quando o sistema não define comando próprio. */
    static final String PADRAO = "npx playwright test {{casos}}";

    private final ProjectCatalogService projeto;
    private final RunPanelService painel;
    private final com.tester.runmanager.repository.RunSystemRepository sistemas;

    public RunSelectionService(ProjectCatalogService projeto, RunPanelService painel,
                               com.tester.runmanager.repository.RunSystemRepository sistemas) {
        this.projeto = projeto;
        this.painel = painel;
        this.sistemas = sistemas;
    }

    public record SelecaoDTO(Long systemId, Long environmentId, String title, List<String> caseIds) {
    }

    @Transactional
    public ExecutionDTO disparar(SelecaoDTO pedido, String quem) {
        if (pedido.caseIds() == null || pedido.caseIds().isEmpty())
            throw new IllegalArgumentException("Marque ao menos um caso.");
        if (pedido.caseIds().size() > 500)
            throw new IllegalArgumentException("São muitos casos de uma vez; marque até 500.");

        var sistema = sistemas.findById(pedido.systemId())
                .orElseThrow(() -> new IllegalArgumentException("Sistema não encontrado."));
        var leitura = projeto.casos(pedido.systemId());

        List<String> alvos = new ArrayList<>();
        List<String> avulsos = new ArrayList<>();
        for (String id : pedido.caseIds()) {
            var caso = procurar(leitura, id);
            if (caso == null) continue;
            if (caso.manual()) {
                if (caso.script() != null && !caso.script().isBlank()) avulsos.add(caso.script().trim());
            } else if (caso.file() != null) {
                alvos.add(caso.line() == null ? caso.file() : caso.file() + ":" + caso.line());
            }
        }
        if (alvos.isEmpty() && avulsos.isEmpty())
            throw new IllegalArgumentException(
                    "Os casos marcados não têm como ser executados: nenhum tem arquivo nem script.");

        String script = montar(sistema.getSelectionScript(), alvos, avulsos);
        String titulo = pedido.title() == null || pedido.title().isBlank()
                ? "Seleção de " + pedido.caseIds().size() + " caso(s)"
                : pedido.title().trim();

        return painel.dispararAvulso(
                new DisparoDTO(titulo, script, pedido.systemId(), pedido.environmentId()), quem);
    }

    public static String montar(String template, List<String> alvos, List<String> avulsos) {
        List<String> comandos = new ArrayList<>();
        if (!alvos.isEmpty()) {
            String modelo = template == null || template.isBlank() ? PADRAO : template.trim();
            String lista = String.join(" ", alvos.stream().map(RunSelectionService::citar).toList());
            comandos.add(modelo.contains("{{casos}}") ? modelo.replace("{{casos}}", lista)
                    : modelo + " " + lista);
        }
        comandos.addAll(avulsos);
        return String.join(" && ", comandos);
    }

    /**
     * Aspas em volta do alvo: caminho de teste tem barra e dois-pontos, e em
     * alguns projetos tem espaço — sem aspas o interpretador parte o caminho no
     * meio e o Playwright não acha o arquivo.
     */
    private static String citar(String alvo) {
        return alvo.contains(" ") ? "\"" + alvo.replace("\"", "") + "\"" : alvo;
    }

    private static ProjectCatalogService.CasoDTO procurar(ProjectCatalogService.LeituraDTO leitura,
                                                          String id) {
        for (var modulo : leitura.modules())
            for (var caso : modulo.tests())
                if (caso.id().equals(id)) return caso;
        return null;
    }
}
