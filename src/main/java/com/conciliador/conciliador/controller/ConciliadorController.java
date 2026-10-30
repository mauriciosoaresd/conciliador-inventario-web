package com.conciliador.conciliador.controller;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.service.CsvService;
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
    private ExcelService excelService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/conciliar")
    public ResponseEntity<InputStreamResource> conciliar(
            @RequestParam("inventario") MultipartFile arquivoCsv,
            @RequestParam("xmls") List<MultipartFile> arquivosXml) {
        try {
            List<ProdutoInventario> produtosInventario = csvService.lerCsv(arquivoCsv);

            List<ItemNotaFiscal> itensNotas = new ArrayList<>();
            for (MultipartFile xml : arquivosXml) {
                if (!xml.isEmpty()) {
                    itensNotas.addAll(xmlService.lerXml(xml));
                }
            }

            List<ResultadoConciliacao> resultados = new ArrayList<>();

            // Motor de Conciliação em Camadas com Jaccard e Regex
            for (ProdutoInventario prod : produtosInventario) {
                ItemNotaFiscal itemMatch = null;
                String status = "OK";
                double qtdNota = 0.0;
                int qtdInventario = 1; // Padrão físico unitário do CSV

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

                        // Calcula o grau de similaridade estatística entre as descrições
                        double escore = MatchUtils.calcularSimilaridadeJaccard(descInvNorm, descNotaNorm);

                        if (escore > maiorEscore) {
                            maiorEscore = escore;
                            melhorCandidato = item;
                        }
                    }

                    // Se o escore de similaridade passar do limiar de segurança (30% de interseção de termos)
                    if (maiorEscore >= 0.3 && melhorCandidato != null) {
                        itemMatch = melhorCandidato;

                        // CAMADA 3: Verificação Dinâmica de Ambiguidade de Medidas via Regex
                        Set<String> dimensoesInventario = MatchUtils.extrairDimensoes(prod.getDescricao());
                        Set<String> dimensoesNota = MatchUtils.extrairDimensoes(itemMatch.getDescricaoProduto());

                        // Se ambos possuem dimensões descritas, mas elas diferem, marca para revisão manual
                        if (!dimensoesInventario.isEmpty() && !dimensoesNota.isEmpty() && !dimensoesInventario.equals(dimensoesNota)) {
                            status = "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA (Inv: " + dimensoesInventario + " vs NF: " + dimensoesNota + ")";
                        }
                    }
                }

                // Definindo o Status final com base na quantidade
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
                    status = "NÃO ENCONTRADO NA NOTA FISCAL";
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
            headers.add("Content-Disposition", "attachment; filename=relatorio-conciliacao-jaccard.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(excelStream));

        } catch (Exception e) {
            throw new RuntimeException("Erro ao realizar a conciliação por Jaccard: " + e.getMessage(), e);
        }
    }
}