package com.tester.controller;
import com.tester.service.CaseMediaService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/cases/{caseId}/media")
public class CaseMediaController {
    private final CaseMediaService media;
    public CaseMediaController(CaseMediaService media){this.media=media;}
    @GetMapping public List<CaseMediaService.View> list(@PathVariable long caseId){return media.list(caseId);}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public CaseMediaService.View save(@PathVariable long caseId,@RequestParam String field,@RequestParam MultipartFile file) throws java.io.IOException{return media.save(caseId,field,file);}
    @GetMapping("/{id}") public ResponseEntity<ByteArrayResource> content(@PathVariable long caseId,@PathVariable UUID id){
        var view=media.list(caseId).stream().filter(v->v.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Arquivo não encontrado."));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(view.mime())).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(new ByteArrayResource(media.bytes(caseId,id)));
    }
    @DeleteMapping("/{id}") public void delete(@PathVariable long caseId,@PathVariable UUID id){media.delete(caseId,id);}
}
