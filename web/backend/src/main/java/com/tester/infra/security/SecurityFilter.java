package com.tester.infra.security;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.tester.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
@Component
public class SecurityFilter extends OncePerRequestFilter {
 private final TokenService tokens; private final UserRepository users;
 public SecurityFilter(TokenService tokens,UserRepository users){this.tokens=tokens;this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
  String header=request.getHeader("Authorization");
  if(header!=null && header.startsWith("Bearer ")) {
   String email=tokens.validateToken(header.substring(7));
   if(email!=null) users.findByEmail(email).filter(u -> u.isActive()).ifPresent(u ->
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of())));
  }
  chain.doFilter(request,response);
 }
}
