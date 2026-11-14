package com.tester.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tester.dto.RegressionDTO;
import com.tester.dto.RegressionItemDTO;
import com.tester.entity.Regression;
import com.tester.entity.RegressionItem;
import com.tester.entity.TestPlan;
import com.tester.enuns.Status;
import com.tester.repository.RegressionRepository;
import com.tester.repository.SystemModuleRepository;
import com.tester.repository.TestPlanRepository;
import com.tester.repository.TesterQARepository;

/**
 * Rodadas de regressão.
 *
 * O trabalho chato que isto evita é levantar à mão o que mexeu desde a última
 * rodada: {@link #aprovados} varre os planos de teste que fecharam no período e
 * devolve os módulos envolvidos, já com o título da demanda e a observação. A
 * partir daí a lista é livre — dá para acrescentar módulos que não passaram por
 * demanda nenhuma e para tirar o que não faz sentido revalidar.
 */
@Service
public class RegressionService {

    private final RegressionRepository regressions;
    private final TestPlanRepository plans;
    private final SystemModuleRepository modules;
    private final TesterQARepository testers;

    public RegressionService(RegressionRepository regressions, TestPlanRepository plans,
                             SystemModuleRepository modules, TesterQARepository testers) {
        this.regressions = regressions;
        this.plans = plans;
        this.modules = modules;
        this.testers = testers;
    }

    /**
     * Lista as rodadas, opcionalmente recortadas por período.
     *
     * A escolha da consulta acontece aqui, e não numa condição dentro do SQL:
     * consulta que testa {@code :parametro IS NULL} passa no banco de memória
     * dos testes e é recusada pelo Postgres, que não consegue deduzir o tipo do
     * parâmetro.
     */
    @Transactional(readOnly = true)
    public List<RegressionDTO> listar(LocalDate de, LocalDate ate) {
        List<com.tester.entity.Regression> encontradas;
        if (de != null && ate != null) {
            encontradas = regressions.findByDataBetweenOrderByDataDescIdDesc(de, ate);
        } else if (de != null) {
            encontradas = regressions.findByDataGreaterThanEqualOrderByDataDescIdDesc(de);
        } else if (ate != null) {
            encontradas = regressions.findByDataLessThanEqualOrderByDataDescIdDesc(ate);
        } else {
            encontradas = regressions.findAllByOrderByDataDescIdDesc();
        }
        return encontradas.stream().map(RegressionDTO::resumo).toList();
    }

    @Transactional(readOnly = true)
    public RegressionDTO buscar(Long id) {
        return RegressionDTO.completo(exigir(id));
    }

    @Transactional
    public RegressionDTO criar(RegressionDTO dto, String autor) {
        Regression regression = new Regression();
        regression.setCreatedBy(autor);
        regression.setData(dto.getData() == null ? LocalDate.now() : dto.getData());
        aplicar(regression, dto);
        return RegressionDTO.completo(regressions.save(regression));
    }

    @Transactional
    public RegressionDTO atualizar(Long id, RegressionDTO dto) {
        Regression regression = exigir(id);
        if (dto.getData() != null) regression.setData(dto.getData());
        aplicar(regression, dto);
        return RegressionDTO.completo(regressions.save(regression));
    }

    @Transactional
    public void excluir(Long id) {
        regressions.delete(exigir(id));
    }

    /**
     * Clona a rodada mantendo o roteiro — módulos, descrição, observação e fluxo —
     * e zerando o que é do resultado: status e QA responsável. Sem isso o clone
     * nasceria dizendo que tudo já foi validado por alguém, que é justamente o
     * contrário do que uma rodada nova significa.
     */
    @Transactional
    public RegressionDTO clonar(Long id, RegressionDTO dto, String autor) {
        Regression origem = exigir(id);

        Regression copia = new Regression();
        copia.setCreatedBy(autor);
        copia.setData(dto != null && dto.getData() != null ? dto.getData() : LocalDate.now());
        copia.setName(dto != null && dto.getName() != null && !dto.getName().isBlank()
                ? dto.getName().trim()
                : nomeDoClone(origem, copia.getData()));
        copia.setObservation(origem.getObservation());

        int posicao = 0;
        for (RegressionItem item : origem.getItems()) {
            RegressionItem novo = new RegressionItem();
            novo.setRegression(copia);
            novo.setSystemModule(item.getSystemModule());
            novo.setDescription(item.getDescription());
            novo.setObservation(item.getObservation());
            novo.setFlow(item.getFlow());
            novo.setJiraKey(item.getJiraKey());
            novo.setStatus(null);
            novo.setTester(null);
            novo.setPosition(posicao++);
            copia.getItems().add(novo);
        }
        return RegressionDTO.completo(regressions.save(copia));
    }

