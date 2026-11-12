package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentoFiscalServiceTest {

    @Mock
    private DocumentoParserService documentoParserService;

    @Mock
    private XmlService xmlService;

    @InjectMocks
    private DocumentoFiscalService documentoFiscalService;

    @Test
    void deveExtrairItensOcrComQuantidadeEIdentificacaoDaNota() {
        MockMultipartFile documento = documento("nota.png");
        when(documentoParserService.extrairTexto(documento))
                .thenReturn("Nota Fiscal 12345\nCadeira executiva 2 100,00");
        when(documentoParserService.extrairQuantidadesColuna(documento)).thenReturn(List.of());
        when(documentoParserService.contemValorMonetario("Nota Fiscal 12345")).thenReturn(false);
        when(documentoParserService.contemValorMonetario("Cadeira executiva 2 100,00")).thenReturn(true);
        when(documentoParserService.extrairQuantidadeDaLinha("Cadeira executiva 2 100,00"))
                .thenReturn(OptionalDouble.of(2.0));

        List<ItemNotaFiscal> itens = documentoFiscalService.extrairItens(List.of(documento));

        assertEquals(2, itens.size());
        assertEquals("12345", itens.get(0).getNumeroNota());
        assertEquals(2.0, itens.get(1).getQuantidade());
        assertEquals("nota.png", itens.get(1).getNomeArquivoOrigem());
    }

    private MockMultipartFile documento(String nome) {
        return new MockMultipartFile(
                "documentos",
                nome,
                "image/png",
                new byte[]{1}
        );
    }
}
