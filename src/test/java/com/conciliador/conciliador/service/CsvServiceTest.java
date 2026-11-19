package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ProdutoInventario;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvServiceTest {

    private final CsvService csvService = new CsvService();

    @Test
    void deveLerInventarioNoFormatoPadraoDeCincoColunas() {
        MockMultipartFile arquivo = new MockMultipartFile(
                "inventario",
                "inventario.csv",
                "text/csv",
                ("codigo,descricao,marca,modelo,estado\n"
                        + "0001,Notebook,Lenovo,IdeaPad 3077,Muito Bom\n"
                        + "0002,Notebook,Lenovo,IdeaPad 3014,Novo\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        List<ProdutoInventario> produtos = csvService.lerCsv(arquivo);

        assertEquals(2, produtos.size());
        assertEquals("0001", produtos.get(0).getCodigo());
        assertEquals("Notebook", produtos.get(0).getDescricao());
        assertEquals("Lenovo", produtos.get(0).getMarca());
        assertEquals("IdeaPad 3077", produtos.get(0).getModelo());
        assertEquals("Muito Bom", produtos.get(0).getEstado());
        assertEquals(1.0, produtos.get(0).getQuantidade());
    }
}
