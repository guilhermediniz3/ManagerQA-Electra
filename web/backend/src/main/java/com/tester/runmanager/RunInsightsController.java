package com.tester.runmanager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.tester.runmanager.RunFailureService.DemandaDTO;
import com.tester.runmanager.RunInsightsService.FalhaDTO;
import com.tester.runmanager.RunInsightsService.Metricas;

/** Métricas, falhas e o que se faz com elas. */
@RestController
@RequestMapping("/runmanager")
public class RunInsightsController {

    private final RunInsightsService insights;
    private final RunFailureService falhas;
    private final VimeoUploader vimeo;

    public RunInsightsController(RunInsightsService insights, RunFailureService falhas,
                                 VimeoUploader vimeo) {
        this.insights = insights;
        this.falhas = falhas;
        this.vimeo = vimeo;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Metricas> metricas(
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate ate) {
        return ResponseEntity.ok(insights.metricas(de, ate));
    }

    @GetMapping("/failures")
    public ResponseEntity<List<FalhaDTO>> falhas(@RequestParam(required = false) Long executionId) {
        return ResponseEntity.ok(insights.falhas(executionId));
    }

    /** A tela esconde o botão de vídeo quando não há token do Vimeo guardado. */
    @GetMapping("/failures/vimeo-status")
    public ResponseEntity<Map<String, Boolean>> vimeoConfigurado() {
        return ResponseEntity.ok(Map.of("configured", vimeo.configurado()));
    }

    @PostMapping("/failures/{id}/vimeo")
    public ResponseEntity<Map<String, String>> enviarVideo(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("url", falhas.enviarVideo(id)));
    }

    @PostMapping("/failures/{id}/jira")
    public ResponseEntity<Map<String, String>> criarDemanda(@PathVariable Long id,
                                                            @RequestBody DemandaDTO dto) {
        return ResponseEntity.ok(Map.of("key", falhas.criarDemanda(id, dto)));
    }
}
