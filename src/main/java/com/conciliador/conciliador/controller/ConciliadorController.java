package com.conciliador.conciliador.controller;

import com.conciliador.conciliador.service.ConciliacaoApplicationService;
import com.conciliador.conciliador.service.ConciliacaoService;
import com.conciliador.conciliador.exception.RequisicaoInvalidaException;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Controller
public class ConciliadorController {

    private static final String REVISAO_SESSION_KEY = ConciliadorController.class.getName() + ".revisao";
    private final ConciliacaoApplicationService conciliacaoApplicationService;

    public ConciliadorController(ConciliacaoApplicationService conciliacaoApplicationService) {
        this.conciliacaoApplicationService = conciliacaoApplicationService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/conciliar")
    public ResponseEntity<InputStreamResource> conciliar(
            @RequestParam("inventario") MultipartFile arquivoCsv,
            @RequestParam("documentos") List<MultipartFile> arquivosDocumentos) {
        var excelStream = conciliacaoApplicationService.conciliar(arquivoCsv, arquivosDocumentos);
        return respostaExcel(excelStream);
    }

    @PostMapping("/conciliar/revisao")
    public String prepararRevisao(
            @RequestParam("inventario") MultipartFile arquivoCsv,
            @RequestParam("documentos") List<MultipartFile> arquivosDocumentos,
            HttpSession session,
            Model model
    ) {
        ConciliacaoApplicationService.ConciliacaoRevisao revisao =
                conciliacaoApplicationService.prepararRevisao(arquivoCsv, arquivosDocumentos);
        session.setAttribute(REVISAO_SESSION_KEY, revisao);
        model.addAttribute(
                "pendenciasNotaFiscal",
                revisao.analise().pendencias()
        );
        model.addAttribute(
                "saldosFisicosSemNota",
                revisao.analise().resultados().stream()
                        .filter(resultado -> resultado.getStatus().startsWith("SEM NF CORRESPONDENTE"))
                        .toList()
        );
        return "revisao";
    }

    @PostMapping("/conciliar/gerar")
    public ResponseEntity<InputStreamResource> gerarRelatorioRevisado(
            @RequestParam(value = "associacoes", required = false) List<String> associacoesRecebidas,
            HttpSession session
    ) {
        Object revisaoSessao = session.getAttribute(REVISAO_SESSION_KEY);
        if (!(revisaoSessao instanceof ConciliacaoApplicationService.ConciliacaoRevisao revisao)) {
            throw new RequisicaoInvalidaException(
                    "A revisão expirou ou não foi iniciada. Envie novamente os arquivos."
            );
        }
        List<ConciliacaoService.AssociacaoManual> associacoes = parseAssociacoes(associacoesRecebidas);
        var excelStream = conciliacaoApplicationService.gerarRelatorio(revisao, associacoes);
        session.removeAttribute(REVISAO_SESSION_KEY);
        return respostaExcel(excelStream);
    }

    private ResponseEntity<InputStreamResource> respostaExcel(java.io.ByteArrayInputStream excelStream) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=relatorio-conciliacao-hibrido.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(excelStream));
    }

    private List<ConciliacaoService.AssociacaoManual> parseAssociacoes(List<String> associacoesRecebidas) {
        if (associacoesRecebidas == null) return List.of();
        List<ConciliacaoService.AssociacaoManual> associacoes = new ArrayList<>();
        try {
            for (String associacao : associacoesRecebidas) {
                if (associacao == null || associacao.isBlank()) continue;
                String[] indices = associacao.split(":", -1);
                if (indices.length != 2) {
                    throw new NumberFormatException("Formato inválido");
                }
                associacoes.add(new ConciliacaoService.AssociacaoManual(
                        Integer.parseInt(indices[0]),
                        Integer.parseInt(indices[1])
                ));
            }
        } catch (NumberFormatException exception) {
            throw new RequisicaoInvalidaException("Uma das sugestões selecionadas é inválida.", exception);
        }
        return List.copyOf(associacoes);
    }
}