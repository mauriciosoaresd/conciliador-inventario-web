package com.conciliador.conciliador.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

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
}