    /**
     * Módulos que passaram por demanda concluída no período.
     *
     * Um módulo pode ter sido tocado por várias demandas; nesse caso ele aparece
     * uma vez só, com as demandas somadas na descrição — a regressão é por
     * módulo, e repetir a mesma linha três vezes só daria trabalho de limpar.
     */
    @Transactional(readOnly = true)
    public List<RegressionItemDTO> aprovados(LocalDate de, LocalDate ate) {
        List<TestPlan> concluidos = plans.findAll().stream()
                .filter(plan -> plan.getStatus() == Status.CONCLUIDA || plan.getStatus() == Status.RETORNO)
                .filter(plan -> plan.getSystemModule() != null)
                .filter(plan -> dentroDoPeriodo(plan.getData(), de, ate))
                .sorted(Comparator.comparing(TestPlan::getData, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<Long, RegressionItemDTO> porModulo = new LinkedHashMap<>();
        for (TestPlan plan : concluidos) {
            Long moduloId = plan.getSystemModule().getId();
            RegressionItemDTO existente = porModulo.get(moduloId);
            if (existente == null) {
                RegressionItemDTO item = new RegressionItemDTO();
                item.setSystemModuleId(moduloId);
                item.setSystemModuleName(plan.getSystemModule().getName());
                item.setDescription(plan.getName());
                item.setObservation(plan.getObservation());
                item.setJiraKey(plan.getJira());
                item.setStatus(plan.getStatus());
                item.setPosition(porModulo.size());
                porModulo.put(moduloId, item);
            } else {
                existente.setDescription(juntar(existente.getDescription(), plan.getName()));
                existente.setJiraKey(juntar(existente.getJiraKey(), plan.getJira()));
                // Uma demanda em retorno dentro do módulo já basta para a linha
                // inteira merecer atenção na regressão.
                if (plan.getStatus() == Status.RETORNO) existente.setStatus(Status.RETORNO);
            }
        }
        return new ArrayList<>(porModulo.values());
    }

    private boolean dentroDoPeriodo(LocalDate data, LocalDate de, LocalDate ate) {
        if (data == null) return de == null && ate == null;
        if (de != null && data.isBefore(de)) return false;
        return ate == null || !data.isAfter(ate);
    }

    private String juntar(String atual, String novo) {
        if (novo == null || novo.isBlank()) return atual;
        if (atual == null || atual.isBlank()) return novo;
        if (atual.contains(novo)) return atual;
        return atual + " · " + novo;
    }

    private String nomeDoClone(Regression origem, LocalDate data) {
        String base = origem.getName() == null || origem.getName().isBlank() ? "Regressão" : origem.getName().trim();
        return base + " (cópia de " + data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")";
    }

    private void aplicar(Regression regression, RegressionDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank())
            throw new IllegalArgumentException("Dê um nome à regressão.");
        regression.setName(dto.getName().trim());
        regression.setObservation(dto.getObservation());

        // A tela envia a lista como ela ficou; orphanRemoval apaga o que saiu.
        regression.getItems().clear();
        int posicao = 0;
        for (RegressionItemDTO entrada : dto.getItems()) {
            boolean vazio = entrada.getSystemModuleId() == null
                    && (entrada.getDescription() == null || entrada.getDescription().isBlank())
                    && (entrada.getFlow() == null || entrada.getFlow().isBlank());
            if (vazio) continue; // linha em branco que sobrou na tela

            RegressionItem item = new RegressionItem();
            item.setRegression(regression);
            item.setSystemModule(entrada.getSystemModuleId() == null ? null
                    : modules.findById(entrada.getSystemModuleId()).orElse(null));
            item.setDescription(entrada.getDescription());
            item.setObservation(entrada.getObservation());
            item.setFlow(entrada.getFlow());
            item.setStatus(entrada.getStatus());
            item.setTester(entrada.getTesterId() == null ? null
                    : testers.findById(entrada.getTesterId()).orElse(null));
            item.setJiraKey(entrada.getJiraKey());
            item.setPosition(posicao++);
            regression.getItems().add(item);
        }
    }

    private Regression exigir(Long id) {
        return regressions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Regressão não encontrada."));
    }
}
