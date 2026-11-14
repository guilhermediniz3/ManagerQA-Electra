package com.tester.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tester.entity.DocCard;

@Repository
public interface DocCardRepository extends JpaRepository<DocCard, Long> {

    List<DocCard> findAllByOrderBySortOrderAscIdDesc();

    /** Só os ativos: é o acervo que o chat da IA pode consultar. */
    List<DocCard> findByActiveTrueOrderBySortOrderAscIdDesc();
}
