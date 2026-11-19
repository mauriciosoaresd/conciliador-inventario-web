package com.conciliador.conciliador.controller;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ConciliadorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveCarregarPaginaInicialComSucesso() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void deveInformarFormatoNaoSuportadoComoErroDeRequisicao() throws Exception {
        MockMultipartFile inventario = new MockMultipartFile(
                "inventario",
                "inventario.csv",
                "text/csv",
                "Codigo,Descricao,Quantidade\nSKU-1,Cadeira,1\n".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile documento = new MockMultipartFile(
                "documentos",
                "nota.txt",
                "text/plain",
                "documento".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/conciliar").file(inventario).file(documento))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error").value("Formato de documento não suportado. Envie XML, PDF, PNG, JPG ou JPEG."));
    }

    @Test
    void devePermitirRevisarSugestaoEGerarExcelComConciliacaoManual() throws Exception {
        MockMultipartFile inventario = new MockMultipartFile(
                "inventario",
                "inventario.csv",
                "text/csv",
                ("Codigo,Descricao,Quantidade\n"
                        + "SKU-1,Cadeira executiva ergonomica com apoio lombar,3\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile nota = new MockMultipartFile(
                "documentos",
                "nota.xml",
                "application/xml",
                ("<nfe><ide><dhEmi>2024-06-15T10:30:00-03:00</dhEmi></ide>"
                        + "<det><prod><cProd>NF-1</cProd><xProd>Cadeira</xProd><qCom>3</qCom>"
                        + "</prod></det></nfe>")
                        .getBytes(StandardCharsets.UTF_8)
        );

        MvcResult revisao = mockMvc.perform(multipart("/conciliar/revisao")
                        .file(inventario)
                        .file(nota))
                .andExpect(status().isOk())
                .andExpect(view().name("revisao"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cadeira")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Não associar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("15/06/2024")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) revisao.getRequest().getSession(false);
        assertTrue(session != null);

        byte[] relatorio = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/conciliar/gerar")
                        .session(session)
                        .param("associacoes", "0:0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(relatorio))) {
            assertEquals(
                    "CONCILIADO MANUALMENTE",
                    workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue()
            );
        }
    }

    @Test
    void devePermitirAssociarDuasLinhasFiscaisAoMesmoGrupoQuandoHaSaldo() throws Exception {
        MockMultipartFile inventario = new MockMultipartFile(
                "inventario",
                "inventario.csv",
                "text/csv",
                ("Codigo,Descricao,Quantidade\n"
                        + "TEL-1,Telefone IP generico,4\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile nota = new MockMultipartFile(
                "documentos",
                "nota.xml",
                "application/xml",
                ("<nfe><det><prod><cProd>NF-1</cProd><xProd>Telefone IP Grandstream GXP1625</xProd><qCom>2</qCom>"
                        + "</prod></det><det><prod><cProd>NF-2</cProd><xProd>Telefone IP Grandstream GXP1610</xProd><qCom>2</qCom>"
                        + "</prod></det></nfe>")
                        .getBytes(StandardCharsets.UTF_8)
        );

        MvcResult revisao = mockMvc.perform(multipart("/conciliar/revisao")
                        .file(inventario)
                        .file(nota))
                .andExpect(status().isOk())
                .andExpect(view().name("revisao"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GXP1625")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GXP1610")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) revisao.getRequest().getSession(false);
        assertTrue(session != null);

        byte[] relatorio = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/conciliar/gerar")
                        .session(session)
                        .param("associacoes", "0:0", "0:1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(relatorio))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(2, sheet.getPhysicalNumberOfRows() - 1);
            assertEquals("CONCILIADO MANUALMENTE", sheet.getRow(1).getCell(9).getStringCellValue());
            assertEquals("CONCILIADO MANUALMENTE", sheet.getRow(2).getCell(9).getStringCellValue());
            assertEquals(2.0, sheet.getRow(1).getCell(4).getNumericCellValue());
            assertEquals(2.0, sheet.getRow(2).getCell(4).getNumericCellValue());
        }
    }

    @Test
    void deveLerQuantidadeNasColunasDeNotasEscaneadas() throws Exception {
        MockMultipartFile inventario = new MockMultipartFile(
                "inventario",
                "inventario.csv",
                "text/csv",
                ("Codigo,Descricao,Marca,Modelo,Estado,Imagem,Quantidade\n"
                        + "RCK-00009,Rack de Piso 42U 570mm Cinza,,,,,6\n"
                        + "OUT-00009,Webcam Full HD Logitech C397,,,,,5\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile rack = imagem("ocr/nf_imagem_ruidosa_inclinada.png");
        MockMultipartFile webcam = imagem("ocr/nf_imagem_baixa_resolucao.jpg");

        byte[] relatorio = mockMvc.perform(multipart("/conciliar")
                        .file(inventario)
                        .file(rack)
                        .file(webcam))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(relatorio))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(4.0, quantidadeNotaPara(sheet, "RCK-00009"));
            assertEquals(5.0, quantidadeNotaDoArquivo(sheet, "nf_imagem_baixa_resolucao.jpg"));
        }
    }

    private MockMultipartFile imagem(String caminhoRecurso) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(caminhoRecurso)) {
            if (input == null) {
                throw new IllegalStateException("Imagem de teste não encontrada: " + caminhoRecurso);
            }
            String nomeArquivo = caminhoRecurso.substring(caminhoRecurso.lastIndexOf('/') + 1);
            String tipoConteudo = nomeArquivo.endsWith(".jpg") ? "image/jpeg" : "image/png";
            return new MockMultipartFile("documentos", nomeArquivo, tipoConteudo, input);
        }
    }

    private double quantidadeNotaPara(org.apache.poi.ss.usermodel.Sheet sheet, String descricao) {
        return java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
                .filter(row -> row.getRowNum() > 0)
                .filter(row -> row.getCell(2).getStringCellValue().contains(descricao))
                .mapToDouble(row -> row.getCell(5).getNumericCellValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item não encontrado no relatório: " + descricao));
    }

    private double quantidadeNotaDoArquivo(org.apache.poi.ss.usermodel.Sheet sheet, String nomeArquivo) {
        return java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
                .filter(row -> row.getRowNum() > 0)
                .filter(row -> row.getCell(8).getStringCellValue().contains(nomeArquivo))
                .mapToDouble(row -> row.getCell(5).getNumericCellValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Documento não encontrado no relatório: " + nomeArquivo));
    }
}