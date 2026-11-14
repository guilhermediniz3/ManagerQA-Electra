package com.tester.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tester.dto.DocCardDTO;
import com.tester.entity.User;
import com.tester.service.DocCardService;
import com.tester.service.DocChatService;

@RestController
@RequestMapping("/doc-cards")
public class DocCardController {

    private final DocCardService service;
    private final DocChatService chat;
    private final com.tester.service.DocExportService exportacao;
    private final com.tester.repository.DocCardRepository cards;

    public DocCardController(DocCardService service, DocChatService chat,
            com.tester.service.DocExportService exportacao,
            com.tester.repository.DocCardRepository cards) {
        this.service = service;
        this.chat = chat;
        this.exportacao = exportacao;
        this.cards = cards;
    }

    public record Pergunta(String question, String provider) {}

    @GetMapping
    public ResponseEntity<List<DocCardDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocCardDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    @PostMapping
    public ResponseEntity<DocCardDTO> criar(@RequestBody DocCardDTO dto, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.criar(dto, user == null ? null : user.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocCardDTO> atualizar(@PathVariable Long id, @RequestBody DocCardDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<DocCardDTO> clonar(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.clonar(id, user == null ? null : user.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /** Pergunta ao acervo: documentação primeiro, casos de teste como reserva. */
    @PostMapping("/chat")
    public ResponseEntity<DocChatService.Resposta> perguntar(@RequestBody Pergunta pergunta) {
        return ResponseEntity.ok(chat.perguntar(pergunta.question(), pergunta.provider()));
    }

    /**
     * O card em Word e em PDF.
     *
     * O Word vai para a área de requisitos, então sai formatado: títulos,
     * avisos coloridos, selos, tabelas e os endereços das gravações como link
     * clicável. O PDF acompanha o mesmo desenho, para quem só vai ler.
     */
    @GetMapping("/{id}/word")
    public ResponseEntity<byte[]> word(@PathVariable Long id) {
        com.tester.entity.DocCard card = exigir(id);
        return arquivo(exportacao.word(card), exportacao.nomeDoArquivo(card, "docx"),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        com.tester.entity.DocCard card = exigir(id);
        return arquivo(exportacao.pdf(card), exportacao.nomeDoArquivo(card, "pdf"), "application/pdf");
    }

    private com.tester.entity.DocCard exigir(Long id) {
        return cards.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Card de documentação não encontrado."));
    }

    /**
     * O arquivo, com o nome escrito das duas formas que o padrão prevê.
     *
     * O {@code filename} simples vai sem acento, para navegador antigo, e o
     * {@code filename*} leva o nome completo. Deixar a montagem por conta da
     * classe do Spring punha no primeiro campo o nome codificado em MIME, e era
     * esse rótulo — cheio de "=?UTF-8?Q?" — que acabava virando o nome do
     * arquivo salvo.
     */
    private ResponseEntity<byte[]> arquivo(byte[] conteudo, String nome, String tipo) {
        String semAcento = java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", "").replace("\"", "");
        String codificado = java.net.URLEncoder.encode(nome, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + semAcento + "\"; filename*=UTF-8''" + codificado)
                .contentType(org.springframework.http.MediaType.parseMediaType(tipo))
                .body(conteudo);
    }
}
