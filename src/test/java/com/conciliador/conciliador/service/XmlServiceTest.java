package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.exception.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XmlServiceTest {

    private final XmlService xmlService = new XmlService();

    @Test
    void deveManterQuantidadeFiscalNulaQuandoAusenteNoXml() {
        ItemNotaFiscal item = lerItem(
                "<nota><item><codigo>SKU-1</codigo><descricao>Cadeira executiva</descricao></item></nota>"
        );

        assertNull(item.getQuantidade());
    }

    @Test
    void deveManterQuantidadeFiscalNulaQuandoInvalidaNoXml() {
        ItemNotaFiscal item = lerItem(
                "<nfe><det><prod><cProd>SKU-1</cProd><xProd>Cadeira executiva</xProd><qCom>inválida</qCom></prod></det></nfe>"
        );

        assertNull(item.getQuantidade());
    }

    @Test
    void deveLerQuantidadeFiscalValidaNoXml() {
        ItemNotaFiscal item = lerItem(
                "<nfe><det><prod><cProd>SKU-1</cProd><xProd>Cadeira executiva</xProd><qCom>2.5</qCom></prod></det></nfe>"
        );

        assertEquals(2.5, item.getQuantidade());
    }

    @Test
    void deveLerXmlComNamespaces() {
        ItemNotaFiscal item = lerItem(
                "<n:nfe xmlns:n=\"urn:exemplo:nfe\"><n:det><n:prod>"
                        + "<n:cProd>SKU-1</n:cProd><n:xProd>Cadeira executiva</n:xProd><n:qCom>2</n:qCom>"
                        + "</n:prod></n:det></n:nfe>"
        );

        assertEquals("SKU-1", item.getCodigoProdutoFornecedor());
        assertEquals(2.0, item.getQuantidade());
    }

    @Test
    void deveAtribuirMesmoIdentificadorAItensDoDocumentoEUmNovoEntreArquivos() {
        List<ItemNotaFiscal> itensPrimeiroArquivo = xmlService.lerXml(arquivoXml(
                "<nota><item><descricao>Cadeira A</descricao></item>"
                        + "<item><descricao>Cadeira B</descricao></item></nota>",
                "nota.xml"
        ));
        List<ItemNotaFiscal> itensSegundoArquivo = xmlService.lerXml(arquivoXml(
                "<nota><item><descricao>Cadeira A</descricao></item></nota>",
                "nota.xml"
        ));

        assertEquals(2, itensPrimeiroArquivo.size());
        assertEquals(
                itensPrimeiroArquivo.get(0).getIdentificadorDocumentoOrigem(),
                itensPrimeiroArquivo.get(1).getIdentificadorDocumentoOrigem()
        );
        org.junit.jupiter.api.Assertions.assertNotEquals(
                itensPrimeiroArquivo.get(0).getIdentificadorDocumentoOrigem(),
                itensSegundoArquivo.get(0).getIdentificadorDocumentoOrigem()
        );
    }

    @Test
    void deveRejeitarXmlComDeclaracaoDoctype() {
        MockMultipartFile arquivo = new MockMultipartFile(
                "documentos",
                "nota.xml",
                "application/xml",
                ("<!DOCTYPE nfe [<!ENTITY texto \"conteudo\">]>"
                        + "<nfe><item><descricao>&texto;</descricao></item></nfe>")
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(RequisicaoInvalidaException.class, () -> xmlService.lerXml(arquivo));
    }

    private ItemNotaFiscal lerItem(String xml) {
        List<ItemNotaFiscal> itens = xmlService.lerXml(arquivoXml(xml, "nota.xml"));
        assertEquals(1, itens.size());
        return itens.get(0);
    }

    private MockMultipartFile arquivoXml(String xml, String nome) {
        return new MockMultipartFile(
                "documentos",
                nome,
                "application/xml",
                xml.getBytes(StandardCharsets.UTF_8)
        );
    }
}
