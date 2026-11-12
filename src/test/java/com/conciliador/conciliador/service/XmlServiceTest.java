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
        MockMultipartFile arquivo = new MockMultipartFile(
                "documentos",
                "nota.xml",
                "application/xml",
                xml.getBytes(StandardCharsets.UTF_8)
        );
        List<ItemNotaFiscal> itens = xmlService.lerXml(arquivo);
        assertEquals(1, itens.size());
        return itens.get(0);
    }
}
