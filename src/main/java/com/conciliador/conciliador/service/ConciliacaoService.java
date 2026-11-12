package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.model.SinonimoDinamico;
import com.conciliador.conciliador.repository.SinonimoDinamicoRepository;
import com.conciliador.conciliador.service.MatchUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConciliacaoService {

    private final SinonimoDinamicoRepository sinonimoRepository;

    public ConciliacaoService(SinonimoDinamicoRepository sinonimoRepository) {
        this.sinonimoRepository = sinonimoRepository;
    }

    public List<ResultadoConciliacao> conciliarInventarioComDocumentos(
            List<ProdutoInventario> produtosInventario,
            List<ItemNotaFiscal> itensNotas) {

        // 1. Agrupa o inventário pela descrição, ignorando códigos como identidade do produto.
        Map<String, InventarioAgrupado> inventarioAgrupadoMap = new LinkedHashMap<>();
        int produtoSemDescricao = 0;

        for (ProdutoInventario prod : produtosInventario) {
            String descricao = MatchUtils.normalizar(prod.getDescricao());
            String chave = descricao.isEmpty() ? "SEM-DESCRICAO:" + produtoSemDescricao++ : descricao;
            inventarioAgrupadoMap.putIfAbsent(chave, new InventarioAgrupado(prod));
            inventarioAgrupadoMap.get(chave).adicionar(prod);
        }

        List<ItemNotaFiscal> itensNotaAgrupados = agruparItensNota(itensNotas);
        List<ResultadoConciliacao> resultados = new ArrayList<>();
        Set<Integer> indicesItensNotaConsumidos = new HashSet<>();

        // 2. Em cada documento, seleciona no máximo uma correspondência para cada grupo.
        for (InventarioAgrupado inv : inventarioAgrupadoMap.values()) {
            String descInvNorm = aplicarSinonimosDinamicos(
                    MatchUtils.normalizar(inv.getProdutoBase().getDescricao())
            );
            Map<String, CandidatoFiscal> melhorCandidatoPorDocumento = new LinkedHashMap<>();

            for (int i = 0; i < itensNotaAgrupados.size(); i++) {
                if (indicesItensNotaConsumidos.contains(i)) continue;
                ItemNotaFiscal item = itensNotaAgrupados.get(i);
                if (item.getDescricaoProduto() == null || item.getDescricaoProduto().isBlank()) continue;
                String descNotaNorm = aplicarSinonimosDinamicos(
                        MatchUtils.normalizar(item.getDescricaoProduto())
                );
                double escoreJaccard = MatchUtils.calcularSimilaridadeJaccardComStemming(descInvNorm, descNotaNorm);
                double escoreLevenshtein = MatchUtils.calcularSimilaridadeLevenshtein(descInvNorm, descNotaNorm);
                double escoreBase = (escoreJaccard * 0.7) + (escoreLevenshtein * 0.3);
                double escoreFinal = calcularSimilaridadeComPenalizacao(
                        inv.getProdutoBase().getDescricao(),
                        item.getDescricaoProduto(),
                        escoreBase
                );
                String documento = item.getIdentificadorDocumentoOrigem();
                if (documento == null || documento.isBlank()) {
                    documento = "ITEM:" + i;
                }
                CandidatoFiscal atual = melhorCandidatoPorDocumento.get(documento);
                if (atual == null || escoreFinal > atual.escore()) {
                    melhorCandidatoPorDocumento.put(documento, new CandidatoFiscal(i, escoreFinal));
                }
            }

            List<ItemNotaFiscal> correspondencias = new ArrayList<>();
            for (CandidatoFiscal candidato : melhorCandidatoPorDocumento.values()) {
                if (candidato.escore() < 0.30) continue;
                ItemNotaFiscal item = itensNotaAgrupados.get(candidato.indice());
                indicesItensNotaConsumidos.add(candidato.indice());
                correspondencias.add(item);
                if (candidato.escore() >= 0.85) {
                    aprenderSinonimoSeNovo(inv.getProdutoBase().getDescricao(), item.getDescricaoProduto());
                }
            }

            adicionarAlocacoes(inv, correspondencias, resultados);
        }

        // 4. Adiciona ao relatório APENAS os itens reais da Nota Fiscal que NÃO foram consumidos,
        // exibindo traços nas colunas do inventário físico ausente.
        for (int i = 0; i < itensNotaAgrupados.size(); i++) {
            if (!indicesItensNotaConsumidos.contains(i)) {
                ItemNotaFiscal itemNaoMapeado = itensNotaAgrupados.get(i);
                String descNota = itemNaoMapeado.getDescricaoProduto();

                if (isDescricaoProdutoValida(descNota)) {
                    String statusItemNaoMapeado = itemNaoMapeado.getQuantidade() == null
                            ? "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA; ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)"
                            : "ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)";
                    resultados.add(new ResultadoConciliacao(
                            "-",
                            "-",
                            descNota,
                            "N/A",
                            null,
                            itemNaoMapeado.getQuantidade(),
                            itemNaoMapeado.getNumeroNota(),
                            itemNaoMapeado.getNomeArquivoOrigem(),
                            statusItemNaoMapeado
                    ));
                }
            }
        }

        return resultados;
    }

    private void adicionarAlocacoes(
            InventarioAgrupado inventario,
            List<ItemNotaFiscal> correspondencias,
            List<ResultadoConciliacao> resultados
    ) {
        double quantidadeFisicaRestante = inventario.getQuantidadeTotal();
        Set<String> dimensoesInventario = MatchUtils.extrairDimensoes(
                inventario.getProdutoBase().getDescricao()
        );
        List<ItemNotaFiscal> correspondenciasOrdenadas = new ArrayList<>(correspondencias);
        correspondenciasOrdenadas.sort(Comparator.comparing(
                item -> item.getQuantidade() == null
                        || dimensoesDivergem(dimensoesInventario, item.getDescricaoProduto())
        ));

        for (ItemNotaFiscal item : correspondenciasOrdenadas) {
            boolean dimensoesDivergentes = dimensoesDivergem(
                    dimensoesInventario,
                    item.getDescricaoProduto()
            );
            if (item.getQuantidade() == null || dimensoesDivergentes) {
                if (quantidadeFisicaRestante > 0) {
                    String status = dimensoesDivergentes
                            ? "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA"
                            : "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA";
                    if (item.getQuantidade() == null && dimensoesDivergentes) {
                        status += "; QUANTIDADE NÃO IDENTIFICADA";
                    }
                    resultados.add(criarLinha(
                            inventario,
                            item,
                            quantidadeFisicaRestante,
                            item.getQuantidade(),
                            status
                    ));
                    quantidadeFisicaRestante = 0;
                } else {
                    resultados.add(criarItemApenasNaNota(item, item.getQuantidade(), dimensoesDivergentes));
                }
                continue;
            }

            double quantidadeFiscalRestante = item.getQuantidade();
            double quantidadeAlocada = Math.min(quantidadeFisicaRestante, quantidadeFiscalRestante);
            if (quantidadeAlocada > 0) {
                resultados.add(criarLinha(
                        inventario,
                        item,
                        quantidadeAlocada,
                        quantidadeAlocada,
                        "OK"
                ));
                quantidadeFisicaRestante = subtrairQuantidade(quantidadeFisicaRestante, quantidadeAlocada);
                quantidadeFiscalRestante = subtrairQuantidade(quantidadeFiscalRestante, quantidadeAlocada);
            }
            if (quantidadeFiscalRestante > 0) {
                resultados.add(criarItemApenasNaNota(
                        item,
                        quantidadeFiscalRestante,
                        "ITEM APENAS NA NF (EXCEDENTE NÃO ALOCADO AO INVENTÁRIO)"
                ));
            } else if (item.getQuantidade() == 0) {
                resultados.add(criarItemApenasNaNota(
                        item,
                        0.0,
                        "REVISÃO MANUAL - QUANTIDADE FISCAL ZERO"
                ));
            }
        }

        if (quantidadeFisicaRestante > 0) {
            resultados.add(new ResultadoConciliacao(
                    inventario.getCodigos(),
                    inventario.getProdutoBase().getDescricao(),
                    "-",
                    inventario.getProdutoBase().getEstado(),
                    quantidadeFisicaRestante,
                    null,
                    null,
                    null,
                    "SEM NF CORRESPONDENTE"
            ));
        }
    }

    private boolean dimensoesDivergem(Set<String> dimensoesInventario, String descricaoNota) {
        Set<String> dimensoesNota = MatchUtils.extrairDimensoes(descricaoNota);
        return !dimensoesInventario.isEmpty()
                && !dimensoesNota.isEmpty()
                && !dimensoesInventario.equals(dimensoesNota);
    }

    private double subtrairQuantidade(double quantidade, double quantidadeAlocada) {
        double restante = quantidade - quantidadeAlocada;
        return Math.abs(restante) < 0.000001 ? 0 : restante;
    }

    private ResultadoConciliacao criarLinha(
            InventarioAgrupado inventario,
            ItemNotaFiscal item,
            Double quantidadeInventario,
            Double quantidadeNotaFiscal,
            String status
    ) {
        return new ResultadoConciliacao(
                inventario.getCodigos(),
                inventario.getProdutoBase().getDescricao(),
                item.getDescricaoProduto(),
                inventario.getProdutoBase().getEstado(),
                quantidadeInventario,
                quantidadeNotaFiscal,
                item.getNumeroNota(),
                item.getNomeArquivoOrigem(),
                status
        );
    }

    private ResultadoConciliacao criarItemApenasNaNota(
            ItemNotaFiscal item,
            Double quantidadeNota,
            boolean dimensoesDivergentes
    ) {
        String status;
        if (dimensoesDivergentes && quantidadeNota == null) {
            status = "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA E QUANTIDADE NÃO IDENTIFICADA; "
                    + "ITEM APENAS NA NF (NÃO ALOCADO)";
        } else if (dimensoesDivergentes) {
            status = "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA; ITEM APENAS NA NF (NÃO ALOCADO)";
        } else if (quantidadeNota == null) {
            status = "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA; "
                    + "ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)";
        } else {
            status = "ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)";
        }
        return criarItemApenasNaNota(item, quantidadeNota, status);
    }

    private ResultadoConciliacao criarItemApenasNaNota(
            ItemNotaFiscal item,
            Double quantidadeNota,
            String status
    ) {
        return new ResultadoConciliacao(
                "-",
                "-",
                item.getDescricaoProduto(),
                "N/A",
                null,
                quantidadeNota,
                item.getNumeroNota(),
                item.getNomeArquivoOrigem(),
                status
        );
    }

    private List<ItemNotaFiscal> agruparItensNota(List<ItemNotaFiscal> itensNota) {
        Map<String, ItemNotaFiscalAgrupado> grupos = new LinkedHashMap<>();
        int itemSemDocumento = 0;

        for (ItemNotaFiscal item : itensNota) {
            String descricao = MatchUtils.normalizar(item.getDescricaoProduto());
            String documento = item.getIdentificadorDocumentoOrigem();
            if (documento == null || documento.isBlank()) {
                String nomeArquivo = item.getNomeArquivoOrigem() == null ? "" : item.getNomeArquivoOrigem();
                String numeroNota = item.getNumeroNota() == null ? "" : item.getNumeroNota();
                documento = nomeArquivo.isBlank() && numeroNota.isBlank()
                        ? "ITEM-SEM-DOCUMENTO:" + itemSemDocumento++
                        : "DOCUMENTO:" + nomeArquivo + "|" + numeroNota;
            }
            String chave = documento + "|" + (descricao.isEmpty()
                    ? "SEM-DESCRICAO:" + itemSemDocumento++
                    : descricao);

            ItemNotaFiscalAgrupado grupo = grupos.get(chave);
            if (grupo == null) {
                grupos.put(chave, new ItemNotaFiscalAgrupado(item, documento));
            } else {
                grupo.adicionar(item);
            }
        }

        return grupos.values().stream().map(ItemNotaFiscalAgrupado::paraItemNotaFiscal).toList();
    }

    /**
     * Valida se o texto extraído é de facto um produto da NF e não lixo de cabeçalho/rodapé.
     */
    private boolean isDescricaoProdutoValida(String descricao) {
        if (descricao == null || descricao.trim().length() < 5) return false;
        String descUpper = descricao.toUpperCase().trim();

        if (descUpper.startsWith("COD") || descUpper.contains("DESCRICAO") || descUpper.contains("VL.UNIT")) {
            return false;
        }

        String[] termosIgnorados = {
                "EMITENTE", "DESTINATARIO", "CNPJ", "CPF", "TOTAL", "DANFE", "DOCUMENTO AUXILIAR",
                "SERIE", "NF-E", "NOTA FISCAL", "CHAVE DE ACESSO", "PROTOCOLO", "INSCRICAO",
                "VALOR", "ICMS", "PIS", "COFINS", "FRETE", "DESCONTO", "---------------", "—"
        };

        for (String termo : termosIgnorados) {
            if (descUpper.contains(termo)) {
                return false;
            }
        }
        return true;
    }

    private double calcularSimilaridadeComPenalizacao(String descInventario, String descNotaFiscal, double escoreBase) {
        if (descInventario == null || descNotaFiscal == null) return escoreBase;

        String inv = descInventario.toLowerCase();
        String nota = descNotaFiscal.toLowerCase();

        // Blindagem de Especificações Numéricas (BTUs, modelos, capacidades, potências)
        // Como o motor Jaccard + Levenshtein já pontua os nomes dos fabricantes pela similaridade textual,
        // esta validação numérica impede que modelos com especificações/códigos diferentes se cruzem.
        Pattern patternNum = Pattern.compile("\\b(\\d{2,5})\\b");
        Matcher matcherInv = patternNum.matcher(inv);
        Matcher matcherNota = patternNum.matcher(nota);

        Set<String> numerosInv = new HashSet<>();
        while (matcherInv.find()) numerosInv.add(matcherInv.group(1));

        Set<String> numerosNota = new HashSet<>();
        while (matcherNota.find()) numerosNota.add(matcherNota.group(1));

        if (!numerosInv.isEmpty() && !numerosNota.isEmpty()) {
            boolean temIntersecao = false;
            for (String num : numerosInv) {
                if (numerosNota.contains(num)) {
                    temIntersecao = true;
                    break;
                }
            }
            if (!temIntersecao) return 0.0;
        }

        return escoreBase;
    }

    private String aplicarSinonimosDinamicos(String texto) {
        if (texto == null) return "";
        try {
            List<SinonimoDinamico> sinonimos = sinonimoRepository.findAll();
            String textoProcessado = texto;
            for (SinonimoDinamico s : sinonimos) {
                if (textoProcessado.contains(s.getTermoOrigem())) {
                    textoProcessado = textoProcessado.replace(s.getTermoOrigem(), s.getTermoPadronizado());
                }
            }
            return textoProcessado;
        } catch (Exception e) {
            return texto;
        }
    }

    private void aprenderSinonimoSeNovo(String origem, String padrao) {
        try {
            String origNorm = MatchUtils.normalizar(origem);
            String padNorm = MatchUtils.normalizar(padrao);
            if (!origNorm.equals(padNorm) && sinonimoRepository.findByTermoOrigem(origNorm).isEmpty()) {
                sinonimoRepository.save(new SinonimoDinamico(origNorm, padNorm));
            }
        } catch (Exception ignored) {}
    }

    private static class InventarioAgrupado {
        private final ProdutoInventario produtoBase;
        private double quantidadeTotal;
        private final Set<String> codigos = new LinkedHashSet<>();

        public InventarioAgrupado(ProdutoInventario produtoBase) {
            this.produtoBase = produtoBase;
            this.quantidadeTotal = 0;
            adicionarCodigo(produtoBase.getCodigo());
        }

        public void adicionar(ProdutoInventario produto) {
            quantidadeTotal += produto.getQuantidade();
            adicionarCodigo(produto.getCodigo());
        }

        private void adicionarCodigo(String codigo) {
            if (codigo != null && !codigo.isBlank()) codigos.add(codigo.trim());
        }

        public ProdutoInventario getProdutoBase() {
            return produtoBase;
        }

        public double getQuantidadeTotal() {
            return quantidadeTotal;
        }

        public String getCodigos() {
            return String.join(", ", codigos);
        }
    }

    private static class ItemNotaFiscalAgrupado {
        private final ItemNotaFiscal itemBase;
        private final String identificadorDocumento;
        private Double quantidadeTotal;
        private final Set<String> numerosNota = new LinkedHashSet<>();
        private final Set<String> nomesArquivo = new LinkedHashSet<>();

        private ItemNotaFiscalAgrupado(ItemNotaFiscal itemBase, String identificadorDocumento) {
            this.itemBase = itemBase;
            this.identificadorDocumento = identificadorDocumento;
            this.quantidadeTotal = itemBase.getQuantidade();
            adicionarRastreabilidade(itemBase);
        }

        private void adicionar(ItemNotaFiscal item) {
            if (quantidadeTotal == null || item.getQuantidade() == null) {
                quantidadeTotal = null;
            } else {
                quantidadeTotal += item.getQuantidade();
            }
            adicionarRastreabilidade(item);
        }

        private void adicionarRastreabilidade(ItemNotaFiscal item) {
            if (item.getNumeroNota() != null && !item.getNumeroNota().isBlank()) {
                numerosNota.add(item.getNumeroNota());
            }
            if (item.getNomeArquivoOrigem() != null && !item.getNomeArquivoOrigem().isBlank()) {
                nomesArquivo.add(item.getNomeArquivoOrigem());
            }
        }

        private ItemNotaFiscal paraItemNotaFiscal() {
            ItemNotaFiscal itemAgrupado = new ItemNotaFiscal();
            itemAgrupado.setCnpjEmitente(itemBase.getCnpjEmitente());
            itemAgrupado.setIdentificadorDocumentoOrigem(identificadorDocumento);
            itemAgrupado.setNumeroNota(String.join(", ", numerosNota));
            itemAgrupado.setCodigoProdutoFornecedor(itemBase.getCodigoProdutoFornecedor());
            itemAgrupado.setDescricaoProduto(itemBase.getDescricaoProduto());
            itemAgrupado.setQuantidade(quantidadeTotal);
            itemAgrupado.setUnidadeMedida(itemBase.getUnidadeMedida());
            itemAgrupado.setValorUnitario(itemBase.getValorUnitario());
            itemAgrupado.setNomeArquivoOrigem(String.join(", ", nomesArquivo));
            return itemAgrupado;
        }
    }

    private record CandidatoFiscal(int indice, double escore) {}
}