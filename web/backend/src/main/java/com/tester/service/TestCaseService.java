package com.tester.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tester.dto.TestCaseDTO;
import com.tester.entity.TestCaseEntity;
import com.tester.entity.TestSuite;
import com.tester.repository.TestCaseRepository;
import com.tester.repository.TestSuiteRepository;

@Service
@org.springframework.transaction.annotation.Transactional
public class TestCaseService {
	
	@Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private TestSuiteRepository testSuiteRepository;

    // Criar um novo TestCase
    public void validateSuitePlan(Long planId,Long suiteId) {var suite=testSuiteRepository.findById(suiteId).orElseThrow(()->new IllegalArgumentException("Suíte inválida."));if(!java.util.Objects.equals(suite.getTestPlan().getId(),planId))throw new IllegalArgumentException("A suíte não pertence a este plano.");}
    public TestCaseDTO create(TestCaseDTO dto) {
        TestSuite testSuite = testSuiteRepository.findById(dto.getTestSuiteId())
                .orElseThrow(() -> new RuntimeException("TestSuite não encontrado com ID: " + dto.getTestSuiteId()));

        TestCaseEntity testCase = new TestCaseEntity();
        testCase.setCodeCase(dto.getCodeCase());
        if(dto.getScenario()==null || dto.getScenario().isBlank() || dto.getScenario().length()>255)throw new IllegalArgumentException("Cenário deve conter de 1 a 255 caracteres.");
        testCase.setScenario(dto.getScenario());
        testCase.setExpectedResult(dto.getExpectedResult());
        testCase.setObtainedResult(dto.getObtainedResult());
        validateEvidence(dto.getVideoEvidence());
        testCase.setVideoEvidence(dto.getVideoEvidence());
        testCase.setStatus(dto.getStatus());
        testCase.setData(dto.getData());
        testCase.setTestSuite(testSuite);

        testCase = testCaseRepository.save(testCase);
        return new TestCaseDTO(testCase);
    }

    // Buscar todos os TestCases
    public List<TestCaseDTO> findAll() {
        return testCaseRepository.findAll().stream()
                .map(TestCaseDTO::new)
                .collect(Collectors.toList());
    }

    // Buscar um TestCase por ID
    public TestCaseDTO findById(Long id) {
        TestCaseEntity testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TestCase não encontrado com ID: " + id));
        return new TestCaseDTO(testCase);
    }

    // Atualizar um TestCase
    public TestCaseDTO update(Long id, TestCaseDTO dto) {
        TestCaseEntity testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TestCase não encontrado com ID: " + id));

        TestSuite testSuite = testSuiteRepository.findById(dto.getTestSuiteId())
                .orElseThrow(() -> new RuntimeException("TestSuite não encontrado com ID: " + dto.getTestSuiteId()));
        
        testCase.setScenario(dto.getScenario());
        testCase.setExpectedResult(dto.getExpectedResult());
        testCase.setObtainedResult(dto.getObtainedResult());
        validateEvidence(dto.getVideoEvidence());
        testCase.setVideoEvidence(dto.getVideoEvidence());
        testCase.setStatus(dto.getStatus());
        testCase.setData(dto.getData());
        testCase.setTestSuite(testSuite);

        testCase = testCaseRepository.save(testCase);
        return new TestCaseDTO(testCase);
    }

    // Excluir um TestCase
    public void delete(Long id) {
        if (!testCaseRepository.existsById(id)) {
            throw new RuntimeException("TestCase não encontrado com ID: " + id);
        }
        testCaseRepository.deleteById(id);
    }
    
// listar todos os cases vinculados
    public List<TestCaseDTO> findTestCasesByTestPlanAndTestSuite(Long testPlanId, Long testSuiteId) {
        return testCaseRepository.findTestCasesByTestPlanAndTestSuite(testPlanId, testSuiteId);
    }
    
    
    
    
   // listar o ultimo codecase
    public Optional<Long> findLastCodeCase(Long testPlanId, Long testSuiteId) {
        return testCaseRepository.findLastCodeCaseByTestPlanIdAndTestSuiteId(testPlanId, testSuiteId);
    }

    public TestCaseDTO updateEvidence(Long id, String url) {
        VideoEvidenceUrl.validate(url);
        var entity=testCaseRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Cenário não encontrado."));
        entity.setVideoEvidence(url);
        return new TestCaseDTO(testCaseRepository.save(entity));
    }
    private void validateEvidence(String value) { VideoEvidenceUrl.validate(value); }
}
