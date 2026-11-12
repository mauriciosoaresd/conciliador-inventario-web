package com.conciliador.conciliador.controller;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.service.ConciliacaoService;
import com.conciliador.conciliador.service.CsvService;
import com.conciliador.conciliador.service.DocumentoParserService;
import com.conciliador.conciliador.service.ExcelService;
import com.conciliador.conciliador.service.XmlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class ConciliadorController {

    @Autowired
    private CsvService csvService;

    @Autowired
    private XmlService xmlService;

    @Autowired
    private DocumentoParserService documentoParserService;

    @Autowired
    private ConciliacaoService conciliacaoService;

    @Autowired
    private ExcelService excelService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/conciliar")
    public ResponseEntity<InputStreamResource> conciliar(
            @RequestParam("inventario") MultipartFile arquivoCsv,
            @RequestParam("documentos") List<MultipartFile> arquivosDocumentos) {
        try {
            // 1. Lê a planilha de inventário CSV
            List<ProdutoInventario> produtosInventario = csvService.lerCsv(arquivoCsv);

            // 2. Extrai itens de notas fiscais (XML, PDF ou Imagens via OCR)
            List<ItemNotaFiscal> itensNotas = new ArrayList<>();

            for (MultipartFile doc : arquivosDocumentos) {
                if (doc.isEmpty()) continue;

                String nomeArquivo = doc.getOriginalFilename();
                if (nomeArquivo != null && nomeArquivo.toLowerCase().endsWith(".xml")) {
                    // Processamento via XML estruturado oficial (já preenche no XmlService)
                    itensNotas.addAll(xmlService.lerXml(doc));
                } else {
                    // Processamento via PDF ou Imagem com OCR/PDFBox
                    String textoExtraido = documentoParserService.extrairTexto(doc);
                    List<Double> quantidadesColuna = documentoParserService.extrairQuantidadesColuna(doc);
                    String[] linhas = textoExtraido.split("\n");
                    long totalLinhasProduto = Arrays.stream(linhas)
                            .map(String::trim)
                            .filter(documentoParserService::contemValorMonetario)
                            .count();
                    boolean quantidadesColunaAlinhadas = quantidadesColuna.size() == totalLinhasProduto;
                    int indiceLinhaProduto = 0;

                    // Tenta extrair o número da nota do texto extraído usando Regex
                    String numeroNotaEncontrado = "-";
                    Pattern pattern = Pattern.compile(
                            "(?i)(?:n[ºo°]|nf-?e|nota\\s*fiscal)[^0-9]*([0-9]+)"
                    );
                    Matcher matcher = pattern.matcher(textoExtraido);
                    if (matcher.find()) {
                        numeroNotaEncontrado = matcher.group(1);
                    }

                    for (String linha : linhas) {
                        String linhaTrim = linha.trim();
                        if (linhaTrim.length() > 3) {
                            ItemNotaFiscal itemOcr = new ItemNotaFiscal();
                            itemOcr.setCodigoProdutoFornecedor("OCR-GENERIC");
                            itemOcr.setDescricaoProduto(linhaTrim);

                            double qtdExtraida = 1.0;
                            if (documentoParserService.contemValorMonetario(linhaTrim)) {
                                if (quantidadesColunaAlinhadas) {
                                    qtdExtraida = quantidadesColuna.get(indiceLinhaProduto);
                                } else {
                                    qtdExtraida = documentoParserService.extrairQuantidadeDaLinha(linhaTrim).orElse(1.0);
                                }
                                indiceLinhaProduto++;
                            }

                            itemOcr.setQuantidade(qtdExtraida);
                            itemOcr.setNomeArquivoOrigem(nomeArquivo);
                            itemOcr.setNumeroNota(numeroNotaEncontrado);

                            itensNotas.add(itemOcr);
                        }
                    }
                }
            }

            // 3. Executa o Motor Híbrido de Conciliação (Stemming + Levenshtein + JPA Dynamic Synonyms)
            List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(produtosInventario, itensNotas);

            // 4. Gera o relatório Excel final
            ByteArrayInputStream excelStream = excelService.gerarRelatorioExcel(resultados);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=relatorio-conciliacao-hibrido.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(excelStream));

        } catch (Exception e) {
            throw new RuntimeException("Erro ao realizar a conciliação híbrida: " + e.getMessage(), e);
        }
    }
}