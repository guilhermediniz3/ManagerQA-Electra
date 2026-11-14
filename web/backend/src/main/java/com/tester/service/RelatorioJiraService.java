package com.tester.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tester.entity.TestCaseEntity;
import com.tester.entity.TestSuite;
import com.tester.repository.TestSuiteRepository;

@Service
public class RelatorioJiraService {

    private final TestSuiteRepository testSuiteRepository;

    @Autowired
    public RelatorioJiraService(TestSuiteRepository testSuiteRepository) {
        this.testSuiteRepository = testSuiteRepository;
    }

    /** Relatório completo da suíte, com todos os casos. */
    public byte[] gerarRelatorioJiraExcel(Long testSuiteId) throws IOException {
        return gerarRelatorioJiraExcel(testSuiteId, java.util.Set.of());
    }

    /**
     * Mesmo relatório, restrito aos casos cujo status esteja em {@code statusFiltro}.
     * Conjunto vazio significa todos — é o comportamento de sempre. Serve para
     * enviar ao Jira só o que interessa à transição: ao devolver a demanda em
     * retorno, só os casos em Retorno; ao concluir, só os concluídos.
     */
    public byte[] gerarRelatorioJiraExcel(Long testSuiteId, java.util.Set<com.tester.enuns.Status> statusFiltro) throws IOException {
        TestSuite testSuite = testSuiteRepository.findById(testSuiteId)
                .orElseThrow(() -> new IOException("Test Suite não encontrada com o ID: " + testSuiteId));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Relatório Jira");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            int currentRow = 0;

            // Linha da Data
            Row dataRow = sheet.createRow(currentRow++);
            String dataFormatada = (testSuite.getData() == null ? "" : testSuite.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))); // Formata a data
            createHeaderWithData(dataRow, "Data", dataFormatada, headerStyle, dateStyle);

            // Linha do Responsável (nome do tester)
            Row responsibleRow = sheet.createRow(currentRow++);
            String responsavel = (testSuite.getTestPlan().getTester() == null ? "N/A" : testSuite.getTestPlan().getTester().getName());
            if (responsavel == null || responsavel.isEmpty()) {
                responsavel = "N/A"; // Valor padrão caso o nome do tester seja nulo ou vazio
            }
            createHeaderWithData(responsibleRow, "Responsável", responsavel, headerStyle, dataStyle);

            // Resumo da suíte inteira, mesmo quando os detalhes são filtrados por status.
            var counts = new java.util.EnumMap<com.tester.enuns.Status, Long>(com.tester.enuns.Status.class);
            for (var status : com.tester.enuns.Status.values()) counts.put(status, 0L);
            for (var testCase : testSuite.getCases()) {
                var status = testCase.getStatus() == null ? com.tester.enuns.Status.EM_PROGRESSO : testCase.getStatus();
                counts.put(status, counts.get(status) + 1);
            }
            long totalCases = testSuite.getCases().size();
            summaryCell(dataRow, 2, "Falhou", counts.get(com.tester.enuns.Status.RETORNO), totalCases, dataStyle);
            summaryCell(dataRow, 3, "Impedimento", counts.get(com.tester.enuns.Status.IMPEDIMENTO), totalCases, dataStyle);
            summaryCell(dataRow, 4, "Em progresso", counts.get(com.tester.enuns.Status.EM_PROGRESSO), totalCases, dataStyle);
            summaryCell(responsibleRow, 2, "Aprovado", counts.get(com.tester.enuns.Status.CONCLUIDA), totalCases, dataStyle);
            responsibleRow.createCell(3).setCellValue("Total da suíte: " + totalCases + (totalCases == 1 ? " CT" : " CTs"));
            responsibleRow.getCell(3).setCellStyle(dataStyle);

            // Linha de Projeto, Chamado, Matriz, Usuário, Senha
            Row metadataHeaderRow = sheet.createRow(currentRow++);
            createHeader(metadataHeaderRow, new String[]{"Projeto", "Chamado", "Matriz", "Usuário", "Senha"}, headerStyle);
            Row metadataRow = sheet.createRow(currentRow++);
            metadataRow.createCell(0).setCellValue(testSuite.getTestPlan().getJira()); // Projeto
            metadataRow.createCell(1).setCellValue(testSuite.getTestPlan().getCallNumber()); // Chamado
            metadataRow.createCell(2).setCellValue(testSuite.getTestPlan().getMatriz()); // Matriz
            metadataRow.createCell(3).setCellValue(testSuite.getTestPlan().getUserName()); // Usuário
            metadataRow.createCell(4).setCellValue(testSuite.getTestPlan().getPassword()); // Senha
            applyDataStyle(metadataRow, dataStyle);

            // Linha da Descrição (mesclada)
            Row descriptionRow = sheet.createRow(currentRow++);
            createMergedHeaderWithData(sheet, descriptionRow, "Descrição", testSuite.getTestPlan().getName(), headerStyle, dataStyle, 1, 4);

            // Cabeçalho dos Casos de Teste
            Row headerRow = sheet.createRow(currentRow++);
            String[] headers = {"Cenário", "Resultado Esperado", "Resultado Obtido", "Evidência", "Resultado"};
            createHeader(headerRow, headers, headerStyle);

            // Dados dos Casos de Teste
            Set<TestCaseEntity> testCaseSet = testSuite.getCases();
            List<TestCaseEntity> testCases = new ArrayList<>(testCaseSet);
            testCases.sort(java.util.Comparator.comparing(TestCaseEntity::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
            if (statusFiltro != null && !statusFiltro.isEmpty()) {
                testCases.removeIf(testCase -> !statusFiltro.contains(testCase.getStatus()));
            }
            responsibleRow.createCell(4).setCellValue("Detalhes enviados: " + testCases.size() + (testCases.size() == 1 ? " CT" : " CTs"));
            responsibleRow.getCell(4).setCellStyle(dataStyle);
            for (TestCaseEntity testCase : testCases) {
                Row dataRowCase = sheet.createRow(currentRow++);
                dataRowCase.createCell(0).setCellValue(testCase.getScenario());
                dataRowCase.createCell(1).setCellValue(testCase.getExpectedResult());
                dataRowCase.createCell(2).setCellValue(testCase.getObtainedResult());
                var evidenceCell=dataRowCase.createCell(3);
                evidenceCell.setCellValue(testCase.getVideoEvidence());
                if(VideoEvidenceUrl.valid(testCase.getVideoEvidence())) {
                    var link=workbook.getCreationHelper().createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.URL);
                    link.setAddress(testCase.getVideoEvidence());evidenceCell.setHyperlink(link);
                }
                dataRowCase.createCell(4).setCellValue((testCase.getStatus() == null ? "EM_PROGRESSO" : testCase.getStatus().getDescricao()));
                applyDataStyle(dataRowCase, dataStyle);
            }

            // Ajustar tamanho das colunas
            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }

            // Escrever o arquivo Excel
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IOException("Erro ao gerar o arquivo Excel: " + e.getMessage(), e);
        }
    }

    private void createHeader(Row row, String[] titles, CellStyle headerStyle) {
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(headerStyle);
        }
    }
    private void summaryCell(Row row, int column, String label, long count, long total, CellStyle style) {
        long percentage = total == 0 ? 0 : Math.round(100.0 * count / total);
        var cell = row.createCell(column);
        cell.setCellValue(label + ": " + percentage + "% (" + count + ")");
        cell.setCellStyle(style);
    }

    private void createHeaderWithData(Row row, String headerText, String data, CellStyle headerStyle, CellStyle dataStyle) {
        Cell headerCell = row.createCell(0);
        headerCell.setCellValue(headerText);
        headerCell.setCellStyle(headerStyle);

        Cell dataCell = row.createCell(1);
        dataCell.setCellValue(data);
        dataCell.setCellStyle(dataStyle);
    }

    private void createMergedHeaderWithData(Sheet sheet, Row row, String headerText, String data, CellStyle headerStyle, CellStyle dataStyle, int startCol, int endCol) {
        Cell headerCell = row.createCell(0);
        headerCell.setCellValue(headerText);
        headerCell.setCellStyle(headerStyle);

        Cell dataCell = row.createCell(1);
        dataCell.setCellValue(data);
        dataCell.setCellStyle(dataStyle);

        // Mesclar células da coluna B até E
        sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), startCol, endCol));
    }

    private void applyDataStyle(Row row, CellStyle dataStyle) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                cell.setCellStyle(dataStyle);
            }
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        return headerStyle;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        return dataStyle;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle dateStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd/MM/yyyy")); // Formato DD/MM/YYYY
        dateStyle.setBorderTop(BorderStyle.THIN);
        dateStyle.setBorderBottom(BorderStyle.THIN);
        dateStyle.setBorderLeft(BorderStyle.THIN);
        dateStyle.setBorderRight(BorderStyle.THIN);
        return dateStyle;
    }
}
