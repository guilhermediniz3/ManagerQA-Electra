package com.tester.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tester.dto.DocCardDTO;
import com.tester.entity.DocCard;
import com.tester.repository.DocCardRepository;
import com.tester.repository.SystemModuleRepository;

/**
 * Acervo de documentação.
 *
 * O conteúdo entra e sai do banco exatamente como o editor o produziu. Qualquer
 * normalização aqui — reindentar, reescrever tags, "limpar" atributos — quebraria
 * os nós próprios do editor (badge, alerta, vídeo redimensionável), que dependem
 * de atributos específicos para voltar a ser o que eram ao reabrir o card.
 */
@Service
public class DocCardService {

    private final DocCardRepository cards;
    private final SystemModuleRepository modules;

    public DocCardService(DocCardRepository cards, SystemModuleRepository modules) {
        this.cards = cards;
        this.modules = modules;
    }

    @Transactional(readOnly = true)
    public List<DocCardDTO> listar() {
        return cards.findAllByOrderBySortOrderAscIdDesc().stream().map(DocCardDTO::resumo).toList();
    }

    @Transactional(readOnly = true)
    public DocCardDTO buscar(Long id) {
        return DocCardDTO.completo(exigir(id));
    }

    @Transactional
    public DocCardDTO criar(DocCardDTO dto, String autor) {
        DocCard card = new DocCard();
        card.setCreatedBy(autor);
        card.setData(dto.getData() == null ? LocalDate.now() : dto.getData());
        aplicar(card, dto);
        return DocCardDTO.completo(cards.save(card));
    }

    @Transactional
    public DocCardDTO atualizar(Long id, DocCardDTO dto) {
        DocCard card = exigir(id);
        if (dto.getData() != null) card.setData(dto.getData());
        aplicar(card, dto);
        return DocCardDTO.completo(cards.save(card));
    }

    /** Duplica o card para servir de ponto de partida a um novo. */
    @Transactional
    public DocCardDTO clonar(Long id, String autor) {
        DocCard origem = exigir(id);
        DocCard copia = new DocCard();
        copia.setTitle(origem.getTitle() + " (cópia)");
        copia.setDescription(origem.getDescription());
        copia.setIcon(origem.getIcon());
        copia.setContent(origem.getContent());
        copia.setActive(false); // nasce fora do ar até alguém revisar
        copia.setSortOrder(origem.getSortOrder());
        copia.setSystemModule(origem.getSystemModule());
        copia.setSuiteId(origem.getSuiteId());
        copia.setCreatedBy(autor);
        copia.setData(LocalDate.now());
        return DocCardDTO.completo(cards.save(copia));
    }

    @Transactional
    public void excluir(Long id) {
        cards.delete(exigir(id));
    }

    private void aplicar(DocCard card, DocCardDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank())
            throw new IllegalArgumentException("Dê um título ao card.");
        card.setTitle(dto.getTitle().trim());
        card.setDescription(dto.getDescription());
        card.setIcon(dto.getIcon() == null || dto.getIcon().isBlank() ? "FaRegFileAlt" : dto.getIcon().trim());
        card.setContent(dto.getContent());
        card.setActive(dto.isActive());
        card.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        card.setSuiteId(dto.getSuiteId());
        card.setSystemModule(dto.getSystemModuleId() == null ? null
                : modules.findById(dto.getSystemModuleId()).orElse(null));
    }

    private DocCard exigir(Long id) {
        return cards.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Card de documentação não encontrado."));
    }
}
