package com.tester.runmanager;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.tester.entity.User;
import com.tester.runmanager.ProjectCatalogService.LeituraDTO;
import com.tester.runmanager.ProjectCatalogService.ManualDTO;
import com.tester.runmanager.RunPanelDTOs.ExecutionDTO;
import com.tester.runmanager.RunSelectionService.SelecaoDTO;

/** Casos de teste por módulo, estrutura do projeto e execução por seleção. */
@RestController
@RequestMapping("/runmanager/project")
public class RunProjectController {

    private final ProjectCatalogService projeto;
    private final RunSelectionService selecao;

    public RunProjectController(ProjectCatalogService projeto, RunSelectionService selecao) {
        this.projeto = projeto;
        this.selecao = selecao;
    }

    /** O que foi lido da última vez — não vasculha o disco a cada abertura. */
    @GetMapping("/{systemId}")
    public ResponseEntity<LeituraDTO> casos(@PathVariable Long systemId) {
        return ResponseEntity.ok(projeto.casos(systemId));
    }

    /** Releitura é ação explícita, com o nome de quem pediu guardado. */
    @PostMapping("/{systemId}/scan")
    public ResponseEntity<LeituraDTO> reler(@PathVariable Long systemId,
                                            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projeto.reler(systemId, user == null ? "Sistema" : user.getName()));
    }

    @PostMapping("/cases")
    public ResponseEntity<ManualDTO> salvarManual(@RequestBody ManualDTO dto) {
        return ResponseEntity.ok(projeto.salvarManual(dto));
    }

    @PutMapping("/cases/{id}")
    public ResponseEntity<ManualDTO> atualizarManual(@PathVariable Long id, @RequestBody ManualDTO dto) {
        return ResponseEntity.ok(projeto.salvarManual(new ManualDTO(id, dto.systemId(),
                dto.moduleKey(), dto.moduleName(), dto.version(), dto.code(), dto.title(),
                dto.script(), dto.tags(), dto.active())));
    }

    @DeleteMapping("/cases/{id}")
    public ResponseEntity<Void> excluirManual(@PathVariable Long id) {
        projeto.excluirManual(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/run-selection")
    public ResponseEntity<ExecutionDTO> dispararSelecao(@RequestBody SelecaoDTO dto,
                                                        @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(selecao.disparar(dto, user == null ? "Sistema" : user.getName()));
    }
}
