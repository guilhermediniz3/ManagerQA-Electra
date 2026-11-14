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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tester.dto.GherkinFeatureDTO;
import com.tester.entity.User;
import com.tester.service.GherkinFeatureService;

@RestController
@RequestMapping("/gherkin")
public class GherkinFeatureController {

    private final GherkinFeatureService service;

    public GherkinFeatureController(GherkinFeatureService service) {
        this.service = service;
    }

    @GetMapping("/testplan/{testPlanId}")
    public ResponseEntity<List<GherkinFeatureDTO>> porPlano(@PathVariable Long testPlanId) {
        return ResponseEntity.ok(service.porPlano(testPlanId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GherkinFeatureDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscar(id));
    }

    @PostMapping
    public ResponseEntity<GherkinFeatureDTO> criar(@RequestBody GherkinFeatureDTO dto,
                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.criar(dto, user == null ? null : user.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GherkinFeatureDTO> atualizar(@PathVariable Long id,
                                                       @RequestBody GherkinFeatureDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    /** Converte a suíte e guarda o resultado na aba Gherkin do plano. */
    @PostMapping("/from-suite/{suiteId}")
    public ResponseEntity<GherkinFeatureDTO> daSuite(@PathVariable Long suiteId,
                                                     @RequestParam(required = false) String provider,
                                                     @RequestParam(required = false) String nome,
                                                     @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                service.daSuite(suiteId, provider, nome, user == null ? null : user.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
