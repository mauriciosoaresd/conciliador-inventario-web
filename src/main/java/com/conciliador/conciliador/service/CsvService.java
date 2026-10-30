package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ProdutoInventario;
import com.opencsv.bean.CsvToBeanBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CsvService {

    public List<ProdutoInventario> lerCsv(MultipartFile arquivo) {
        try (Reader reader = new BufferedReader(new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {
            return new CsvToBeanBuilder<ProdutoInventario>(reader)
                    .withType(ProdutoInventario.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar o arquivo CSV de inventário: " + e.getMessage(), e);
        }
    }
}