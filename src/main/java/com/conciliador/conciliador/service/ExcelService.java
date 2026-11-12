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
            Sheet sheet = workbook.createSheet("Conciliação Inteligente");

            // 1. Estilo do Cabeçalho
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Estilos condicionais para o Status
            CellStyle styleOk = criarEstiloStatus(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle styleRevisao = criarEstiloStatus(workbook, IndexedColors.LIGHT_YELLOW);
            CellStyle styleDivergente = criarEstiloStatus(workbook, IndexedColors.ROSE);
            CellStyle styleNaoEncontrado = criarEstiloStatus(workbook, IndexedColors.GREY_40_PERCENT);

            // Linha de Cabeçalho atualizada com as 2 novas colunas de rastreabilidade
            String[] colunas = {
                    "Código",
                    "Descrição Inventário",
                    "Descrição Nota Fiscal",
                    "Estado",
                    "Qtd Inventário",
                    "Qtd Nota Fiscal",
                    "Nº da Nota Fiscal",
                    "Nome do Arquivo",
                    "Status"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < colunas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(colunas[i]);
                cell.setCellStyle(headerStyle);
            }

            // 2. Preenchendo os dados
            int rowIdx = 1;
            for (ResultadoConciliacao res : resultados) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(res.getCodigo() != null ? res.getCodigo() : "");
                row.createCell(1).setCellValue(res.getDescricaoInventario() != null ? res.getDescricaoInventario() : "");
                row.createCell(2).setCellValue(res.getDescricaoNotaFiscal() != null ? res.getDescricaoNotaFiscal() : "-");
                row.createCell(3).setCellValue(res.getEstado() != null ? res.getEstado() : "");
                Cell quantidadeInventarioCell = row.createCell(4);
                if (res.getQuantidadeInventario() == null) {
                    quantidadeInventarioCell.setCellValue("-");
                } else {
                    quantidadeInventarioCell.setCellValue(res.getQuantidadeInventario());
                }
                Cell quantidadeNotaCell = row.createCell(5);
                if (res.getQuantidadeNotaFiscal() == null) {
                    quantidadeNotaCell.setCellValue("NÃO IDENTIFICADA");
                } else {
                    quantidadeNotaCell.setCellValue(res.getQuantidadeNotaFiscal());
                }

                // Preenchendo os novos campos de rastreabilidade
                row.createCell(6).setCellValue(res.getNumeroNotaFiscal() != null ? res.getNumeroNotaFiscal() : "-");
                row.createCell(7).setCellValue(res.getNomeArquivoNota() != null ? res.getNomeArquivoNota() : "-");

                // Célula de Status com formatação condicional baseada no texto (agora na coluna index 8)
                Cell statusCell = row.createCell(8);
                String statusText = res.getStatus();
                statusCell.setCellValue(statusText);

                if (statusText.startsWith("OK")) {
                    statusCell.setCellStyle(styleOk);
                } else if (statusText.startsWith("REVISÃO")) {
                    statusCell.setCellStyle(styleRevisao);
                } else if (statusText.contains("DIVERGENTE")) {
                    statusCell.setCellStyle(styleDivergente);
                } else {
                    statusCell.setCellStyle(styleNaoEncontrado);
                }
            }

            // 3. Auto-ajustar largura das colunas para legibilidade
            for (int i = 0; i < colunas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar o relatório Excel inteligente: " + e.getMessage(), e);
        }
    }

    // Auxiliar para criar estilos de cores nas células de status
    private CellStyle criarEstiloStatus(Workbook workbook, IndexedColors corFundo) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(corFundo.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}