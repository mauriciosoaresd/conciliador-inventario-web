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
            assertEquals("NÃO IDENTIFICADA", row.getCell(5).getStringCellValue());
            assertEquals("REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA", row.getCell(8).getStringCellValue());
        }
    }
}
