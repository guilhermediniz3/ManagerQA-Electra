package com.tester.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tester.entity.Regression;

/**
 * Rodadas de regressão.
 *
 * O filtro por período usa quatro consultas derivadas em vez de uma só com
 * {@code :parametro IS NULL}. A forma condensada funciona no banco em memória
 * dos testes e falha no Postgres, que recusa um parâmetro cujo tipo não
 * consegue deduzir — "could not determine data type of parameter". Foi assim
 * que o filtro por data chegou quebrado ao uso real: verde no teste, erro na
 * tela.
 *
 * Da mais recente para a mais antiga: numa listagem de regressões o que
 * interessa quase sempre é a rodada atual, não a do semestre passado.
 */
@Repository
public interface RegressionRepository extends JpaRepository<Regression, Long> {

    List<Regression> findAllByOrderByDataDescIdDesc();

    List<Regression> findByDataBetweenOrderByDataDescIdDesc(LocalDate de, LocalDate ate);

    List<Regression> findByDataGreaterThanEqualOrderByDataDescIdDesc(LocalDate de);

    List<Regression> findByDataLessThanEqualOrderByDataDescIdDesc(LocalDate ate);
}
