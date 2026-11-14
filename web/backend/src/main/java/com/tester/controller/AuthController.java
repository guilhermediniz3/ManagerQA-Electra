package com.tester.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tester.dto.LoginRequestDTO;
import com.tester.dto.RegisterRequestDTO;
import com.tester.dto.ResponseDTO;
import com.tester.entity.User;
import com.tester.infra.security.TokenService;
import com.tester.repository.UserRepository;

@RestController
@RequestMapping("/auth")



public class AuthController {

	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final UserRepository repository;

	@Autowired
	public AuthController(UserRepository repository, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequestDTO body) {
	    User user = this.repository.findByEmail(body.email())
	            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

	    // Verifica se o usuário está ativo
	    if (!user.isActive()) {
	        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                .body(java.util.Map.of("message", "Esta conta está inativa. Procure um administrador."));
	    }

	    // Verifica se a senha está correta
	    if (passwordEncoder.matches(body.password(), user.getPassword())) {
	        String token = this.tokenService.generationToken(user);
	        return ResponseEntity.ok(new ResponseDTO(user.getName(), token));
	    }

	    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	            .body(java.util.Map.of("message", "E-mail ou senha inválidos."));
	}
    @PostMapping("/refresh")
    public ResponseEntity<ResponseDTO> refresh(@org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        if(user==null || !user.isActive()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(new ResponseDTO(user.getName(),tokenService.generationToken(user)));
    }
	/**
	 * Cadastro aberto.
	 *
	 * Qualquer pessoa cria a própria conta por aqui. Fechar isso atrás de um
	 * administrador já foi tentado e custou caro: numa atualização em que a
	 * conta padrão não pôde ser criada, não sobrou caminho nenhum para entrar
	 * no sistema — e a tela ainda deixava preencher o formulário inteiro para
	 * recusar no fim.
	 */
	@PostMapping("/register")
	public ResponseEntity<?> register(@jakarta.validation.Valid @RequestBody RegisterRequestDTO body) {
		Optional<User> user = this.repository.findByEmail(body.email());
		if (user.isPresent()) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(java.util.Map.of("message",
							"Já existe uma conta com este e-mail. Use \"Já tenho acesso\" para entrar."));
		}
		User newUser = new User();
		newUser.setPassword(passwordEncoder.encode(body.password()));
		newUser.setEmail(body.email());
		newUser.setName(body.name());
		newUser.setActive(true);
		this.repository.save(newUser);

		String token = this.tokenService.generationToken(newUser);
		return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseDTO(newUser.getName(), token));
	}
	
	
}
