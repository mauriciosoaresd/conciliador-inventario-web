package com.conciliador.conciliador.controller;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.service.CsvService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return "index"; // Vai carregar o arquivo index.html na pasta templates
    }

    @PostMapping("/conciliar")
    public ResponseEntity<InputStreamResource> conciliar(
            @RequestParam("inventario") MultipartFile arquivoCsv,
            @RequestParam("xmls") List<MultipartFile> arquivosXml) {
        try {
            // 1. Ler o Inventário Físico (CSV)
            List<ProdutoInventario> produtosInventario = csvService.lerCsv(arquivoCsv);

            // 2. Ler todas as Notas Fiscais (XMLs)
            List<ItemNotaFiscal> itensNotas = new ArrayList<>();
            for (MultipartFile xml : arquivosXml) {
                if (!xml.isEmpty()) {
                    itensNotas.addAll(xmlService.lerXml(xml));
                }
            }

            // Mapear quantidades totais por código do produto na Nota Fiscal
            Map<String, Double> somaNotasPorCodigo = itensNotas.stream()
                    .collect(Collectors.groupingBy(
                            ItemNotaFiscal::getCodigoProdutoFornecedor,
                            Collectors.summingDouble(ItemNotaFiscal::getQuantidade)
                    ));

            // 3. Cruzamento / Motor de Conciliação
            List<ResultadoConciliacao> resultados = new ArrayList<>();
            for (ProdutoInventario prod : produtosInventario) {
                double qtdNota = somaNotasPorCodigo.getOrDefault(prod.getCodigo(), 0.0);
                int qtdInventario = 1; // Cada linha do CSV costuma representar 1 item físico unitário ou ajuste

                String status = "OK";
                if (qtdInventario > qtdNota) {
                    status = "DIVERGENTE - SOBRA";
                } else if (qtdInventario < qtdNota) {
                    status = "DIVERGENTE - FALTA";
                }

                resultados.add(new ResultadoConciliacao(
                        prod.getCodigo(),
                        prod.getDescricao(),
                        prod.getEstado(),
                        qtdInventario,
                        qtdNota,
                        status
                ));
            }

            // 4. Gerar Relatório Excel
            ByteArrayInputStream excelStream = excelService.gerarRelatorioExcel(resultados);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=relatorio-conciliacao.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(excelStream));

        } catch (Exception e) {
            throw new RuntimeException("Erro ao realizar a conciliação: " + e.getMessage(), e);
        }
    }
}