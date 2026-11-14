package com.tester.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tester.dto.RegressionDTO;
import com.tester.dto.RegressionItemDTO;
import com.tester.entity.User;
import com.tester.service.RegressionService;

@RestController
@RequestMapping("/regressions")
public class RegressionController {

    private final RegressionService service;

    public RegressionController(RegressionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RegressionDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate ate) {
        return ResponseEntity.ok(service.listar(de, ate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegressionDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    /**
     * Módulos das demandas que fecharam no período — é o que alimenta o botão de
     * buscar aprovados. Não grava nada: devolve a sugestão para a tela montar.
     */
    @GetMapping("/aprovados")
    public ResponseEntity<List<RegressionItemDTO>> aprovados(
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate ate) {
        return ResponseEntity.ok(service.aprovados(de, ate));
    }

    @PostMapping
    public ResponseEntity<RegressionDTO> criar(@RequestBody RegressionDTO dto,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.criar(dto, user == null ? null : user.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegressionDTO> atualizar(@PathVariable Long id, @RequestBody RegressionDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<RegressionDTO> clonar(@PathVariable Long id,
                                                @RequestBody(required = false) RegressionDTO dto,
                                                @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.clonar(id, dto, user == null ? null : user.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
