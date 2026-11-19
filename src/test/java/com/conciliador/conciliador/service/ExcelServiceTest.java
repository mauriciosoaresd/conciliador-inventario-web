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
    void deveExibirItemFiscalComQuantidadeNaoIdentificada() throws Exception {
        ResultadoConciliacao resultado = new ResultadoConciliacao(
                "-",
                "Cadeira executiva",
                "Cadeira executiva",
                "N/A",
                null,
                null,
                "123",
                "nota.pdf",
                "ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)"
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excelService.gerarRelatorioExcel(List.of(resultado)).readAllBytes())
        )) {
            var row = workbook.getSheetAt(0).getRow(1);
            assertEquals("-", row.getCell(4).getStringCellValue());
            assertEquals("NÃO IDENTIFICADA", row.getCell(5).getStringCellValue());
            assertEquals("-", row.getCell(6).getStringCellValue());
            assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", row.getCell(9).getStringCellValue());
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
            assertEquals("-", row.getCell(6).getStringCellValue());
        }
    }

    @Test
    void deveExibirDiferencaEntreQuantidadeFisicaEFiscal() throws Exception {
        ResultadoConciliacao faltante = new ResultadoConciliacao(
                "SKU-1",
                "Cadeira executiva",
                "Cadeira executiva",
                "Bom",
                3.0,
                5.0,
                "123",
                "nota.xml",
                "FALTANDO NO FÍSICO"
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(excelService.gerarRelatorioExcel(List.of(faltante)).readAllBytes())
        )) {
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            var row = sheet.getRow(1);

            assertEquals("Diferença (Físico - NF)", header.getCell(6).getStringCellValue());
            assertEquals(3.0, row.getCell(4).getNumericCellValue());
            assertEquals(5.0, row.getCell(5).getNumericCellValue());
            assertEquals(-2.0, row.getCell(6).getNumericCellValue());
            assertEquals("123", row.getCell(7).getStringCellValue());
            assertEquals("nota.xml", row.getCell(8).getStringCellValue());
            assertEquals("FALTANDO NO FÍSICO", row.getCell(9).getStringCellValue());
        }
    }
}
