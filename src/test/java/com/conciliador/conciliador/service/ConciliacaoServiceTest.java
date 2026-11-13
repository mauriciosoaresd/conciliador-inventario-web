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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void deveExibirSaldoFisicoSemNotaDepoisDasAlocacoes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-1", "Cadeira executiva", 4.0),
                item("SKU-1", "Cadeira executiva", 2.0)
        );

        assertEquals(2, resultados.size());
        assertEquals(2.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(2.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals(2.0, resultados.get(1).getQuantidadeInventario());
        assertNull(resultados.get(1).getQuantidadeNotaFiscal());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(1).getStatus());
    }

    @Test
    void deveExibirExcedenteFiscalSemAlocarMaisQueOInventario() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-1", "Cadeira executiva", 1.0),
                item("SKU-1", "Cadeira executiva", 3.0)
        );

        assertEquals(2, resultados.size());
        assertEquals(1.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(1.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
        assertNull(resultados.get(1).getQuantidadeInventario());
        assertEquals(2.0, resultados.get(1).getQuantidadeNotaFiscal());
        assertEquals("ITEM APENAS NA NF (EXCEDENTE NÃO ALOCADO AO INVENTÁRIO)", resultados.get(1).getStatus());
    }

    @Test
    void deveSomarLinhasFiscaisRepetidasComMesmoCodigo() {
        ItemNotaFiscal primeiraLinha = item("SKU-1", "Cadeira executiva", 1.0);
        ItemNotaFiscal segundaLinha = item("SKU-1", "Cadeira executiva", 2.0);

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produto("SKU-1", "Cadeira executiva", 4.0)),
                List.of(primeiraLinha, segundaLinha)
        );

        assertEquals(2, resultados.size());
        assertEquals(3.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(1).getStatus());
        assertEquals(1.0, resultados.get(1).getQuantidadeInventario());
    }

    @Test
    void deveSomarLinhasFiscaisRepetidasSemCodigoPelaDescricao() {
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produto(null, "Cadeira executiva", 3.0)),
                List.of(
                        item(null, "Cadeira executiva", 1.0),
                        item(null, "Cadeira executiva", 2.0)
                )
        );

        assertEquals(1, resultados.size());
        assertEquals(3.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void naoDeveAgruparProdutosSemCodigoComDescricoesDiferentes() {
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(
                        produto(null, "Cadeira executiva modelo 123", 1.0),
                        produto(null, "Mouse óptico sem fio modelo 456", 1.0)
                ),
                List.of(
                        item(null, "Cadeira executiva modelo 123", 1.0),
                        item(null, "Mouse óptico sem fio modelo 456", 1.0)
                )
        );

        assertEquals(2, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals("OK", resultados.get(1).getStatus());
    }

    @Test
    void deveExigirRevisaoQuandoQuantidadeFiscalNaoFoiIdentificada() {
        ItemNotaFiscal itemSemQuantidade = item("SKU-1", "Cadeira executiva", 1.0);
        itemSemQuantidade.setQuantidade(null);

        ResultadoConciliacao resultado = conciliar(
                produto("SKU-1", "Cadeira executiva", 5.0),
                itemSemQuantidade
        ).get(0);

        assertNull(resultado.getQuantidadeNotaFiscal());
        assertEquals("REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA", resultado.getStatus());
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
    void deveConciliarMedidasEquivalentesComToleranciaDeCincoCentimetros() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Gaveteiro Volante 3 Gavetas 40x50cm", 2.0),
                item("SKU-NF", "Gaveteiro Volante 3 Gavetas 0,40x0,50m", 2.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void naoDeveConciliarCadeiraPresidenteComCadeiraFixa() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Cadeira Presidente Giratoria Preta", 1.0),
                item("SKU-NF", "Cadeira Fixa Empilhavel Preta", 1.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void naoDeveConciliarSwitchesComQuantidadeDePortasDiferente() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Switch Cisco Catalyst 2960 24 portas", 2.0),
                item("SKU-NF", "Switch Cisco Catalyst 2960 48 portas", 2.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void naoDeveConciliarRacksComUnidadesDiferentes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Rack de Parede 12U 570mm Preto", 2.0),
                item("SKU-NF", "Rack de Parede 8U 570mm Preto", 2.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void deveConciliarMesmoModeloDeComputadorQuandoProcessadorERamDiferem() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Notebook Dell Latitude 3420 i5 8GB SSD 256GB", 2.0),
                item("SKU-NF", "Notebook Dell Latitude 3420 i7 16GB SSD 256GB", 2.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveExibirTodosOsEstadosQuandoAgrupaItensDoInventario() {
        ProdutoInventario bom = produto("SKU-A", "Armario Baixo", 1.0);
        bom.setEstado("Bom");
        ProdutoInventario novo = produto("SKU-B", "Armario Baixo", 1.0);
        novo.setEstado("Novo");

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(bom, novo),
                Collections.emptyList()
        );

        assertEquals("Bom, Novo", resultados.get(0).getEstado());
    }

    @Test
    void naoDeveConciliarProdutosComEspecificacoesNumericasDiferentes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Notebook modelo 123 8GB", 1.0),
                item("SKU-NF", "Notebook modelo 456 16GB", 1.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
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
        assertNull(resultados.get(0).getQuantidadeInventario());
    }

    @Test
    void deveSinalizarQuantidadeNaoIdentificadaEmItemExclusivoDaNota() {
        ItemNotaFiscal itemSemQuantidade = item("SKU-NF", "Mouse sem fio Logitech modelo MX", 1.0);
        itemSemQuantidade.setQuantidade(null);

        ResultadoConciliacao resultado = conciliacaoService.conciliarInventarioComDocumentos(
                Collections.emptyList(),
                List.of(itemSemQuantidade)
        ).get(0);

        assertNull(resultado.getQuantidadeNotaFiscal());
        assertEquals(
                "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA; ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)",
                resultado.getStatus()
        );
    }

    @Test
    void deveAlocarQuantidadesEntreNotasEExibirSaldoFisicoSemNota() {
        ItemNotaFiscal itemNotaA = item("COD-A", "Cadeira executiva", 5.0);
        itemNotaA.setIdentificadorDocumentoOrigem("documento-a");
        itemNotaA.setNumeroNota("NF-A");
        itemNotaA.setNomeArquivoOrigem("nota-a.xml");

        ItemNotaFiscal itemNotaB = item("COD-B", "Cadeira executiva", 5.0);
        itemNotaB.setIdentificadorDocumentoOrigem("documento-b");
        itemNotaB.setNumeroNota("NF-B");
        itemNotaB.setNomeArquivoOrigem("nota-b.xml");

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(
                        produto("SKU-1", "Cadeira executiva", 4.0),
                        produto("SKU-2", "Cadeira executiva", 6.0)
                ),
                List.of(itemNotaA, itemNotaB)
        );

        assertEquals(2, resultados.size());
        assertEquals("SKU-1, SKU-2", resultados.get(0).getCodigo());
        assertEquals("NF-A", resultados.get(0).getNumeroNotaFiscal());
        assertEquals("nota-a.xml", resultados.get(0).getNomeArquivoNota());
        assertEquals(5.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(5.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals("NF-B", resultados.get(1).getNumeroNotaFiscal());
        assertEquals("nota-b.xml", resultados.get(1).getNomeArquivoNota());
        assertEquals(5.0, resultados.get(1).getQuantidadeInventario());
        assertEquals(5.0, resultados.get(1).getQuantidadeNotaFiscal());
        assertEquals("OK", resultados.get(1).getStatus());
    }

    @Test
    void deveAlocarOitoUnidadesDeDezEDeixarDuasSemNotaFiscal() {
        ItemNotaFiscal itemNotaA = item("COD-A", "Cadeira executiva", 4.0);
        itemNotaA.setIdentificadorDocumentoOrigem("documento-a");
        itemNotaA.setNumeroNota("NF-A");
        ItemNotaFiscal itemNotaB = item("COD-B", "Cadeira executiva", 4.0);
        itemNotaB.setIdentificadorDocumentoOrigem("documento-b");
        itemNotaB.setNumeroNota("NF-B");

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produto("SKU", "Cadeira executiva", 10.0)),
                List.of(itemNotaA, itemNotaB)
        );

        assertEquals(3, resultados.size());
        assertEquals(4.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(4.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("NF-A", resultados.get(0).getNumeroNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals(4.0, resultados.get(1).getQuantidadeInventario());
        assertEquals(4.0, resultados.get(1).getQuantidadeNotaFiscal());
        assertEquals("NF-B", resultados.get(1).getNumeroNotaFiscal());
        assertEquals("OK", resultados.get(1).getStatus());
        assertEquals(2.0, resultados.get(2).getQuantidadeInventario());
        assertNull(resultados.get(2).getQuantidadeNotaFiscal());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(2).getStatus());
    }

    @Test
    void naoDeveMarcarComoSemNotaSaldoQuandoHaQuantidadeFiscalDesconhecida() {
        ItemNotaFiscal quantidadeConhecida = item("COD-A", "Cadeira executiva", 4.0);
        quantidadeConhecida.setIdentificadorDocumentoOrigem("documento-a");
        ItemNotaFiscal quantidadeDesconhecida = item("COD-B", "Cadeira executiva", 1.0);
        quantidadeDesconhecida.setQuantidade(null);
        quantidadeDesconhecida.setIdentificadorDocumentoOrigem("documento-b");

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produto("SKU", "Cadeira executiva", 10.0)),
                List.of(quantidadeConhecida, quantidadeDesconhecida)
        );

        assertEquals(2, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals(4.0, resultados.get(0).getQuantidadeInventario());
        assertEquals("REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA", resultados.get(1).getStatus());
        assertEquals(6.0, resultados.get(1).getQuantidadeInventario());
        assertNull(resultados.get(1).getQuantidadeNotaFiscal());
    }

    @Test
    void deveSinalizarQuantidadeFiscalZeroParaRevisao() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU", "Cadeira executiva", 2.0),
                item("SKU-NF", "Cadeira executiva", 0.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("REVISÃO MANUAL - QUANTIDADE FISCAL ZERO", resultados.get(0).getStatus());
        assertEquals(0.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(1).getStatus());
        assertEquals(2.0, resultados.get(1).getQuantidadeInventario());
    }

    @Test
    void deveAgruparInventarioPorDescricaoMesmoComCodigosDiferentes() {
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(
                        produto("SKU-A", "Cadeira executiva", 1.0),
                        produto("SKU-B", "Cadeira executiva", 2.0)
                ),
                List.of(item("SKU-NF", "Cadeira executiva", 3.0))
        );

        assertEquals(1, resultados.size());
        assertEquals("SKU-A, SKU-B", resultados.get(0).getCodigo());
        assertEquals(3.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(3.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
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
        item.setIdentificadorDocumentoOrigem("documento-teste");
        return item;
    }
}
