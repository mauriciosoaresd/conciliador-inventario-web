package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentoFiscalService {

    private static final Pattern NUMERO_NOTA = Pattern.compile(
            "(?i)(?:n[ºo°]|nf-?e|nota\\s*fiscal)[^0-9]*([0-9]+)"
    );

    private final DocumentoParserService documentoParserService;
    private final XmlService xmlService;

    public DocumentoFiscalService(DocumentoParserService documentoParserService, XmlService xmlService) {
        this.documentoParserService = documentoParserService;
        this.xmlService = xmlService;
    }

    public List<ItemNotaFiscal> extrairItens(List<MultipartFile> arquivosDocumentos) {
        List<ItemNotaFiscal> itens = new ArrayList<>();
        for (MultipartFile documento : arquivosDocumentos) {
            if (documento.isEmpty()) {
                continue;
            }

            String nomeArquivo = documento.getOriginalFilename();
            if (nomeArquivo != null && nomeArquivo.toLowerCase(Locale.ROOT).endsWith(".xml")) {
                itens.addAll(xmlService.lerXml(documento));
            } else {
                itens.addAll(extrairItensPorOcr(documento, nomeArquivo));
            }
        }
        return itens;
    }

    private List<ItemNotaFiscal> extrairItensPorOcr(MultipartFile documento, String nomeArquivo) {
        String textoExtraido = documentoParserService.extrairTexto(documento);
        List<Double> quantidadesColuna = documentoParserService.extrairQuantidadesColuna(documento);
        String[] linhas = textoExtraido.split("\n");
        long totalLinhasProduto = Arrays.stream(linhas)
                .map(String::trim)
                .filter(documentoParserService::contemValorMonetario)
                .count();
        boolean quantidadesColunaAlinhadas = quantidadesColuna.size() == totalLinhasProduto;
        int indiceLinhaProduto = 0;
        String numeroNota = extrairNumeroNota(textoExtraido);

        List<ItemNotaFiscal> itens = new ArrayList<>();
        for (String linha : linhas) {
            String linhaTrim = linha.trim();
            if (linhaTrim.length() <= 3) {
                continue;
            }

            ItemNotaFiscal item = new ItemNotaFiscal();
            item.setCodigoProdutoFornecedor("OCR-GENERIC");
            item.setDescricaoProduto(linhaTrim);

            Double quantidade = null;
            if (documentoParserService.contemValorMonetario(linhaTrim)) {
                if (quantidadesColunaAlinhadas) {
                    quantidade = quantidadesColuna.get(indiceLinhaProduto);
                } else {
                    var quantidadeDetectada = documentoParserService.extrairQuantidadeDaLinha(linhaTrim);
                    if (quantidadeDetectada.isPresent()) {
                        quantidade = quantidadeDetectada.getAsDouble();
                    }
                }
                indiceLinhaProduto++;
            }

            item.setQuantidade(quantidade);
            item.setNomeArquivoOrigem(nomeArquivo);
            item.setNumeroNota(numeroNota);
            itens.add(item);
        }
        return itens;
    }

    private String extrairNumeroNota(String texto) {
        Matcher matcher = NUMERO_NOTA.matcher(texto);
        return matcher.find() ? matcher.group(1) : "-";
    }
}
