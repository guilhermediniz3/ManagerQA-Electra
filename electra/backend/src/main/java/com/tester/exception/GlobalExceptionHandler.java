package com.tester.exception;
import java.util.Map;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.tester.integration.IntegrationException;
@RestControllerAdvice public class GlobalExceptionHandler {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
 private ResponseEntity<Map<String,String>> response(int status,String message){return ResponseEntity.status(status).body(Map.of("message",message));}
 @ExceptionHandler({ResourceNotFoundException.class,jakarta.persistence.EntityNotFoundException.class}) public ResponseEntity<?> missing(Exception e){return response(404,"Registro não encontrado.");}
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(Exception e){return response(400,e.getMessage()==null?"Dados inválidos.":e.getMessage());}
 @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> state(Exception e){return response(409,e.getMessage()==null?"Operação indisponível.":e.getMessage());}
 @ExceptionHandler(IntegrationException.class) public ResponseEntity<?> integration(Exception e){return response(502,e.getMessage());}
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> status(ResponseStatusException e){return response(e.getStatusCode().value(),e.getReason()==null?"Requisição rejeitada.":e.getReason());}
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<?> validation(MethodArgumentNotValidException e){return response(400,"Verifique os campos obrigatórios e seus formatos.");}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflict(Exception e){return response(409,"Registro duplicado ou vinculado a outros registros.");}
 /** Endereço que não existe é 404, não falha do servidor — sem isso um erro de digitação na URL vira 500. */
 @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class) public ResponseEntity<?> noResource(Exception e){return response(404,"Endereço não encontrado.");}
 /**
  * A resposta continua genérica de propósito — detalhe interno não vai para a
  * tela —, mas o erro precisa ficar registrado. Sem isso um 500 é indiagnosticável:
  * quem investiga vê só "Não foi possível concluir a operação." e não tem por onde
  * começar, nem no log do servidor.
  */
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) public ResponseEntity<?> uploadTooLarge(Exception e){return response(413,"A gravação excede o limite de 512 MB.");}
 @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(Exception e){
  log.error("Falha não tratada ao atender a requisição",e);
  return response(500,"Não foi possível concluir a operação.");
 }
}
