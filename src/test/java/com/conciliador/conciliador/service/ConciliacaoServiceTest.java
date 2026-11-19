package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.exception.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConciliacaoServiceTest {

    private final ConciliacaoService conciliacaoService = new ConciliacaoService();

    @Test
    void deveSugerirItemProximoEPermitirConfirmacaoManual() {
        List<ProdutoInventario> inventario = List.of(
                produto("SKU-1", "Cadeira executiva ergonomica com apoio lombar", 3.0)
        );
        List<ItemNotaFiscal> itensNota = List.of(item("NF-1", "Cadeira", 3.0));

        ConciliacaoService.AnaliseConciliacao analise =
                conciliacaoService.analisarConciliacao(inventario, itensNota);

        assertEquals(1, analise.pendencias().size());
        assertEquals("Cadeira", analise.pendencias().get(0).descricao());
        assertEquals(1, analise.pendencias().get(0).sugestoes().size());
        assertEquals("SKU-1", analise.pendencias().get(0).sugestoes().get(0).codigo());

        ConciliacaoService.SugestaoInventario sugestao =
                analise.pendencias().get(0).sugestoes().get(0);
        List<ResultadoConciliacao> resultados = conciliacaoService.analisarConciliacao(
                inventario,
                itensNota,
                List.of(new ConciliacaoService.AssociacaoManual(
                        sugestao.indiceInventario(),
                        analise.pendencias().get(0).indiceNotaFiscal()
                ))
        ).resultados();

        assertEquals(1, resultados.size());
        assertEquals("CONCILIADO MANUALMENTE", resultados.get(0).getStatus());
        assertEquals(3.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(3.0, resultados.get(0).getQuantidadeNotaFiscal());
    }

    @Test
    void devePermitirRevisarSaldoFisicoRestanteAposUmaConciliacaoAutomatica() {
        ItemNotaFiscal conciliacaoAutomatica = item("NF-1", "Cadeira executiva ergonomica com apoio lombar", 3.0);
        conciliacaoAutomatica.setIdentificadorDocumentoOrigem("documento-1");
        ItemNotaFiscal sugestaoManual = item("NF-2", "Cadeira", 2.0);
        sugestaoManual.setIdentificadorDocumentoOrigem("documento-2");
        List<ProdutoInventario> inventario = List.of(
                produto("SKU-1", "Cadeira executiva ergonomica com apoio lombar", 5.0)
        );
        List<ItemNotaFiscal> itensNota = List.of(conciliacaoAutomatica, sugestaoManual);

        ConciliacaoService.AnaliseConciliacao analise =
                conciliacaoService.analisarConciliacao(inventario, itensNota);

        assertEquals(1, analise.pendencias().size());
        assertEquals(2.0, analise.pendencias().get(0).quantidade());
        assertEquals("Cadeira", analise.pendencias().get(0).descricao());
        assertEquals("SKU-1", analise.pendencias().get(0).sugestoes().get(0).codigo());

        List<ResultadoConciliacao> resultados = conciliacaoService.analisarConciliacao(
                inventario,
                itensNota,
                List.of(new ConciliacaoService.AssociacaoManual(
                        analise.pendencias().get(0).sugestoes().get(0).indiceInventario(),
                        analise.pendencias().get(0).indiceNotaFiscal()
                ))
        ).resultados();

        assertEquals(2, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals(3.0, resultados.get(0).getQuantidadeInventario());
        assertEquals("CONCILIADO MANUALMENTE", resultados.get(1).getStatus());
        assertEquals(2.0, resultados.get(1).getQuantidadeInventario());
        assertEquals(2.0, resultados.get(1).getQuantidadeNotaFiscal());
    }

    @Test
    void deveExibirDataDeEmissaoComoContextoNasPendenciasFiscais() {
        ItemNotaFiscal notaRecente = item("NF-RECENTE", "Cadeira", 1.0);
        notaRecente.setNumeroNota("NF-RECENTE");
        notaRecente.setIdentificadorDocumentoOrigem("documento-recente");
        notaRecente.setDataEmissao(LocalDate.of(2024, 1, 1));
        ItemNotaFiscal notaAntiga = item("NF-ANTIGA", "Cadeira", 1.0);
        notaAntiga.setNumeroNota("NF-ANTIGA");
        notaAntiga.setIdentificadorDocumentoOrigem("documento-antigo");
        notaAntiga.setDataEmissao(LocalDate.of(2020, 1, 1));

        ConciliacaoService.AnaliseConciliacao analiseRuim = conciliacaoService.analisarConciliacao(
                List.of(produtoComDetalhes("SKU-R", "Cadeira executiva com apoio lombar", "", "", "Ruim", 1.0)),
                List.of(notaRecente, notaAntiga)
        );
        assertEquals(2, analiseRuim.pendencias().size());
        assertEquals("NF-RECENTE", analiseRuim.pendencias().get(0).numeroNotaFiscal());
        assertEquals("01/01/2024", analiseRuim.pendencias().get(0).dataEmissaoFormatada());
        assertEquals("NF-ANTIGA", analiseRuim.pendencias().get(1).numeroNotaFiscal());
        assertEquals("01/01/2020", analiseRuim.pendencias().get(1).dataEmissaoFormatada());
    }

    @Test
    void devePermitirAssociarDuasNotasAoMesmoGrupoFisicoRespeitandoQuantidade() {
        List<ProdutoInventario> inventario = List.of(
                produto("TEL-1", "Telefone IP generico", 4.0)
        );
        List<ItemNotaFiscal> itensNota = List.of(
                item("NF-1", "Telefone IP Grandstream GXP1625", 2.0),
                item("NF-2", "Telefone IP Grandstream GXP1610", 2.0)
        );

        ConciliacaoService.AnaliseConciliacao analise =
                conciliacaoService.analisarConciliacao(inventario, itensNota);

        assertEquals(2, analise.pendencias().size());
        assertEquals(0, analise.pendencias().get(0).sugestoes().get(0).indiceInventario());
        assertEquals(0, analise.pendencias().get(1).sugestoes().get(0).indiceInventario());

        List<ResultadoConciliacao> resultados = conciliacaoService.analisarConciliacao(
                inventario,
                itensNota,
                List.of(
                        new ConciliacaoService.AssociacaoManual(0, 0),
                        new ConciliacaoService.AssociacaoManual(0, 1)
                )
        ).resultados();

        assertEquals(2, resultados.size());
        assertEquals("CONCILIADO MANUALMENTE", resultados.get(0).getStatus());
        assertEquals("CONCILIADO MANUALMENTE", resultados.get(1).getStatus());
        assertEquals(2.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(2.0, resultados.get(1).getQuantidadeInventario());
    }

    @Test
    void deveRejeitarAssociacoesManuaisQueExcedemSaldoFisico() {
        List<ProdutoInventario> inventario = List.of(produto("TEL-1", "Telefone IP generico", 4.0));
        List<ItemNotaFiscal> itensNota = List.of(
                item("NF-1", "Telefone IP Grandstream GXP1625", 3.0),
                item("NF-2", "Telefone IP Grandstream GXP1610", 3.0)
        );

        assertThrows(RequisicaoInvalidaException.class, () -> conciliacaoService.analisarConciliacao(
                inventario,
                itensNota,
                List.of(
                        new ConciliacaoService.AssociacaoManual(0, 0),
                        new ConciliacaoService.AssociacaoManual(0, 1)
                )
        ));
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
    void deveIdentificarComoFaltanteNoFisicoOExcedenteFiscalDoItemConciliado() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-1", "Cadeira executiva", 1.0),
                item("SKU-1", "Cadeira executiva", 3.0)
        );

        assertEquals(1, resultados.size());
        assertEquals(1.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(3.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("FALTANDO NO FÍSICO", resultados.get(0).getStatus());
        assertEquals("SKU-1", resultados.get(0).getCodigo());
    }

    @Test
    void deveSomarItensFisicosIguaisEAlocarParcialmenteAQuantidadeDaNota() {
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(
                        produto("SKU-1", "Cadeira executiva", 1.0),
                        produto("SKU-2", "Cadeira executiva", 1.0),
                        produto("SKU-3", "Cadeira executiva", 1.0)
                ),
                List.of(item("NF-1", "Cadeira executiva", 5.0))
        );

        assertEquals(1, resultados.size());
        assertEquals(3.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(5.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("FALTANDO NO FÍSICO", resultados.get(0).getStatus());
        assertEquals("SKU-1, SKU-2, SKU-3", resultados.get(0).getCodigo());
    }

    @Test
    void deveSepararCodigosFisicosConciliadosDosCodigosSemNotaCorrespondente() {
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(
                        produto("MESA-1", "Mesa X", 1.0),
                        produto("MESA-2", "Mesa X", 1.0),
                        produto("MESA-3", "Mesa X", 1.0),
                        produto("MESA-4", "Mesa X", 1.0)
                ),
                List.of(item("NF-MESA", "Mesa X", 3.0))
        );

        assertEquals(2, resultados.size());
        assertEquals(3.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(3.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("MESA-1, MESA-2, MESA-3", resultados.get(0).getCodigo());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals(1.0, resultados.get(1).getQuantidadeInventario());
        assertNull(resultados.get(1).getQuantidadeNotaFiscal());
        assertEquals("MESA-4", resultados.get(1).getCodigo());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(1).getStatus());
    }

    @Test
    void deveDividirQuantidadeFisicaEntreLinhasDeNotasDiferentes() {
        ItemNotaFiscal primeiraNota = item("NF-1", "Cadeira executiva", 2.0);
        primeiraNota.setIdentificadorDocumentoOrigem("documento-1");
        ItemNotaFiscal segundaNota = item("NF-2", "Cadeira executiva", 3.0);
        segundaNota.setIdentificadorDocumentoOrigem("documento-2");

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produto("SKU-1", "Cadeira executiva", 3.0)),
                List.of(primeiraNota, segundaNota)
        );

        assertEquals(2, resultados.size());
        assertEquals(2.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(2.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals(1.0, resultados.get(1).getQuantidadeInventario());
        assertEquals(3.0, resultados.get(1).getQuantidadeNotaFiscal());
        assertEquals("FALTANDO NO FÍSICO", resultados.get(1).getStatus());
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
    void naoDeveAssociarQuandoQuantidadeFiscalNaoFoiIdentificada() {
        ItemNotaFiscal itemSemQuantidade = item("SKU-1", "Cadeira executiva", 1.0);
        itemSemQuantidade.setQuantidade(null);

        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-1", "Cadeira executiva", 5.0),
                itemSemQuantidade
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertNull(resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals(5.0, resultados.get(0).getQuantidadeInventario());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
        assertNull(resultados.get(1).getQuantidadeNotaFiscal());
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
    void naoDeveAssociarProdutosQuandoAsDimensoesDivergemAcimaDaTolerancia() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa modelo 123 120x60 cm", 1.0),
                item("SKU-NF", "Mesa modelo 123 140x70 cm", 1.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void deveConciliarDescricaoSimilarComPequenaDiferencaDeMedidaDentroDaTolerancia() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 120x60cm MDF", 1.0),
                item("SKU-NF", "Mesa 121x60cm MDF", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarArmarioComMedidaDiferenteDentroDaTolerancia() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Armario Alto 2 Portas 121x80cm MDF", 1.0),
                item("SKU-NF", "ARMARIO ALTO 2 PORTAS 120X80CM MDF", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarDimensoesEquivalentesEmCentimetrosEMilimetrosMesmoComMdfAntesDaMedida() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Balcao de Recepcao 180x60cm MDF", 1.0),
                item("SKU-NF", "BALCAO DE RECEPCAO MDF 1800X600MM", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarDuasDimensoesComDescricaoFiscalEmTresDimensoes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Painel de TV 180x120cm MDF", 1.0),
                item("SKU-NF", "PAINEL DE TV 180X120X30CM MDF", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveTratarDimensoesInvertidasComoEquivalentes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 120x60cm MDF Preto", 1.0),
                item("SKU-NF", "Mesa 60x120cm MDF Preta", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarMedidasMetricasEquivalentesMesmoComUnidadesDiferentes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Rack de Parede 12U 57cm Preto", 1.0),
                item("SKU-NF", "RACK DE PAREDE 12U 570MM 19 POL PRETO", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void naoDeveConciliarMedidasMetricasDiferentesMesmoComUnidadesDiferentes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Rack de Parede 12U 57cm Preto", 1.0),
                item("SKU-NF", "RACK DE PAREDE 12U 600MM 19 POL PRETO", 1.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void deveDeixarDesvinculadosItensQuandoHaAtributosAusentesNoInventario() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 200x60cm", 1.0),
                item("SKU-NF", "Mesa de Atendimento 200x60cm Madeira Branca", 1.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void deveConciliarItensQuandoMaterialEstaDescritoPorSinonimos() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 120x60cm Madeira Cinza", 1.0),
                item("SKU-NF", "Mesa 120x60cm MDF Cinza", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarItensQuandoMaterialMetalicoEstaDescritoDeFormaDiferente() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 120x60cm Metal Branca", 1.0),
                item("SKU-NF", "Mesa 120x60cm Aco Branca", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarItensQuandoCorUsaSinonimoGrafiteECinza() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 120x60cm Aco Grafite", 1.0),
                item("SKU-NF", "Mesa 120x60cm Aco Cinza", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarItensQuandoApenasACorInformadaDiverge() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 120x60cm Aco Branca", 1.0),
                item("SKU-NF", "Mesa 120x60cm Aco Preta", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarItensQuandoApenasOMaterialInformadoDiverge() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 120x60cm MDF Cinza", 1.0),
                item("SKU-NF", "Mesa 120x60cm Aco Cinza", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarItensQuandoAcabamentoInformadoDiverge() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 120x60cm MDF Cinza", 1.0),
                item("SKU-NF", "Mesa 120x60cm Madeira Nogueira", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarItemDeCategoriaEDimensoesIguaisComMaterialECorDivergentes() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Arquivo 160x80cm MDF Branco", 1.0),
                item("SKU-NF", "Arquivo 160x80cm Aco Preto", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarCarrinhoMesmoQuandoACorFoiAlterada() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Carrinho de Ferramentas 7 Gavetas 70x45x85cm Cinza", 1.0),
                item("SKU-NF", "CARRINHO DE FERRAMENTAS 7 GAV 700X450X850MM VERMELHO", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarArmarioQuandoMaterialMetalicoEstaDescritoComoAco() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Armario Baixo 90x40cm Metal", 1.0),
                item("SKU-NF", "ARMARIO BAIXO CHAPA DE ACO 90X40CM", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarMesaMesmoQuandoMdfEstaDescritoComoMdp() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Mesa 160x80cm MDF", 1.0),
                item("SKU-NF", "MESA 160X80CM MDP 18MM", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveSugerirRevisaoManualParaArquivoDescritoComoFerroEAcoCarbono() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Arquivo 4 Gavetas Ferro", 1.0),
                item("SKU-NF", "ARQUIVO 4 GAVETAS ACO CARBONO", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("SKU-INV", resultados.get(0).getCodigo());
        assertEquals(1.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(1.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("REVISÃO MANUAL — MATERIAL", resultados.get(0).getStatus());
    }

    @Test
    void deveDeixarDesvinculadasDescricoesComEspecificacoesNumericasDiferentesSemCampoModelo() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU-INV", "Notebook modelo 123 8GB", 1.0),
                item("SKU-NF", "Notebook modelo 456 16GB", 1.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
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
    void naoDeveDescartarProdutoQueContemPisComoParteDePiso() {
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                Collections.emptyList(),
                List.of(item(null, "Rack de piso modelo RK691", 1.0))
        );

        assertEquals(1, resultados.size());
        assertEquals("Rack de piso modelo RK691", resultados.get(0).getDescricaoNotaFiscal());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(0).getStatus());
    }

    @Test
    void naoDeveAplicarFiltroDeCabecalhoOcrAItensEstruturadosDeXml() {
        ItemNotaFiscal item = item(null, "PIS para equipamentos eletrônicos", 1.0);
        item.setNomeArquivoOrigem("nota.xml");

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                Collections.emptyList(),
                List.of(item)
        );

        assertEquals(1, resultados.size());
        assertEquals("PIS para equipamentos eletrônicos", resultados.get(0).getDescricaoNotaFiscal());
    }

    @Test
    void deveUsarStatusSimplesParaQuantidadeNaoIdentificadaEmItemExclusivoDaNota() {
        ItemNotaFiscal itemSemQuantidade = item("SKU-NF", "Mouse sem fio Logitech modelo MX", 1.0);
        itemSemQuantidade.setQuantidade(null);

        ResultadoConciliacao resultado = conciliacaoService.conciliarInventarioComDocumentos(
                Collections.emptyList(),
                List.of(itemSemQuantidade)
        ).get(0);

        assertNull(resultado.getQuantidadeNotaFiscal());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultado.getStatus());
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
    void deveSepararSaldoFisicoDeItemFiscalComQuantidadeDesconhecida() {
        ItemNotaFiscal quantidadeConhecida = item("COD-A", "Cadeira executiva", 4.0);
        quantidadeConhecida.setIdentificadorDocumentoOrigem("documento-a");
        ItemNotaFiscal quantidadeDesconhecida = item("COD-B", "Cadeira executiva", 1.0);
        quantidadeDesconhecida.setQuantidade(null);
        quantidadeDesconhecida.setIdentificadorDocumentoOrigem("documento-b");

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produto("SKU", "Cadeira executiva", 10.0)),
                List.of(quantidadeConhecida, quantidadeDesconhecida)
        );

        assertEquals(3, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals(4.0, resultados.get(0).getQuantidadeInventario());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(1).getStatus());
        assertEquals(6.0, resultados.get(1).getQuantidadeInventario());
        assertNull(resultados.get(1).getQuantidadeNotaFiscal());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(2).getStatus());
        assertNull(resultados.get(2).getQuantidadeNotaFiscal());
    }

    @Test
    void deveSepararItemComQuantidadeFiscalZeroDoSaldoFisico() {
        List<ResultadoConciliacao> resultados = conciliar(
                produto("SKU", "Cadeira executiva", 2.0),
                item("SKU-NF", "Cadeira executiva", 0.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals(2.0, resultados.get(0).getQuantidadeInventario());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
        assertEquals(0.0, resultados.get(1).getQuantidadeNotaFiscal());
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

    @Test
    void deveSepararProdutosComDescricaoIgualEModelosDiferentes() {
        ItemNotaFiscal modelo3077 = item(
                "NF-3077",
                "Notebook Lenovo IdeaPad 3077 Ryzen 7 8GB SSD 512GB",
                4.0
        );
        modelo3077.setIdentificadorDocumentoOrigem("nf-3000");
        modelo3077.setNumeroNota("3000");
        ItemNotaFiscal modelo3014 = item(
                "NF-3014",
                "Notebook Lenovo IdeaPad 3014 i3 16GB SSD 512GB",
                3.0
        );
        modelo3014.setIdentificadorDocumentoOrigem("nf-3001");
        modelo3014.setNumeroNota("3001");

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(
                        produtoComDetalhes("0001", "Notebook", "Lenovo", "IdeaPad 3077", "Muito Bom", 2.0),
                        produtoComDetalhes("0013", "Notebook", "Lenovo", "IdeaPad 3077", "Novo", 2.0),
                        produtoComDetalhes("0011", "Notebook", "Lenovo", "IdeaPad 3014", "Ruim", 2.0),
                        produtoComDetalhes("0018", "Notebook", "Lenovo", "IdeaPad 3014", "Novo", 1.0)
                ),
                List.of(modelo3077, modelo3014)
        );

        assertEquals(2, resultados.size());
        assertEquals("Notebook Lenovo IdeaPad 3077", resultados.get(0).getDescricaoInventario());
        assertEquals(4.0, resultados.get(0).getQuantidadeInventario());
        assertEquals(4.0, resultados.get(0).getQuantidadeNotaFiscal());
        assertEquals("3000", resultados.get(0).getNumeroNotaFiscal());
        assertEquals("Muito Bom, Novo", resultados.get(0).getEstado());
        assertEquals("OK", resultados.get(0).getStatus());

        assertEquals("Notebook Lenovo IdeaPad 3014", resultados.get(1).getDescricaoInventario());
        assertEquals(3.0, resultados.get(1).getQuantidadeInventario());
        assertEquals(3.0, resultados.get(1).getQuantidadeNotaFiscal());
        assertEquals("3001", resultados.get(1).getNumeroNotaFiscal());
        assertEquals("Ruim, Novo", resultados.get(1).getEstado());
        assertEquals("OK", resultados.get(1).getStatus());
    }

    @Test
    void devePriorizarModeloEspecificoAntesDeDescricaoGenerica() {
        ProdutoInventario produtoGenerico = produto("SKU-GENERICO", "Projetor", 1.0);
        ProdutoInventario produtoEspecifico = produtoComDetalhes(
                "SKU-ESPECIFICO", "Projetor", "Epson", "PowerLite X14", "Bom", 1.0
        );
        ItemNotaFiscal projetor = item(
                "SKU-NF",
                "Projetor Epson PowerLite X14 WXGA",
                1.0
        );

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produtoGenerico, produtoEspecifico),
                List.of(projetor)
        );

        assertEquals(2, resultados.size());
        assertEquals("Projetor Epson PowerLite X14", resultados.get(0).getDescricaoInventario());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(1).getStatus());
    }

    @Test
    void deveConciliarProdutoComMarcaEModeloEmCaixaAlta() {
        List<ResultadoConciliacao> resultados = conciliar(
                produtoComDetalhes("0046", "DESKTOP", "DELL", "OPTIPLEX 5947", "Ruim", 1.0),
                item("DSK-00009", "Desktop Dell OptiPlex 5947 i5 4GB HD 1TB", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveUsarModeloDeTextoSemNumerosQuandoEleApareceNaNota() {
        List<ResultadoConciliacao> resultados = conciliar(
                produtoComDetalhes("SKU-MESA", "Mesa 120x60cm Aco Carvalho", "", "Smart", "Bom", 1.0),
                item("NF-MESA", "Mesa de Apoio Pandin Smart 120x60cm Aco Carvalho", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveConciliarModeloCompostoMesmoQuandoANotaSeparaAsPalavras() {
        List<ResultadoConciliacao> resultados = conciliar(
                produtoComDetalhes("SKU-DESKTOP", "Desktop", "HP", "ProDesk7023", "Bom", 1.0),
                item("NF-DESKTOP", "Desktop HP ProDesk 7023 i5 8GB SSD 256GB", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void deveReconhecerMarcaAbreviadaEModeloSeparadoNaDescricaoFiscal() {
        List<ResultadoConciliacao> resultados = conciliar(
                produtoComDetalhes("SKU-NOTEBOOK", "Notebook", "HP", "ProBook7046", "Bom", 1.0),
                item("NF-NOTEBOOK", "Notebook Hewlett-Packard ProBook 7046 Ryzen 5 16GB SSD 512GB", 1.0)
        );

        assertEquals(1, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
    }

    @Test
    void naoDeveConciliarModelosDiferentesMesmoComEspecificacoesParecidas() {
        ProdutoInventario latitude = produtoComDetalhes(
                "SKU-LATITUDE", "Notebook", "Dell", "Latitude 9205", "Bom", 1.0
        );
        ItemNotaFiscal vostro = item(
                "SKU-VOSTRO",
                "Notebook Dell Vostro 4490 i3 16GB SSD 512GB",
                1.0
        );

        List<ResultadoConciliacao> resultados = conciliar(latitude, vostro);

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void naoDeveConciliarCodigosDeModeloQueDiferemPorUmDigito() {
        ProdutoInventario arCondicionado = produtoComDetalhes(
                "SKU-AR", "Ar Condicionado 48000 BTUs", "LG", "WindFree53-752", "Bom", 1.0
        );
        ItemNotaFiscal itemFiscal = item(
                "SKU-NF",
                "Ar Condicionado Split Hi-Wall LG WindFree43-752 48000 BTUs Frio",
                1.0
        );

        List<ResultadoConciliacao> resultados = conciliar(arCondicionado, itemFiscal);

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void naoDeveConciliarModelosIguaisQuandoAsMarcasConhecidasDivergem() {
        ProdutoInventario produtoDell = produtoComDetalhes(
                "SKU-DELL", "Notebook", "Dell", "ThinkPad E 9113", "Bom", 1.0
        );
        ProdutoInventario outroProdutoLenovo = produtoComDetalhes(
                "SKU-LENOVO", "Notebook", "Lenovo", "ThinkPad E 4789", "Bom", 1.0
        );
        ItemNotaFiscal itemLenovo = item(
                "SKU-NF",
                "Notebook Lenovo ThinkPad E 9113 Ryzen 7 16GB SSD 1024GB",
                1.0
        );

        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(produtoDell, outroProdutoLenovo),
                List.of(itemLenovo)
        );

        assertEquals(3, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(1).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(2).getStatus());
    }

    @Test
    void naoDeveSugerirRevisaoQuandoModeloConfereMasMarcaFiscalDiverge() {
        List<ResultadoConciliacao> resultados = conciliar(
                produtoComDetalhes(
                        "SKU-SPRINGER", "Ar Condicionado 48000 BTUs", "Springer", "Dual24-199", "Bom", 1.0
                ),
                item("SKU-NF", "Ar Condicionado Midea Dual24-199 48000 BTUs", 1.0)
        );

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void deveDeixarDesvinculadosItensQuandoDescricaoGenericaNaoIdentificaOModeloFiscal() {
        ProdutoInventario produto = produtoComDetalhes(
                "SKU-GENERICO", "Telefone IP", "Grandstream", "", "Bom", 1.0
        );
        ItemNotaFiscal telefone = item(
                "SKU-NF",
                "Telefone IP Grandstream GRP2649",
                1.0
        );

        List<ResultadoConciliacao> resultados = conciliar(produto, telefone);

        assertEquals(2, resultados.size());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(0).getStatus());
        assertEquals("ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)", resultados.get(1).getStatus());
    }

    @Test
    void deveTolerarErroSimplesDeDigitacaoNaMarcaQuandoOModeloConfere() {
        ProdutoInventario dvr = produtoComDetalhes(
                "SKU-DVR", "DVR 32 canais", "Inteblras", "XVR 1493", "Bom", 1.0
        );
        ItemNotaFiscal dvrFiscal = item(
                "SKU-NF",
                "DVR Intelbras XVR 1493 32 canais",
                1.0
        );

        ResultadoConciliacao resultado = conciliar(dvr, dvrFiscal).get(0);

        assertEquals("OK", resultado.getStatus());
    }

    @Test
    void naoDeveTratarVariacaoDeDigitacaoDaMarcaComoFabricanteDiferente() {
        List<ResultadoConciliacao> resultados = conciliacaoService.conciliarInventarioComDocumentos(
                List.of(
                        produtoComDetalhes("SKU-1", "Switch", "Intelbras", "XVR 1493", "Bom", 1.0),
                        produtoComDetalhes("SKU-2", "Switch", "Inteblras", "XVR 1456", "Bom", 1.0)
                ),
                List.of(item("NF", "Switch Intelbras XVR 1493", 1.0))
        );

        assertEquals(2, resultados.size());
        assertEquals("OK", resultados.get(0).getStatus());
        assertEquals("SEM NF CORRESPONDENTE", resultados.get(1).getStatus());
    }

    private ProdutoInventario produtoComDetalhes(
            String codigo,
            String descricao,
            String marca,
            String modelo,
            String estado,
            double quantidade
    ) {
        ProdutoInventario produto = produto(codigo, descricao, quantidade);
        produto.setMarca(marca);
        produto.setModelo(modelo);
        produto.setEstado(estado);
        return produto;
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
