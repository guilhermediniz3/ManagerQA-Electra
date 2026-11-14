package com.tester.integration.ai;
/** Um provider de IA visto pelo sistema. `texto` serve aos usos que pedem prosa
 *  (documentacao, Gherkin); `generate` e `summarize` continuam com seus prompts proprios. */
public interface TestCaseAiProvider {String id();String generate(String description);String summarize(String description);String texto(String prompt,int maxTokens);java.util.List<String> models();void testConnection();}
