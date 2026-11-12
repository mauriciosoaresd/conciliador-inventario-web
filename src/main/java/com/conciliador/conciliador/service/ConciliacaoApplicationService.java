package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

@Service
public class ConciliacaoApplicationService {

    private final CsvService csvService;
    private final DocumentoFiscalService documentoFiscalService;
    private final ConciliacaoService conciliacaoService;
    private final ExcelService excelService;

    public ConciliacaoApplicationService(
            CsvService csvService,
            DocumentoFiscalService documentoFiscalService,
            ConciliacaoService conciliacaoService,
            ExcelService excelService
    ) {
        this.csvService = csvService;
        this.documentoFiscalService = documentoFiscalService;
        this.conciliacaoService = conciliacaoService;
        this.excelService = excelService;
    }

    public ByteArrayInputStream conciliar(MultipartFile arquivoCsv, List<MultipartFile> arquivosDocumentos) {
        List<ProdutoInventario> produtosInventario = csvService.lerCsv(arquivoCsv);
        List<ItemNotaFiscal> itensNotas = documentoFiscalService.extrairItens(arquivosDocumentos);
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                produtosInventario,
                itensNotas
        );
        return excelService.gerarRelatorioExcel(resultados);
    }
}
