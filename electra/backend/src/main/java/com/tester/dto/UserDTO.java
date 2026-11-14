package com.tester.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

/**
 * Uma conta do sistema, como a tela a vê.
 *
 * A senha só entra: nunca sai numa resposta, nem mascarada.
 */
public class UserDTO {

	private Long id;
	private String name;

	@Email(message = "O e-mail deve ser válido")
	@NotEmpty(message = "O e-mail é obrigatório")
	private String email;

	/**
	 * Vazia na edição significa "mantenha a que já existe".
	 *
	 * Por isso a obrigatoriedade não mora aqui: exigi-la no formato fazia toda
	 * atualização de conta responder 400, mesmo quando só se queria trocar o
	 * nome. Quem cobra a senha é o serviço, e só na criação.
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;

	private Boolean active;

	public UserDTO() {
	}

	public UserDTO(Long id, String name, String email, String password, boolean active) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.password = password;
		this.active = active;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }

	public Boolean isActive() { return active; }
	public void setActive(Boolean active) { this.active = active; }
}
