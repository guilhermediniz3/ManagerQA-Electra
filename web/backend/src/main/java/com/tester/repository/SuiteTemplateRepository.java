package com.tester.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import com.tester.entity.SuiteTemplate;

@Repository
public interface SuiteTemplateRepository extends JpaRepository<SuiteTemplate, Long> {

    /** Usado pelo seletor dentro da suíte, que só oferece template ativo. */
    List<SuiteTemplate> findByActiveTrueOrderByNameAsc();
}
