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
        String nomeArquivo = arquivo.getOriginalFilename(); // <--- Captura o nome do arquivo

        try (InputStream is = arquivo.getInputStream()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            // 1. Tenta extrair o Número da Nota Fiscal (nNF padrão NFe ou tags genéricas)
            String numeroNota = "";
            NodeList ideList = doc.getElementsByTagName("ide");
            if (ideList.getLength() > 0) {
                Element ideElement = (Element) ideList.item(0);
                numeroNota = pegarValorTag(ideElement, new String[]{"nNF", "numeroNota", "numero"});
            }
            if (numeroNota.isEmpty()) {
                numeroNota = pegarValorTag(doc.getDocumentElement(), new String[]{"nNF", "numeroNota", "numero"});
            }

            // 2. Busca os itens da nota
            NodeList nList = doc.getElementsByTagName("det");
            if (nList.getLength() == 0) {
                nList = doc.getElementsByTagName("item"); // Fallback para XML de teste simplificado
            }
            if (nList.getLength() == 0) {
                nList = doc.getElementsByTagName("ItemNotaFiscal");
            }

            for (int i = 0; i < nList.getLength(); i++) {
                Element elemento = (Element) nList.item(i);
                ItemNotaFiscal item = new ItemNotaFiscal();

                String codigo = pegarValorTag(elemento, new String[]{"cProd", "codigo", "codigoProdutoFornecedor"});
                String descricao = pegarValorTag(elemento, new String[]{"xProd", "descricao", "descricaoProduto"});
                String qtdStr = pegarValorTag(elemento, new String[]{"qCom", "qTrib", "quantidade", "qtd", "qtdItem", "quant", "QTD"});

                try {
                    item.setQuantidade(qtdStr != null && !qtdStr.isEmpty()
                            ? Double.parseDouble(qtdStr.replace(",", "."))
                            : 1.0);
                } catch (NumberFormatException e) {
                    item.setQuantidade(1.0);
                }

                item.setCodigoProdutoFornecedor(codigo);
                item.setDescricaoProduto(descricao);

                // Atribui corretamente os dados de rastreabilidade
                item.setNumeroNota(numeroNota);
                item.setNomeArquivoOrigem(nomeArquivo);

                try {
                    item.setQuantidade(qtdStr != null && !qtdStr.isEmpty() ? Double.parseDouble(qtdStr.replace(",", ".")) : 1.0);
                } catch (NumberFormatException e) {
                    item.setQuantidade(1.0);
                }

                if (descricao != null && !descricao.isEmpty()) {
                    itens.add(item);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar arquivo XML da nota fiscal: " + e.getMessage(), e);
        }
        return itens;
    }

    private String pegarValorTag(Element elemento, String[] possiveisTags) {
        for (String tag : possiveisTags) {
            NodeList lista = elemento.getElementsByTagName(tag);
            if (lista.getLength() > 0 && lista.item(0).getFirstChild() != null) {
                return lista.item(0).getFirstChild().getNodeValue().trim();
            }
        }
        return "";
    }
}