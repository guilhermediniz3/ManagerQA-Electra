package com.tester.controller;

import java.util.List;
import java.util.Map;

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

import com.tester.dto.SuiteTemplateDTO;
import com.tester.entity.User;
import com.tester.service.SuiteTemplateService;

@RestController
@RequestMapping("/suite-templates")
public class SuiteTemplateController {

    private final SuiteTemplateService service;

    public SuiteTemplateController(SuiteTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SuiteTemplateDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    /** Lista enxuta para o seletor dentro da suíte de teste. */
    @GetMapping("/disponiveis")
    public ResponseEntity<List<SuiteTemplateDTO>> disponiveis() {
        return ResponseEntity.ok(service.disponiveis());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuiteTemplateDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    @PostMapping
    public ResponseEntity<SuiteTemplateDTO> criar(@RequestBody SuiteTemplateDTO dto,
                                                  @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.criar(dto, user == null ? null : user.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuiteTemplateDTO> atualizar(@PathVariable Long id, @RequestBody SuiteTemplateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /** Transforma uma suíte existente em template reutilizável. */
    @PostMapping("/from-suite/{suiteId}")
    public ResponseEntity<SuiteTemplateDTO> aPartirDaSuite(@PathVariable Long suiteId,
                                                           @RequestBody(required = false) SuiteTemplateDTO dto,
                                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.fromSuite(suiteId, dto, user == null ? null : user.getName()));
    }

    /** Acrescenta os casos do template à suíte informada. */
    @PostMapping("/{templateId}/import/{suiteId}")
    public ResponseEntity<Map<String, Object>> importar(@PathVariable Long templateId, @PathVariable Long suiteId) {
        int importados = service.importInto(suiteId, templateId);
        return ResponseEntity.ok(Map.of("imported", importados));
    }
}
