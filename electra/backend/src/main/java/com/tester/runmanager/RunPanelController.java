package com.tester.runmanager;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.tester.entity.User;
import com.tester.runmanager.RunPanelDTOs.*;

/** Painel de disparo do RunManager: containers, cards, execuções e terminal. */
@RestController
@RequestMapping("/runmanager")
public class RunPanelController {

    private final RunPanelService service;

    public RunPanelController(RunPanelService service) {
        this.service = service;
    }

    /**
     * A tela de disparo pede só o que está ativo; a de cadastro pede tudo,
     * senão não haveria como reativar o que foi inativado.
     */
    @GetMapping("/containers")
    public ResponseEntity<List<ContainerDTO>> containers(
            @RequestParam(defaultValue = "false") boolean todos) {
        return ResponseEntity.ok(service.listar(!todos));
    }

    @PostMapping("/containers")
    public ResponseEntity<ContainerDTO> salvarContainer(@RequestBody ContainerDTO dto) {
        return ResponseEntity.ok(service.salvarContainer(dto));
    }

    @PutMapping("/containers/{id}")
    public ResponseEntity<ContainerDTO> atualizarContainer(@PathVariable Long id,
                                                           @RequestBody ContainerDTO dto) {
        return ResponseEntity.ok(service.salvarContainer(new ContainerDTO(id, dto.title(),
                dto.description(), dto.active(), dto.position(), List.of())));
    }

    @DeleteMapping("/containers/{id}")
    public ResponseEntity<Void> excluirContainer(@PathVariable Long id) {
        service.excluirContainer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cards")
    public ResponseEntity<CardDTO> salvarCard(@RequestBody CardDTO dto) {
        return ResponseEntity.ok(service.salvarCard(dto));
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<CardDTO> atualizarCard(@PathVariable Long id, @RequestBody CardDTO dto) {
        return ResponseEntity.ok(service.salvarCard(new CardDTO(id, dto.containerId(), dto.title(),
                dto.environmentId(), null, false, dto.systemId(), null, dto.versionId(), null,
                dto.tagId(), null, dto.script(), dto.active(), dto.position())));
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> excluirCard(@PathVariable Long id) {
        service.excluirCard(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cards/{id}/run")
    public ResponseEntity<ExecutionDTO> dispararCard(@PathVariable Long id,
                                                     @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.dispararCard(id, quem(user)));
    }

    @PostMapping("/run")
    public ResponseEntity<ExecutionDTO> dispararAvulso(@RequestBody DisparoDTO dto,
                                                       @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.dispararAvulso(dto, quem(user)));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<ExecutionDTO>> historico() {
        return ResponseEntity.ok(service.historico());
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<ExecutionDTO> execucao(@PathVariable Long id) {
        return ResponseEntity.ok(service.execucao(id));
    }

    /**
     * Terminal. A tela manda o tamanho que já tem e recebe só o que veio depois
     * — um log de teste passa de megabytes, e reenviá-lo a cada segundo
     * entupiria a tela e o servidor.
     */
    @GetMapping("/executions/{id}/log")
    public ResponseEntity<LogDTO> log(@PathVariable Long id,
                                      @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(service.log(id, offset));
    }

    @PostMapping("/executions/{id}/cancel")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        service.cancelar(id);
        return ResponseEntity.noContent().build();
    }

    private static String quem(User user) {
        return user == null ? "Sistema" : user.getName();
    }
}
