package com.tester.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tester.dto.TestSuiteDTO;
import com.tester.entity.TestCaseEntity;
import com.tester.entity.TestPlan;
import com.tester.entity.TestSuite;
import com.tester.enuns.Status;
import com.tester.repository.TestCaseRepository;
import com.tester.repository.TestPlanRepository;
import com.tester.repository.TestSuiteRepository;

@Service
@org.springframework.transaction.annotation.Transactional
public class TestSuiteService {

	@Autowired
	private TestSuiteRepository testSuiteRepository;

	@Autowired
	private TestPlanRepository testPlanRepository;

	@Autowired
	private TestCaseRepository testCaseRepository;

	// Criar um novo TestSuite
	public TestSuiteDTO create(TestSuiteDTO dto) {
		if(dto.getTestCaseId()!=null && !dto.getTestCaseId().isEmpty()) throw new IllegalArgumentException("Crie a suíte e adicione seus casos dentro dela.");
        TestSuite testSuite = new TestSuite();
		testSuite.setStatus(dto.getStatus());
		testSuite.setData(dto.getData());
		testSuite.setCodeSuite(dto.getCodeSuite());

		// Buscar e setar o TestPlan
		Optional<TestPlan> testPlanOpt = testPlanRepository.findById(dto.getTestPlanId());
		if (!testPlanOpt.isPresent()) {
			throw new RuntimeException("TestPlan não encontrado com ID: " + dto.getTestPlanId());
		}
		testSuite.setTestPlan(testPlanOpt.get());

        testSuite.setCases(new HashSet<>());

		// Salvar no repositório
		testSuite = testSuiteRepository.save(testSuite);
		return new TestSuiteDTO(testSuite);
	}

	// Buscar todos os TestSuites
	public List<TestSuiteDTO> findAll() {
		List<TestSuite> testSuites = testSuiteRepository.findAll();
		return testSuites.stream().map(testSuite -> new TestSuiteDTO(testSuite)).collect(Collectors.toList());
	}

	// Buscar um TestSuite por ID
	public TestSuiteDTO findById(Long id) {
		TestSuite testSuite = testSuiteRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("TestSuite não encontrado com ID: " + id));
		return new TestSuiteDTO(testSuite);
	}

	// Atualizar um TestSuite
	public TestSuiteDTO update(Long id, TestSuiteDTO dto) {
		TestSuite testSuite = testSuiteRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("TestSuite não encontrado com ID: " + id));

		testSuite.setStatus(dto.getStatus());
		testSuite.setData(dto.getData());
		testSuite.setCodeSuite(dto.getCodeSuite());

		// Atualizar TestPlan
		Optional<TestPlan> testPlanOpt = testPlanRepository.findById(dto.getTestPlanId());
		if (!testPlanOpt.isPresent()) {
			throw new RuntimeException("TestPlan não encontrado com ID: " + dto.getTestPlanId());
		}
		if(!java.util.Objects.equals(testSuite.getTestPlan().getId(),dto.getTestPlanId())) throw new IllegalArgumentException("A suíte não pode ser movida por esta operação.");

        // Case membership is managed by the case endpoints. Metadata edits preserve cases.
        if(dto.getTestCaseId()!=null && !dto.getTestCaseId().isEmpty() && !testSuite.getCases().stream().map(TestCaseEntity::getId).collect(Collectors.toSet()).equals(dto.getTestCaseId())) throw new IllegalArgumentException("Altere os casos pelos endpoints de casos de teste.");

		// Salvar mudanças
		testSuite = testSuiteRepository.save(testSuite);
		return new TestSuiteDTO(testSuite);
	}

	// Excluir um TestSuite
	public void delete(Long id) {
		if (!testSuiteRepository.existsById(id)) {
			throw new RuntimeException("TestSuite não encontrado com ID: " + id);
		}
		testSuiteRepository.deleteById(id);
	}
	public TestSuiteDTO clone(Long id, TestSuiteDTO dto) {
	    // Buscar o TestSuite original
	    TestSuite originalTestSuite = testSuiteRepository.findById(id)
	            .orElseThrow(() -> new RuntimeException("TestSuite não encontrado com ID: " + id));

	    // Criar um novo TestSuite
	    TestSuite clonedTestSuite = new TestSuite();
	    clonedTestSuite.setStatus(dto.getStatus()); // Usa o status diretamente do DTO
	    clonedTestSuite.setData(dto.getData()); // Usa a data diretamente do DTO
	    clonedTestSuite.setCodeSuite(dto.getCodeSuite()); // Usa o codeSuite diretamente do DTO
	    clonedTestSuite.setTestPlan(originalTestSuite.getTestPlan()); // Mantém o mesmo testPlan

	    // Clonar os TestCases associados ao TestSuite original
	    Set<TestCaseEntity> clonedTestCases = new HashSet<>();
	    for (TestCaseEntity testCase : originalTestSuite.getCases()) {
	        // Criar um novo TestCase
	        TestCaseEntity clonedTestCase = new TestCaseEntity();
	        clonedTestCase.setCodeCase(testCase.getCodeCase());
            clonedTestCase.setScenario(testCase.getScenario());
	        clonedTestCase.setExpectedResult(testCase.getExpectedResult());
	        clonedTestCase.setObtainedResult(testCase.getObtainedResult());
	       // clonedTestCase.setVideoEvidence(testCase.getVideoEvidence());
	        clonedTestCase.setStatus(Status.EM_PROGRESSO);
	        clonedTestCase.setData(testCase.getData());

	        // Adicionar o TestCase à coleção de TestCases clonados
	        clonedTestCases.add(clonedTestCase);
	    }

	    // Salvar o novo TestSuite
	    TestSuite finalClonedTestSuite = testSuiteRepository.save(clonedTestSuite);

	    // Associar o TestSuite recém-criado aos TestCases clonados
	    clonedTestCases.forEach(testCase -> testCase.setTestSuite(finalClonedTestSuite));

	    // Salvar todos os TestCases clonados
	    testCaseRepository.saveAll(clonedTestCases);
        finalClonedTestSuite.setCases(clonedTestCases);

	    // Retornar o DTO do TestSuite clonado
	    return new TestSuiteDTO(finalClonedTestSuite);
	}

	
	  public List<TestSuiteDTO> getTestSuitesByTestPlanId(Long testPlanId) {
	        return testSuiteRepository.findAllTestSuitesByTestPlanId(testPlanId);
		}

}
