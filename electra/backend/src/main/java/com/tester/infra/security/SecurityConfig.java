package com.tester.infra.security;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
public class SecurityConfig {
 private final SecurityFilter filter;
 private final RotaDeTela telas;
 public SecurityConfig(SecurityFilter filter,RotaDeTela telas) { this.filter=filter;this.telas=telas; }
 @Bean public PasswordEncoder passwordEncoder() { return new FlexiblePasswordEncoder(); }

 @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
  return http.cors(c -> {}).csrf(c -> c.disable())
   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
    .requestMatchers(HttpMethod.POST,"/auth/login","/auth/register","/auth/reset-local-password").permitAll()
    .requestMatchers("/actuator/health","/error").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/drive/callback").permitAll()
    // A interface carrega sem sessão: é nela que se digita a senha, e o app
    // desktop abre esta mesma URL ao iniciar. Só a casca — todo o conteúdo
    // vem das APIs, que seguem exigindo autenticação.
    .requestMatchers(telas).permitAll()
    // Tudo o mais autenticado: endpoint novo nasce protegido, sem depender de
    // alguém lembrar de acrescentá-lo a uma lista de rotas.
    .anyRequest().authenticated())
   .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> {r.setStatus(401);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Autenticação necessária\"}");})
    .accessDeniedHandler((q,r,x) -> {r.setStatus(403);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Acesso negado\"}");}))
   .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
 }
}
