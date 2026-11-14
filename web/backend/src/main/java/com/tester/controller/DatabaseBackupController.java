package com.tester.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tester.service.DatabaseBackupService;

/**
 * Backup, restauração e limpeza do banco, pela tela.
 *
 * As três operações ficam atrás de sessão autenticada como o resto do sistema.
 * Duas delas destroem dado, e por isso a tela pede confirmação antes — aqui não
 * há como distinguir um clique deliberado de um acidental.
 */
@RestController
@RequestMapping("/database")
public class DatabaseBackupController {

    private final DatabaseBackupService service;

    public DatabaseBackupController(DatabaseBackupService service) {
        this.service = service;
    }

    /** O que há no banco hoje, para a tela dizer o que está em jogo. */
    @GetMapping("/summary")
    public ResponseEntity<List<DatabaseBackupService.Contagem>> resumo() {
        return ResponseEntity.ok(service.resumo());
    }

    /** O arquivo .sql, já com nome datado — backup sem data vira pasta de arquivos iguais. */
    @GetMapping("/backup")
    public ResponseEntity<byte[]> exportar() {
        byte[] conteudo = service.exportar().getBytes(StandardCharsets.UTF_8);
        String nome = "qa-manager-backup-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".sql";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.valueOf("application/sql"))
                .body(conteudo);
    }

    public record Arquivo(String conteudo) {}

    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> importar(@RequestBody Arquivo arquivo) {
        int comandos = service.importar(arquivo == null ? null : arquivo.conteudo());
        return ResponseEntity.ok(Map.of("comandos", comandos));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Long>> zerar() {
        return ResponseEntity.ok(service.zerar());
    }

    /**
     * Apaga o banco inteiro, contas e credenciais incluídas.
     *
     * Quem chamar perde a própria sessão: a conta que a sustentava some no meio
     * da operação. O administrador padrão volta na próxima subida do
     * aplicativo, e é por isso que isto não tranca ninguém do lado de fora.
     */
    @PostMapping("/wipe")
    public ResponseEntity<Map<String, Long>> apagarTudo() {
        return ResponseEntity.ok(service.apagarTudo());
    }
}
