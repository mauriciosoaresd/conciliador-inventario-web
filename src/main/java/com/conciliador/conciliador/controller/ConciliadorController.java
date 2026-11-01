package com.conciliador.conciliador.controller;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.service.CsvService;
import com.conciliador.conciliador.service.DocumentoParserService;
import com.conciliador.conciliador.service.ExcelService;
import com.conciliador.conciliador.service.MatchUtils;
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
import java.util.List;
import java.util.Set;

@Controller
public class ConciliadorController {

    @Autowired
    private CsvService csvService;

    @Autowired
    private XmlService xmlService;

    @Autowired
    private DocumentoParserService documentoParserService;

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
            List<ProdutoInventario> produtosInventario = csvService.lerCsv(arquivoCsv);

            List<ItemNotaFiscal> itensNotas = new ArrayList<>();

            for (MultipartFile doc : arquivosDocumentos) {
                if (doc.isEmpty()) continue;

                String nomeArquivo = doc.getOriginalFilename();
                if (nomeArquivo != null && nomeArquivo.toLowerCase().endsWith(".xml")) {
                    // Processamento padrão via XML estruturado
                    itensNotas.addAll(xmlService.lerXml(doc));
                } else {
                    // Processamento via PDF ou Imagem com OCR/PDFBox
                    String textoExtraido = documentoParserService.extrairTexto(doc);

                    // Converte as linhas do texto extraído em itens de nota fiscal simulados
                    // para que o motor de Jaccard consiga cruzar as descrições
                    String[] linhas = textoExtraido.split("\n");
                    for (String linha : linhas) {
                        String linhaTrim = linha.trim();
                        if (linhaTrim.length() > 3) { // Ignora linhas muito curtas/vazias
                            ItemNotaFiscal itemOcr = new ItemNotaFiscal();
                            itemOcr.setCodigoProdutoFornecedor("OCR-GENERIC");
                            itemOcr.setDescricaoProduto(linhaTrim);
                            itemOcr.setQuantidade(1.0); // Quantidade padrão unitária estimada
                            itensNotas.add(itemOcr);
                        }
                    }
                }
            }

            List<ResultadoConciliacao> resultados = new ArrayList<>();

            // Motor de Conciliação em Camadas com Jaccard e Regex
            for (ProdutoInventario prod : produtosInventario) {
                ItemNotaFiscal itemMatch = null;
                String status = "OK";
                double qtdNota = 0.0;
                int qtdInventario = 1;

                // CAMADA 1: Match exato por Código/SKU
                for (ItemNotaFiscal item : itensNotas) {
                    if (item.getCodigoProdutoFornecedor() != null &&
                            item.getCodigoProdutoFornecedor().equalsIgnoreCase(prod.getCodigo())) {
                        itemMatch = item;
                        break;
                    }
                }

                // CAMADA 2: Match por Similaridade Avançada (Índice de Jaccard >= 0.3)
                if (itemMatch == null) {
                    String descInvNorm = MatchUtils.normalizar(prod.getDescricao());

                    double maiorEscore = 0.0;
                    ItemNotaFiscal melhorCandidato = null;

                    for (ItemNotaFiscal item : itensNotas) {
                        String descNotaNorm = MatchUtils.normalizar(item.getDescricaoProduto());
                        double escore = MatchUtils.calcularSimilaridadeJaccard(descInvNorm, descNotaNorm);

                        if (escore > maiorEscore) {
                            maiorEscore = escore;
                            melhorCandidato = item;
                        }
                    }

                    if (maiorEscore >= 0.3 && melhorCandidato != null) {
                        itemMatch = melhorCandidato;

                        // CAMADA 3: Verificação Dinâmica de Ambiguidade de Medidas via Regex
                        Set<String> dimensoesInventario = MatchUtils.extrairDimensoes(prod.getDescricao());
                        Set<String> dimensoesNota = MatchUtils.extrairDimensoes(itemMatch.getDescricaoProduto());

                        if (!dimensoesInventario.isEmpty() && !dimensoesNota.isEmpty() && !dimensoesInventario.equals(dimensoesNota)) {
                            status = "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA (Inv: " + dimensoesInventario + " vs NF: " + dimensoesNota + ")";
                        }
                    }
                }

                if (itemMatch != null) {
                    qtdNota = itemMatch.getQuantidade();
                    if (!status.startsWith("REVISÃO")) {
                        if (qtdInventario > qtdNota) {
                            status = "DIVERGENTE - SOBRA";
                        } else if (qtdInventario < qtdNota) {
                            status = "DIVERGENTE - FALTA";
                        }
                    }
                } else {
                    status = "NÃO ENCONTRADO NO DOCUMENTO";
                }

                resultados.add(new ResultadoConciliacao(
                        prod.getCodigo(),
                        prod.getDescricao(),
                        itemMatch != null ? itemMatch.getDescricaoProduto() : null,
                        prod.getEstado(),
                        qtdInventario,
                        qtdNota,
                        status
                ));
            }

            ByteArrayInputStream excelStream = excelService.gerarRelatorioExcel(resultados);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=relatorio-conciliacao-misto.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(excelStream));

        } catch (Exception e) {
            throw new RuntimeException("Erro ao realizar a conciliação mista: " + e.getMessage(), e);
        }
    }
}