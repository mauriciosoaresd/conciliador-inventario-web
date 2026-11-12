package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ResultadoConciliacao;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelServiceTest {

    private final ExcelService excelService = new ExcelService();

    @Test
    void deveExibirQuantidadeNaoIdentificadaNoRelatorio() throws Exception {
        ResultadoConciliacao resultado = new ResultadoConciliacao(
                "SKU-1",
                "Cadeira executiva",
                "Cadeira executiva",
                "Bom",
                5.0,
                null,
                "123",
                "nota.pdf",
                "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA"
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excelService.gerarRelatorioExcel(List.of(resultado)).readAllBytes())
        )) {
            var row = workbook.getSheetAt(0).getRow(1);
            assertEquals(5.0, row.getCell(4).getNumericCellValue());
            assertEquals("NÃO IDENTIFICADA", row.getCell(5).getStringCellValue());
            assertEquals("REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA", row.getCell(8).getStringCellValue());
        }
    }

    @Test
    void deveExibirTracoNaQuantidadeDeInventarioDosDetalhes() throws Exception {
        ResultadoConciliacao detalhe = new ResultadoConciliacao(
                "-",
                "-",
                "Cadeira executiva",
                "N/A",
                null,
                5.0,
                "123",
                "nota.pdf",
                "DETALHE DA NF (INCLUÍDO NO RESUMO)"
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excelService.gerarRelatorioExcel(List.of(detalhe)).readAllBytes())
        )) {
            assertEquals("-", workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue());
        }
    }

    @Test
    void deveExibirTracoNaQuantidadeFiscalQuandoNaoHaNotaCorrespondente() throws Exception {
        ResultadoConciliacao saldoFisico = new ResultadoConciliacao(
                "SKU-1",
                "Cadeira executiva",
                "-",
                "Bom",
                2.0,
                null,
                null,
                null,
                "SEM NF CORRESPONDENTE"
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excelService.gerarRelatorioExcel(List.of(saldoFisico)).readAllBytes())
        )) {
            var row = workbook.getSheetAt(0).getRow(1);
            assertEquals(2.0, row.getCell(4).getNumericCellValue());
            assertEquals("-", row.getCell(5).getStringCellValue());
        }
    }
}
