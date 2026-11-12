package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.repository.SinonimoDinamicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConciliacaoServiceTest {

    @Mock
    private SinonimoDinamicoRepository sinonimoRepository;

    @InjectMocks
    private ConciliacaoService conciliacaoService;

    @BeforeEach
    void setUp() {
        lenient().when(sinonimoRepository.findAll()).thenReturn(Collections.emptyList());
    }

    @Test
    void deveClassificarQuantidadeFisicaMaiorComoSobra() {
        ResultadoConciliacao resultado = conciliar(
                produto("SKU-1", "Cadeira executiva", 4.0),
                item("SKU-1", "Cadeira executiva", 2.0)
        ).get(0);

        assertEquals(4.0, resultado.getQuantidadeInventario());
        assertEquals(2.0, resultado.getQuantidadeNotaFiscal());
        assertEquals("DIVERGENTE - SOBRA", resultado.getStatus());
    }

    @Test
    void deveClassificarQuantidadeFiscalMaiorComoFalta() {
        ResultadoConciliacao resultado = conciliar(
                produto("SKU-1", "Cadeira executiva", 1.0),
                item("SKU-1", "Cadeira executiva", 3.0)
        ).get(0);

        assertEquals(1.0, resultado.getQuantidadeInventario());
        assertEquals(3.0, resultado.getQuantidadeNotaFiscal());
        assertEquals("DIVERGENTE - FALTA", resultado.getStatus());
    }

    @Test
    void deveConciliarPorDescricaoQuandoCodigoNaoCoincide() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Cadeira executiva ergonômica", 2.0),
                item("SKU-NF", "Cadeira executiva ergonômica", 2.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("Cadeira executiva ergonômica", resultados.get(0).getDescricaoNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveExigirRevisaoQuandoDescricaoCoincideMasDimensoesDivergem() {
        ResultadoConciliacao resultado = conciliar(
                produto("SKU-INV", "Mesa modelo 123 120x60 cm", 1.0),
                item("SKU-NF", "Mesa modelo 123 140x70 cm", 1.0)
        ).get(0);

        assertEquals("REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA", resultado.getStatus());
    }

    @Test
    void naoDeveConciliarProdutosComEspecificacoesNumericasDiferentes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Notebook modelo 123 8GB", 1.0),
                item("SKU-NF", "Notebook modelo 456 16GB", 1.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("NÃO ENCONTRADO NO DOCUMENTO", resultados.get(0).getStatus());
        assertEquals("-", resultados.get(0).getDescricaoNotaFiscal());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
        assertEquals("Notebook modelo 456 16GB", resultados.get(1).getDescricaoNotaFiscal());
    }

    @Test
    void deveIgnorarDescricaoDeCabecalhoNosItensExclusivosDaNota() {
        ItemNotaFiscal cabecalho = item(null, "DESCRICAO DOS PRODUTOS", 1.0);
        ItemNotaFiscal itemSemInventario = item(null, "Mouse sem fio Logitech modelo MX", 2.0);

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                Collections.emptyList(),
                List.of(cabecalho, itemSemInventario)
        );

        assertEquals(1, resultados.size());
        assertEquals("Mouse sem fio Logitech modelo MX", resultados.get(0).getDescricaoNotaFiscal());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(0).getStatus());
        assertEquals(0.0, resultados.get(0).getQuantidadeInventario());
    }

    private List<ResultadoConciliacao> conciliar(ProdutoInventario produto, ItemNotaFiscal item) {
        return conciliacaoService.conciliarInventarioComDocumentos(List.of(produto), List.of(item));
    }

    private ProdutoInventario produto(String codigo, String descricao, Double quantidade) {
        ProdutoInventario produto = new ProdutoInventario();
        produto.setCodigo(codigo);
        produto.setDescricao(descricao);
        produto.setQuantidade(quantidade);
        return produto;
    }

    private ItemNotaFiscal item(String codigo, String descricao, double quantidade) {
        ItemNotaFiscal item = new ItemNotaFiscal();
        item.setCodigoProdutoFornecedor(codigo);
        item.setDescricaoProduto(descricao);
        item.setQuantidade(quantidade);
        return item;
    }
}
