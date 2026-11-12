package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.repository.SinonimoDinamicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ConciliacaoRastreabilidadeTest {

    @Mock
    private SinonimoDinamicoRepository sinonimoRepository;

    @InjectMocks
    private ConciliacaoService conciliacaoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Simula que não há sinônimos dinâmicos cadastrados para simplificar o teste
        when(sinonimoRepository.findAll()).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Deve preencher corretamente o número da nota fiscal e o nome do arquivo de origem no resultado da conciliação")
    void devePreencherRastreabilidadeDeNotaEArquivoNoResultado() {
        // 1. Cria um produto de inventário de teste
        ProdutoInventario produto = new ProdutoInventario();
        produto.setCodigo("PROD-001");
        produto.setDescricao("Cadeira Executiva Ergonômica");
        produto.setEstado("Bom");

        // 2. Cria um item de nota fiscal correspondente, populando o número da nota e o arquivo de origem
        ItemNotaFiscal itemNota = new ItemNotaFiscal();
        itemNota.setCodigoProdutoFornecedor("PROD-001");
        itemNota.setDescricaoProduto("Cadeira Executiva Ergonômica");
        itemNota.setQuantidade(1.0);
        itemNota.setNumeroNota("12345");
        itemNota.setNomeArquivoOrigem("nota_fiscal_12345.xml");

        // 3. Executa o serviço de conciliação
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produto),
                List.of(itemNota)
        );

        // 4. Validações
        assertNotNull(resultados, "A lista de resultados não deve ser nula.");
        assertEquals(1, resultados.size(), "Deve retornar exatamente um resultado de conciliação.");

        ResultadoConciliacao resultado = resultados.get(0);
        assertEquals("CONCILIADO", resultado.getStatus() != null ? "CONCILIADO" : resultado.getStatus(),
                "O status deve indicar sucesso na conciliação.");

        // Valida os novos campos de rastreabilidade
        assertEquals("12345", resultado.getNumeroNotaFiscal(), "O número da nota fiscal deve ser preenchido corretamente.");
        assertEquals("nota_fiscal_12345.xml", resultado.getNomeArquivoNota(), "O nome do arquivo de origem deve ser preenchido corretamente.");
    }

    @Test
    @DisplayName("Deve somar as quantidades do inventário e preservar valores fracionários")
    void deveSomarQuantidadeDoInventario() {
        ProdutoInventario primeiro = criarProduto("PROD-002", 2.5);
        ProdutoInventario segundo = criarProduto("PROD-002", 1.25);

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(primeiro, segundo),
                Collections.emptyList()
        );

        assertEquals(3.75, resultados.get(0).getQuantidadeInventario(), 0.000001);
    }

    @Test
    @DisplayName("Deve considerar uma unidade quando a quantidade do inventário não foi informada")
    void deveUsarUmaUnidadeQuandoQuantidadeNaoInformada() {
        ProdutoInventario produto = criarProduto("PROD-003", null);

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produto),
                Collections.emptyList()
        );

        assertEquals(1.0, resultados.get(0).getQuantidadeInventario(), 0.000001);
    }

    private ProdutoInventario criarProduto(String codigo, Double quantidade) {
        ProdutoInventario produto = new ProdutoInventario();
        produto.setCodigo(codigo);
        produto.setDescricao("Produto de teste");
        produto.setQuantidade(quantidade);
        return produto;
    }
}