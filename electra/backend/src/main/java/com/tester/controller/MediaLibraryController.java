package com.tester.controller;
import com.tester.service.CaseMediaService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/media-library")
public class MediaLibraryController {
    private final CaseMediaService media;
    public MediaLibraryController(CaseMediaService media){this.media=media;}
    @GetMapping public CaseMediaService.Library list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return media.library(page,size);}
    @DeleteMapping public Map<String,Integer> clear(@RequestParam String confirmation){
        if(!"LIMPAR".equals(confirmation))throw new IllegalArgumentException("Confirme a limpeza de mídias.");
        return Map.of("deleted",media.clear());
    }
}
