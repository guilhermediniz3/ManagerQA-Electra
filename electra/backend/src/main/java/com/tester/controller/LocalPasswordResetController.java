package com.tester.controller;

import com.tester.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/auth/reset-local-password")
public class LocalPasswordResetController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public LocalPasswordResetController(UserRepository users,PasswordEncoder passwords){this.users=users;this.passwords=passwords;}
    public record Input(@NotBlank @Email @Size(max=255) String email,
                        @NotEmpty String password,
                        @NotEmpty String confirmation) {}

    @PostMapping
    @Transactional
    public Map<String,String> reset(@Valid @RequestBody Input input,HttpServletRequest request) {
        var loopback=Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1");
        var hosts=Set.of("localhost","127.0.0.1","::1","[::1]","0:0:0:0:0:0:0:1");
        if(!loopback.contains(request.getRemoteAddr()) || !hosts.contains(request.getServerName().toLowerCase(java.util.Locale.ROOT))
                || request.getHeader("Forwarded")!=null || request.getHeader("X-Forwarded-For")!=null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A recuperação simples está disponível apenas no aplicativo ou em localhost, nesta máquina.");
        if(!input.password().equals(input.confirmation()))throw new IllegalArgumentException("As senhas não coincidem.");
        var user=users.findByEmail(input.email().strip()).orElseThrow(()->new IllegalArgumentException("Não existe conta com este e-mail neste banco local."));
        if(!user.isActive())throw new IllegalArgumentException("Esta conta está inativa. Reative-a em Usuários antes de redefinir a senha.");
        user.setPassword(passwords.encode(input.password()));users.save(user);
        return Map.of("message","Senha alterada. Entre com a nova senha.");
    }
}
