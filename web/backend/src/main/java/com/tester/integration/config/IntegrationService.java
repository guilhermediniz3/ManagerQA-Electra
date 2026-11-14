package com.tester.integration.config;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class IntegrationService {
 public static final List<String> PROVIDERS=List.of("jira","openai","claude","gemini","groq","ollama","vimeo");
 /** Roda na propria maquina: nao ha chave de API para exigir. */
 public static final List<String> SEM_CHAVE=List.of("ollama");
 private final IntegrationRepository repository; private final SecretCipher cipher;
 public IntegrationService(IntegrationRepository repository,SecretCipher cipher){this.repository=repository;this.cipher=cipher;}
 public record Input(String baseUrl,String username,String model,String secret,String squadField,String sprintField,String testerField,boolean defaultProvider){}
 public record View(String provider,String baseUrl,String username,String model,String maskedSecret,String status,String squadField,String sprintField,String testerField,boolean defaultProvider){}
 private void valid(String provider){if(!PROVIDERS.contains(provider)) throw new IllegalArgumentException("Provider inválido.");}
 public IntegrationSetting setting(String provider){valid(provider);return repository.findById(provider).orElseGet(()->{var s=new IntegrationSetting();s.provider=provider;return s;});}
 public View view(IntegrationSetting s){return new View(s.provider,s.baseUrl,s.username,s.model,s.encryptedSecret==null?"":"••••••••"+s.secretSuffix,s.status,s.squadField,s.sprintField,s.testerField,s.defaultProvider);}
 public List<View> list(){return PROVIDERS.stream().map(this::setting).map(this::view).toList();}
 @Transactional public View save(String provider,Input input){
  var s=setting(provider);
  if(provider.equals("jira")) {
   URI uri;try {uri=URI.create(input.baseUrl());}catch(Exception e){throw new IllegalArgumentException("URL Jira inválida.");}
   if(!"https".equals(uri.getScheme()) || uri.getHost()==null || !uri.getHost().endsWith(".atlassian.net") || uri.getUserInfo()!=null || uri.getPort()!=-1 || (uri.getPath()!=null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("Use a URL HTTPS do seu site Jira Cloud.");
   if(input.username()==null || input.username().isBlank()) throw new IllegalArgumentException("Informe o e-mail da integração.");
   s.baseUrl="https://"+uri.getHost();
   s.username=input.username();s.squadField=field(input.squadField());s.sprintField=field(input.sprintField());s.testerField=field(input.testerField());
  }
  // Endereco de provider local: so o Jira tinha esse campo gravado, entao o que
  // a tela mandava para os demais se perdia — quem apontasse a IA local para
  // outra porta salvava e continuava batendo no endereco padrao.
  if(SEM_CHAVE.contains(provider)){
   String base=input.baseUrl()==null?null:input.baseUrl().trim();
   if(base!=null&&!base.isBlank()){
    URI uri;try{uri=URI.create(base);}catch(Exception e){throw new IllegalArgumentException("Endereço inválido.");}
    if(uri.getHost()==null||!(uri.getScheme().equals("http")||uri.getScheme().equals("https")))
     throw new IllegalArgumentException("Informe um endereço http:// ou https://.");
    s.baseUrl=base.replaceAll("/+$","");
   } else s.baseUrl=null;
  }
  s.model=input.model();
  if(input.secret()!=null && !input.secret().isBlank()){if(input.secret().length()<8)throw new IllegalArgumentException("A credencial deve ter pelo menos 8 caracteres.");s.encryptedSecret=cipher.encrypt(input.secret());s.secretSuffix=input.secret().substring(Math.max(0,input.secret().length()-4));}
  if(input.defaultProvider() && !provider.equals("jira")) for(var other:repository.findAll()){other.defaultProvider=false;repository.save(other);}
  s.defaultProvider=!provider.equals("jira") && input.defaultProvider();// Provider local nao tem credencial: marca-lo como "nao configurado" so por
  // nao ter chave o faria parecer inutilizavel quando esta pronto para uso.
  s.status=(s.encryptedSecret==null&&!SEM_CHAVE.contains(provider))?"NOT_CONFIGURED":"UNTESTED";
  return view(repository.save(s));
 }
 private String field(String value){if(value==null || value.isBlank())return null;if(!java.util.Set.of("project","components").contains(value) && !value.matches("customfield_[0-9]+"))throw new IllegalArgumentException("Selecione um campo customizado descoberto no Jira.");return value;}
 public String secret(String provider){var s=setting(provider);if(s.encryptedSecret==null){if(SEM_CHAVE.contains(provider))return "";throw new IllegalStateException("Integração não configurada: "+provider);}return cipher.decrypt(s.encryptedSecret);}
 @Transactional public View status(String provider,boolean connected){var s=setting(provider);s.status=connected?"CONNECTED":"ERROR";return view(repository.save(s));}
}
