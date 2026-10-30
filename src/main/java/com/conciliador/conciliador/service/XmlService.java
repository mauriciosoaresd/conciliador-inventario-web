package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class XmlService {

    public List<ItemNotaFiscal> lerXml(MultipartFile arquivo) {
        List<ItemNotaFiscal> itens = new ArrayList<>();
        try (InputStream inputStream = arquivo.getInputStream()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();

            // Extrair CNPJ do Emitente
            String cnpjEmitente = "";
            NodeList emitList = doc.getElementsByTagName("emit");
            if (emitList.getLength() > 0) {
                Element emitElement = (Element) emitList.item(0);
                cnpjEmitente = obterValorTag(emitElement, "CNPJ");
            }

            // Extrair Número da Nota Fiscal
            String numeroNota = "";
            NodeList ideList = doc.getElementsByTagName("ide");
            if (ideList.getLength() > 0) {
                Element ideElement = (Element) ideList.item(0);
                numeroNota = obterValorTag(ideElement, "nNF");
            }

            // Varrer os produtos da nota (<det>)
            NodeList detList = doc.getElementsByTagName("det");
            for (int i = 0; i < detList.getLength(); i++) {
                Element detElement = (Element) detList.item(i);
                Element prodElement = (Element) detElement.getElementsByTagName("prod").item(0);

                ItemNotaFiscal item = new ItemNotaFiscal();
                item.setCnpjEmitente(cnpjEmitente);
                item.setNumeroNota(numeroNota);
                item.setCodigoProdutoFornecedor(obterValorTag(prodElement, "cProd"));
                item.setDescricaoProduto(obterValorTag(prodElement, "xProd"));
                item.setQuantidade(Double.parseDouble(obterValorTag(prodElement, "qCom")));
                item.setUnidadeMedida(obterValorTag(prodElement, "uCom"));
                item.setValorUnitario(Double.parseDouble(obterValorTag(prodElement, "vUnCom")));

                itens.add(item);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar o XML da NF-e: " + e.getMessage(), e);
        }
        return itens;
    }

    private String obterValorTag(Element elemento, String nomeTag) {
        NodeList nodeList = elemento.getElementsByTagName(nomeTag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }
}