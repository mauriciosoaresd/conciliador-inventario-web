package com.conciliador.conciliador.service;

import com.conciliador.conciliador.exception.RequisicaoInvalidaException;
import com.conciliador.conciliador.model.ItemNotaFiscal;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class XmlService {

    public List<ItemNotaFiscal> lerXml(MultipartFile arquivo) {
        List<ItemNotaFiscal> itens = new ArrayList<>();
        String nomeArquivo = arquivo.getOriginalFilename(); // <--- Captura o nome do arquivo

        try (InputStream is = arquivo.getInputStream()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            dbFactory.setXIncludeAware(false);
            dbFactory.setExpandEntityReferences(false);
            dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            dBuilder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            // 1. Tenta extrair o Número da Nota Fiscal (nNF padrão NFe ou tags genéricas)
            String numeroNota = "";
            NodeList ideList = buscarElementos(doc.getDocumentElement(), "ide");
            if (ideList.getLength() > 0) {
                Element ideElement = (Element) ideList.item(0);
                numeroNota = pegarValorTag(ideElement, new String[]{"nNF", "numeroNota", "numero"});
            }
            if (numeroNota.isEmpty()) {
                numeroNota = pegarValorTag(doc.getDocumentElement(), new String[]{"nNF", "numeroNota", "numero"});
            }

            // 2. Busca os itens da nota
            NodeList nList = buscarElementos(doc.getDocumentElement(), "det");
            if (nList.getLength() == 0) {
                nList = buscarElementos(doc.getDocumentElement(), "item");
            }
            if (nList.getLength() == 0) {
                nList = buscarElementos(doc.getDocumentElement(), "ItemNotaFiscal");
            }

            for (int i = 0; i < nList.getLength(); i++) {
                Element elemento = (Element) nList.item(i);
                ItemNotaFiscal item = new ItemNotaFiscal();

                String codigo = pegarValorTag(elemento, new String[]{"cProd", "codigo", "codigoProdutoFornecedor"});
                String descricao = pegarValorTag(elemento, new String[]{"xProd", "descricao", "descricaoProduto"});
                String qtdStr = pegarValorTag(elemento, new String[]{"qCom", "qTrib", "quantidade", "qtd", "qtdItem", "quant", "QTD"});

                item.setQuantidade(parseQuantidade(qtdStr));

                item.setCodigoProdutoFornecedor(codigo);
                item.setDescricaoProduto(descricao);

                // Atribui corretamente os dados de rastreabilidade
                item.setNumeroNota(numeroNota);
                item.setNomeArquivoOrigem(nomeArquivo);

                if (descricao != null && !descricao.isEmpty()) {
                    itens.add(item);
                }
            }
        } catch (SAXException | IOException e) {
            throw new RequisicaoInvalidaException("O arquivo XML da nota fiscal é inválido ou está malformado.", e);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar arquivo XML da nota fiscal.", e);
        }
        return itens;
    }

    private String pegarValorTag(Element elemento, String[] possiveisTags) {
        for (String tag : possiveisTags) {
            NodeList lista = buscarElementos(elemento, tag);
            if (lista.getLength() > 0 && lista.item(0).getFirstChild() != null) {
                return lista.item(0).getFirstChild().getNodeValue().trim();
            }
        }
        return "";
    }

    private NodeList buscarElementos(Element elemento, String tag) {
        NodeList lista = elemento.getElementsByTagNameNS("*", tag);
        return lista.getLength() > 0 ? lista : elemento.getElementsByTagName(tag);
    }

    private Double parseQuantidade(String quantidade) {
        if (quantidade == null || quantidade.isBlank()) {
            return null;
        }
        try {
            double valor = Double.parseDouble(quantidade.replace(",", "."));
            return Double.isFinite(valor) ? valor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}