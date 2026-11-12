package com.conciliador.conciliador.controller;

import com.conciliador.conciliador.service.ConciliacaoApplicationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class ConciliadorController {

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
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=relatorio-conciliacao-hibrido.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(excelStream));
    }
}