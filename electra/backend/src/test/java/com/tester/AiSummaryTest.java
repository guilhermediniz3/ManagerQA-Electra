package com.tester;
import org.junit.jupiter.api.Test;import com.tester.integration.ai.AiService;import com.fasterxml.jackson.databind.ObjectMapper;import java.util.List;import static org.assertj.core.api.Assertions.*;
class AiSummaryTest {
 private AiService service(){return new AiService(List.of(),new ObjectMapper(),null,null,null);}
 @Test void readsStructuredSummary(){
  var summary=service().readSummary("claude","{\"summary\":\"Ajusta o disparo de mensagens.\",\"points\":[\"Validar template\",\"Conferir WABA\"],\"questions\":[\"Qual organização?\"]}");
  assertThat(summary.provider()).isEqualTo("claude");
  assertThat(summary.summary()).isEqualTo("Ajusta o disparo de mensagens.");
  assertThat(summary.points()).containsExactly("Validar template","Conferir WABA");
  assertThat(summary.questions()).containsExactly("Qual organização?");
 }
 @Test void readsSummaryInsideCodeFence(){
  assertThat(service().readSummary("openai","```json {\"summary\":\"Resumo curto.\",\"points\":[]} ```").summary()).isEqualTo("Resumo curto.");
 }
 @Test void fallsBackToPlainTextWhenNotJson(){
  var summary=service().readSummary("groq","A demanda ajusta a geração de boletos.");
  assertThat(summary.summary()).isEqualTo("A demanda ajusta a geração de boletos.");
  assertThat(summary.points()).isEmpty();
  assertThat(summary.questions()).isEmpty();
 }
 @Test void capsListsAndRejectsEmptyOutput(){
  var summary=service().readSummary("gemini","{\"summary\":\"Resumo.\",\"points\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\"],\"questions\":[\"a\",\"b\",\"c\",\"d\",\"e\"]}");
  assertThat(summary.points()).hasSize(6);
  assertThat(summary.questions()).hasSize(4);
  assertThatThrownBy(()->service().readSummary("claude","   ")).hasMessageContaining("não retornou um resumo");
 }
}
