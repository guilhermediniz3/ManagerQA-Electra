package com.tester.integration.ai;
import java.util.List;import org.springframework.web.bind.annotation.*;import com.tester.dto.TestCaseDTO;
@RestController @RequestMapping("/api/ai") public class AiController {
 private final AiService service;private final LocalModelService locais;public AiController(AiService service,LocalModelService locais){this.service=service;this.locais=locais;}
 public record Generate(String provider,String description){}public record Selected(Long suiteId,List<AiService.Draft> cases){}
 /** Estado e gestao da IA que roda na propria maquina. */
 @GetMapping("/local")public java.util.Map<String,Object> local(){
  boolean ligado=locais.disponivel();
  // Modelo que veio no instalador e ainda nao foi escolhido por ninguém e
  // adotado aqui: sem escolha a geração recusaria, e a escolha seria entre uma
  // alternativa só.
  if(ligado)locais.adotarModeloSeNecessario();
  return java.util.Map.of("available",ligado,"suggested",locais.sugeridos(),
   "installed",ligado?locais.instalados():java.util.List.of());}
 @PostMapping("/local/models")public LocalModelService.Modelo baixarLocal(@RequestBody java.util.Map<String,String> corpo){return locais.baixar(corpo.get("name"));}
 @DeleteMapping("/local/models/{nome}")public void removerLocal(@PathVariable String nome){locais.remover(nome);}
 @GetMapping("/models")public List<String> models(@RequestParam String provider){return service.models(provider);}
 @PostMapping("/summary")public AiService.Summary summary(@RequestBody Generate input){return service.summarize(input.provider(),input.description());}
 /** Conteudo livre para o editor de documentacao. */
 @PostMapping("/conteudo")public java.util.Map<String,String> conteudo(@RequestBody Generate input){return java.util.Map.of("html",service.conteudo(input.provider(),input.description()));}
 @PostMapping("/preview")public List<AiService.Draft> preview(@RequestBody Generate input){return service.preview(input.provider(),input.description());}
 @PostMapping("/import")public List<TestCaseDTO> importSelected(@RequestBody Selected input){return service.importSelected(input.suiteId(),input.cases());}
}
