package com.tester.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tester.service.SuiteDocService;

/**
 * As três formas de virar documentação a partir de uma suíte. Nenhuma grava
 * nada: devolvem o texto para a tela mostrar, deixar editar e então salvar como
 * card de documentação ou exportar.
 */
@RestController
@RequestMapping("/test-suites/{suiteId}/doc")
public class SuiteDocController {

    private final SuiteDocService service;

    public SuiteDocController(SuiteDocService service) {
        this.service = service;
    }

    /** Conteúdo exato dos casos, sem passar por IA. */
    @GetMapping("/exato")
    public ResponseEntity<SuiteDocService.Documento> exato(@PathVariable Long suiteId) {
        return ResponseEntity.ok(service.exato(suiteId));
    }

    @GetMapping("/ia")
    public ResponseEntity<SuiteDocService.Documento> comIa(@PathVariable Long suiteId,
                                                           @RequestParam(required = false) String provider) {
        return ResponseEntity.ok(service.comIa(suiteId, provider));
    }

    @GetMapping("/gherkin")
    public ResponseEntity<SuiteDocService.Documento> gherkin(@PathVariable Long suiteId,
                                                             @RequestParam(required = false) String provider) {
        return ResponseEntity.ok(service.gherkin(suiteId, provider));
    }
}
