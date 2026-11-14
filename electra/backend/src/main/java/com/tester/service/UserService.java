package com.tester.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tester.dto.UserDTO;
import com.tester.entity.User;
import com.tester.repository.UserRepository;

/**
 * Contas do sistema: quem entra.
 *
 * Uma regra existe para evitar um estado do qual não se sai pela tela: ninguém
 * exclui a própria conta. Sem ela, um clique custa uma ida ao banco por fora
 * para destravar o acesso.
 */
@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder encoder;

	public UserService(UserRepository userRepository, PasswordEncoder encoder) {
		this.userRepository = userRepository;
		this.encoder = encoder;
	}

	// ── Leitura ──────────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<UserDTO> getAllUsers() {
		return userRepository.findAllByOrderByNameAsc().stream().map(UserService::paraDTO).toList();
	}

	@Transactional(readOnly = true)
	public Optional<UserDTO> getUserById(Long id) {
		return userRepository.findById(id).map(UserService::paraDTO);
	}

	@Transactional(readOnly = true)
	public Optional<UserDTO> getUserByEmail(String email) {
		return userRepository.findByEmail(email).map(UserService::paraDTO);
	}

	// ── Escrita ──────────────────────────────────────────────────────────────

	@Transactional
	public UserDTO createUser(UserDTO pedido) {
		if (pedido.getPassword() == null || pedido.getPassword().isEmpty())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma senha para a nova conta.");
		exigirEmailLivre(pedido.getEmail(), null);

		User conta = new User(pedido.getName(), pedido.getEmail().trim(),
				encoder.encode(pedido.getPassword()), pedido.isActive() == null || pedido.isActive());
		return paraDTO(userRepository.save(conta));
	}

	@Transactional
	public UserDTO updateUser(Long id, UserDTO pedido) {
		User conta = exigir(id);
		exigirEmailLivre(pedido.getEmail(), id);

		conta.setName(pedido.getName());
		conta.setEmail(pedido.getEmail().trim());
		if (pedido.getPassword() != null && !pedido.getPassword().isEmpty())
			conta.setPassword(encoder.encode(pedido.getPassword()));
		if (pedido.isActive() != null) conta.setActive(pedido.isActive());
		return paraDTO(userRepository.save(conta));
	}

	@Transactional
	public UserDTO patchUser(Long id, UserDTO pedido) {
		User conta = exigir(id);
		if (pedido.getName() != null) conta.setName(pedido.getName());
		if (pedido.getEmail() != null) {
			exigirEmailLivre(pedido.getEmail(), id);
			conta.setEmail(pedido.getEmail().trim());
		}
		if (pedido.getPassword() != null && !pedido.getPassword().isEmpty())
			conta.setPassword(encoder.encode(pedido.getPassword()));
		if (pedido.isActive() != null) conta.setActive(pedido.isActive());
		return paraDTO(userRepository.save(conta));
	}

	@Transactional
	public void deleteUser(Long id, User quemPede) {
		User conta = exigir(id);
		if (quemPede != null && quemPede.getId() != null && quemPede.getId().equals(id))
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Você não pode excluir a sua própria conta.");
		userRepository.delete(conta);
	}

	// ── Apoio ────────────────────────────────────────────────────────────────

	private User exigir(Long id) {
		return userRepository.findById(id).orElseThrow(
				() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
	}

	private void exigirEmailLivre(String email, Long idAtual) {
		if (email == null || email.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o e-mail.");
		userRepository.findByEmail(email.trim()).ifPresent(outra -> {
			if (idAtual == null || !outra.getId().equals(idAtual))
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"Já existe uma conta com este e-mail.");
		});
	}

	public static UserDTO paraDTO(User conta) {
		return new UserDTO(conta.getId(), conta.getName(), conta.getEmail(), null, conta.isActive());
	}
}
