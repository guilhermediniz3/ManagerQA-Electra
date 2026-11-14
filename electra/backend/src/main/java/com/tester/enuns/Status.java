package com.tester.enuns;

import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {

	EM_PROGRESSO("em progresso"), CONCLUIDA("Aprovado"), IMPEDIMENTO("Impedimento"), RETORNO("Falhou");

	private final String descricao;

	// Construtor para atribuir a descrição ao enum
	Status(String descricao) {
		this.descricao = descricao;
	}

	// Método para acessar a descrição
	public String getDescricao() {
		return descricao;
	}

	
}
