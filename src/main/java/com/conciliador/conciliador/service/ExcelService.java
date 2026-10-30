package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ResultadoConciliacao;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelService {

    public ByteArrayInputStream gerarRelatorioExcel(List<ResultadoConciliacao> resultados) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Conciliação de Inventário");

            // Estilo do Cabeçalho
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Linha de Cabeçalho
            String[] colunas = {"Código", "Descrição", "Estado", "Qtd Inventário", "Qtd Nota Fiscal", "Status"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < colunas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(colunas[i]);
                cell.setCellStyle(headerStyle);
            }

            // Preenchendo os dados
            int rowIdx = 1;
            for (ResultadoConciliacao res : resultados) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(res.getCodigo());
                row.createCell(1).setCellValue(res.getDescricao());
                row.createCell(2).setCellValue(res.getEstado());
                row.createCell(3).setCellValue(res.getQuantidadeInventario());
                row.createCell(4).setCellValue(res.getQuantidadeNotaFiscal());
                row.createCell(5).setCellValue(res.getStatus());
            }

            // Auto-ajustar largura das colunas
            for (int i = 0; i < colunas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar o relatório Excel: " + e.getMessage(), e);
        }
    }
}