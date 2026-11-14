package com.tester.integration.ai;
import org.springframework.stereotype.Component;
@Component public class GeminiTestCaseProvider implements TestCaseAiProvider {
 private final AiTransport transport;public GeminiTestCaseProvider(AiTransport transport){this.transport=transport;}
 public String id(){return "gemini";}public String generate(String description){return transport.generate(id(),description);}public String summarize(String description){return transport.summarize(id(),description);}public String texto(String prompt,int maxTokens){return transport.texto(id(),prompt,maxTokens);}public java.util.List<String> models(){return transport.models(id());}public void testConnection(){transport.test(id());}
}
