package com.conciliador.conciliador.controller;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:conciliador-controller-test;DB_CLOSE_DELAY=-1")
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
            assertEquals(5.0, quantidadeNotaPara(sheet, "Webcam Full HD Logitech C397"));
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
}