package com.conciliador.conciliador.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void devePermitirReutilizarUploadAposExtrairTexto() throws Exception {
        byte[] conteudo;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("ocr/nf_imagem_baixa_resolucao.jpg")) {
            assertNotNull(input);
            conteudo = input.readAllBytes();
        }

        MultipartFile arquivo = mock(MultipartFile.class);
        AtomicBoolean arquivoMovido = new AtomicBoolean();
        when(arquivo.getOriginalFilename()).thenReturn("nf_imagem_baixa_resolucao.jpg");
        when(arquivo.getInputStream()).thenAnswer(invocation -> {
            if (arquivoMovido.get()) {
                throw new NoSuchFileException("upload temporário já movido");
            }
            return new ByteArrayInputStream(conteudo);
        });
        doAnswer(invocation -> {
            File destino = invocation.getArgument(0);
            Files.copy(new ByteArrayInputStream(conteudo), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            arquivoMovido.set(true);
            return null;
        }).when(arquivo).transferTo(any(File.class));

        assertFalse(documentoParserService.extrairTexto(arquivo).isBlank());
        assertEquals(List.of(5.0, 5.0, 2.0), documentoParserService.extrairQuantidadesColuna(arquivo));
    }

    private List<Double> extrairQuantidades(String nomeRecurso) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(nomeRecurso)) {
            assertNotNull(input);
            MockMultipartFile arquivo = new MockMultipartFile("documentos", nomeRecurso, "image/png", input);
            return documentoParserService.extrairQuantidadesColuna(arquivo);
        }
    }
}