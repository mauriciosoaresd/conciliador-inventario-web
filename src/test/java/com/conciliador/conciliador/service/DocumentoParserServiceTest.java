package com.conciliador.conciliador.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentoParserServiceTest {

    private DocumentoParserService documentoParserService;

    @BeforeEach
    void setUp() {
        documentoParserService = new DocumentoParserService();
    }

    @Test
    void deveRetornarVazioParaArquivoNuloOuInvalido() {
        MockMultipartFile arquivoVazio = new MockMultipartFile(
                "documentos",
                "vazio.txt",
                "text/plain",
                new byte[0]
        );

        String texto = documentoParserService.extrairTexto(arquivoVazio);
        assertTrue(texto.isEmpty(), "O texto extraído de arquivos não suportados/vazios deve ser vazio.");
    }

    @Test
    void deveLocarExcecaoOuTratarArquivoPdfInvalidoComSeguranca() {
        // Arquivo simulado com extensão PDF mas conteúdo inválido
        MockMultipartFile arquivoInvalido = new MockMultipartFile(
                "documentos",
                "nota-falsa.pdf",
                "application/pdf",
                "conteudo invalido".getBytes()
        );

        // Como o PDFBox nao consegue carregar um PDF invalido, esperamos que lance a RuntimeException tratada
        assertThrows(RuntimeException.class, () -> {
            documentoParserService.extrairTexto(arquivoInvalido);
        });
    }

    @Test
    void deveExtrairQuantidadesDeColunaEmImagensDeNotas() throws Exception {
        assertEquals(List.of(3.0, 6.0, 4.0), extrairQuantidades("ocr/nf_imagem_ruidosa_inclinada.png"));
        assertEquals(List.of(5.0, 5.0, 2.0), extrairQuantidades("ocr/nf_imagem_baixa_resolucao.jpg"));
    }

    @Test
    void deveExtrairQuantidadeMesmoComTracoEntreQuantidadeEValor() {
        assertEquals(5.0, documentoParserService.extrairQuantidadeDaLinha(
                "OUT-00009 Webcam Full HD Logitech C397 5 — 7788,90").orElseThrow());
    }

    private List<Double> extrairQuantidades(String nomeRecurso) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(nomeRecurso)) {
            assertNotNull(input);
            MockMultipartFile arquivo = new MockMultipartFile("documentos", nomeRecurso, "image/png", input);
            return documentoParserService.extrairQuantidadesColuna(arquivo);
        }
    }
}