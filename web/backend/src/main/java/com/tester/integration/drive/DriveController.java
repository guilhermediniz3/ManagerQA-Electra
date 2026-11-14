package com.tester.integration.drive;
import com.tester.entity.User;
import com.tester.service.TestCaseService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.Map;
@RestController
@RequestMapping("/api/drive")
public class DriveController {
    private final DriveService drive;private final TestCaseService cases;
    public DriveController(DriveService drive,TestCaseService cases){this.drive=drive;this.cases=cases;}
    @GetMapping("/settings") public DriveService.View settings(@AuthenticationPrincipal User user){return drive.view(user.getId());}
    @PutMapping("/settings") public DriveService.View save(@AuthenticationPrincipal User user,@RequestBody DriveService.Input input){return drive.save(user.getId(),input);}
    @PostMapping("/connect") public Map<String,String> connect(@AuthenticationPrincipal User user){return Map.of("url",drive.authorize(user.getId()));}
    @PostMapping("/disconnect") public void disconnect(@AuthenticationPrincipal User user){drive.disconnect(user.getId());}
    @PostMapping("/test") public Map<String,String> test(@AuthenticationPrincipal User user){return Map.of("folderName",drive.test(user.getId()));}
    @GetMapping(value="/callback",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@RequestParam(required=false) String state,@RequestParam(required=false) String code,@RequestParam(required=false) String error){
        boolean ok=true;String text="Conta Google conectada. Você pode fechar esta aba e voltar ao QA Manager.";
        try{drive.callback(state,code,error);}catch(Exception e){ok=false;text=e instanceof IllegalArgumentException?e.getMessage():"Não foi possível conectar. Volte ao QA Manager e confira as configurações.";}
        return ResponseEntity.status(ok?200:400).header("Cache-Control","no-store").header("Referrer-Policy","no-referrer").header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'")
            .body("<!doctype html><html lang=\"pt-BR\"><meta charset=\"utf-8\"><title>QA Manager · Google Drive</title><body style=\"font:18px sans-serif;padding:40px\"><h1>Google Drive</h1><p>"+org.springframework.web.util.HtmlUtils.htmlEscape(text)+"</p></body></html>");
    }
    @PostMapping(value="/cases/{id}/recording",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> upload(@AuthenticationPrincipal User user,@PathVariable Long id,@RequestParam("file") MultipartFile file) throws java.io.IOException {
        cases.findById(id);
        String type=file.getContentType();
        if(type==null || !(type.equals("video/webm") || type.equals("video/mp4")) || file.isEmpty() || file.getSize()>512L*1024*1024)
            throw new IllegalArgumentException("Envie uma gravação WebM ou MP4 de até 512 MB.");
        Path temp=Files.createTempFile("qa-recording-",".upload");
        try{file.transferTo(temp);return Map.of("url",drive.upload(user.getId(),id,temp,type));}
        finally{Files.deleteIfExists(temp);}
    }
}
