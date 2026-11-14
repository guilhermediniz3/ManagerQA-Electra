package com.tester.runmanager;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.tester.runmanager.RunCatalogDTOs.*;

/** Cadastro do RunManager: sistemas, versões, ambientes e marcações. */
@RestController
@RequestMapping("/runmanager/catalog")
public class RunCatalogController {

    private final RunCatalogService service;

    public RunCatalogController(RunCatalogService service) {
        this.service = service;
    }

    /** Tudo de uma vez: é o que a tela precisa para montar os seletores. */
    @GetMapping
    public ResponseEntity<CatalogoDTO> catalogo() {
        return ResponseEntity.ok(service.catalogo());
    }

    @GetMapping("/systems")
    public ResponseEntity<List<SystemDTO>> sistemas() {
        return ResponseEntity.ok(service.listarSistemas());
    }

    @PostMapping("/systems")
    public ResponseEntity<SystemDTO> salvarSistema(@RequestBody SystemDTO dto) {
        return ResponseEntity.ok(service.salvarSistema(dto));
    }

    @PutMapping("/systems/{id}")
    public ResponseEntity<SystemDTO> atualizarSistema(@PathVariable Long id, @RequestBody SystemDTO dto) {
        return ResponseEntity.ok(service.salvarSistema(comId(dto, id)));
    }

    @DeleteMapping("/systems/{id}")
    public ResponseEntity<Void> excluirSistema(@PathVariable Long id) {
        service.excluirSistema(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/versions")
    public ResponseEntity<VersionDTO> salvarVersao(@RequestBody VersionDTO dto) {
        return ResponseEntity.ok(service.salvarVersao(dto));
    }

    @PutMapping("/versions/{id}")
    public ResponseEntity<VersionDTO> atualizarVersao(@PathVariable Long id, @RequestBody VersionDTO dto) {
        return ResponseEntity.ok(service.salvarVersao(new VersionDTO(id, dto.systemId(), dto.name(),
                dto.key(), dto.active(), dto.position())));
    }

    @DeleteMapping("/versions/{id}")
    public ResponseEntity<Void> excluirVersao(@PathVariable Long id) {
        service.excluirVersao(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/environments")
    public ResponseEntity<EnvironmentDTO> salvarAmbiente(@RequestBody EnvironmentDTO dto) {
        return ResponseEntity.ok(service.salvarAmbiente(dto));
    }

    @PutMapping("/environments/{id}")
    public ResponseEntity<EnvironmentDTO> atualizarAmbiente(@PathVariable Long id, @RequestBody EnvironmentDTO dto) {
        return ResponseEntity.ok(service.salvarAmbiente(new EnvironmentDTO(id, dto.name(), dto.key(),
                dto.production(), dto.variables(), dto.active(), dto.position())));
    }

    @DeleteMapping("/environments/{id}")
    public ResponseEntity<Void> excluirAmbiente(@PathVariable Long id) {
        service.excluirAmbiente(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tags")
    public ResponseEntity<TagDTO> salvarTag(@RequestBody TagDTO dto) {
        return ResponseEntity.ok(service.salvarTag(dto));
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<TagDTO> atualizarTag(@PathVariable Long id, @RequestBody TagDTO dto) {
        return ResponseEntity.ok(service.salvarTag(new TagDTO(id, dto.name(), dto.key(),
                dto.description(), dto.active(), dto.position())));
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> excluirTag(@PathVariable Long id) {
        service.excluirTag(id);
        return ResponseEntity.noContent().build();
    }

    private static SystemDTO comId(SystemDTO dto, Long id) {
        return new SystemDTO(id, dto.name(), dto.key(), dto.repository(), dto.branch(),
                dto.localPath(), dto.selectionScript(), dto.variables(), dto.versionFlag(),
                dto.active(), dto.position(), dto.versions());
    }
}
