package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.exception.RequisicaoInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;

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
        validarArquivos(arquivoCsv, arquivosDocumentos);
        List<ProdutoInventario> produtosInventario = csvService.lerCsv(arquivoCsv);
        List<ItemNotaFiscal> itensNotas = documentoFiscalService.extrairItens(arquivosDocumentos);
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                produtosInventario,
                itensNotas
        );
        return excelService.gerarRelatorioExcel(resultados);
    }

    private void validarArquivos(MultipartFile arquivoCsv, List<MultipartFile> arquivosDocumentos) {
        if (arquivoCsv == null || arquivoCsv.isEmpty()) {
            throw new RequisicaoInvalidaException("Envie um arquivo CSV de inventário não vazio.");
        }
        if (!temExtensao(arquivoCsv, "csv")) {
            throw new RequisicaoInvalidaException("O inventário deve estar no formato CSV.");
        }
        if (arquivosDocumentos == null || arquivosDocumentos.isEmpty()) {
            throw new RequisicaoInvalidaException("Envie ao menos um documento fiscal.");
        }

        for (MultipartFile documento : arquivosDocumentos) {
            if (documento == null || documento.isEmpty()) {
                throw new RequisicaoInvalidaException("Um dos documentos enviados está vazio.");
            }
            if (!temExtensao(documento, "xml", "pdf", "png", "jpg", "jpeg")) {
                throw new RequisicaoInvalidaException(
                        "Formato de documento não suportado. Envie XML, PDF, PNG, JPG ou JPEG."
                );
            }
        }
    }

    private boolean temExtensao(MultipartFile arquivo, String... extensoesPermitidas) {
        String nome = arquivo.getOriginalFilename();
        if (nome == null) {
            return false;
        }
        String nomeNormalizado = nome.toLowerCase(Locale.ROOT);
        for (String extensao : extensoesPermitidas) {
            if (nomeNormalizado.endsWith("." + extensao)) {
                return true;
            }
        }
        return false;
    }
}
