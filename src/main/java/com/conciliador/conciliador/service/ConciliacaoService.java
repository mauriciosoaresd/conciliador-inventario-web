package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.model.SinonimoDinamico;
import com.conciliador.conciliador.repository.SinonimoDinamicoRepository;
import com.conciliador.conciliador.service.MatchUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ConciliacaoService {

    @Autowired
    private SinonimoDinamicoRepository sinonimoRepository;

    public List<ResultadoConciliacao> conciliarInventarioComDocumentos(
            List<ProdutoInventario> produtosInventario,
            List<ItemNotaFiscal> itensNotas) {

        // 1. Agrupa o inventário por Código, somando a quantidade real contada (já que é inserido de 1 em 1)
        // Mapeia o Código -> Objeto base do produto + Quantidade Total Contada + Estado
        Map<String, InventarioAgrupado> inventarioAgrupadoMap = new LinkedHashMap<>();

        for (ProdutoInventario prod : produtosInventario) {
            String codigo = prod.getCodigo() != null ? prod.getCodigo().trim() : "SEM-CODIGO";
            inventarioAgrupadoMap.putIfAbsent(codigo, new InventarioAgrupado(prod));
            inventarioAgrupadoMap.get(codigo).incrementarQuantidade();
        }

        List<ResultadoConciliacao> resultados = new ArrayList<>();
        Set<Integer> indicesItensNotaConsumidos = new HashSet<>();

        // 2. Processa cada produto único do inventário contra as notas fiscais
        for (InventarioAgrupado inv : inventarioAgrupadoMap.values()) {
            ItemNotaFiscal itemMatch = null;
            String status = "OK";
            double qtdNota = 0.0;
            double qtdInventario = inv.getQuantidadeTotal();

            // CAMADA 1: Match exato por Código/SKU
            for (int i = 0; i < itensNotas.size(); i++) {
                ItemNotaFiscal item = itensNotas.get(i);
                if (!indicesItensNotaConsumidos.contains(i) &&
                        item.getCodigoProdutoFornecedor() != null &&
                        item.getCodigoProdutoFornecedor().equalsIgnoreCase(inv.getProdutoBase().getCodigo())) {
                    itemMatch = item;
                    indicesItensNotaConsumidos.add(i);
                    break;
                }
            }

            // CAMADA 2: Motor Híbrido (caso não tenha match exato por código)
            if (itemMatch == null) {
                String descInvNorm = aplicarSinonimosDinamicos(MatchUtils.normalizar(inv.getProdutoBase().getDescricao()));

                double maiorEscore = 0.0;
                ItemNotaFiscal melhorCandidato = null;
                int melhorIndice = -1;

                for (int i = 0; i < itensNotas.size(); i++) {
                    if (indicesItensNotaConsumidos.contains(i)) continue;

                    ItemNotaFiscal item = itensNotas.get(i);
                    String descNotaNorm = aplicarSinonimosDinamicos(MatchUtils.normalizar(item.getDescricaoProduto()));

                    double escoreJaccard = MatchUtils.calcularSimilaridadeJaccardComStemming(descInvNorm, descNotaNorm);
                    double escoreLevenshtein = MatchUtils.calcularSimilaridadeLevenshtein(descInvNorm, descNotaNorm);
                    double escoreBase = (escoreJaccard * 0.7) + (escoreLevenshtein * 0.3);

                    // Validação de penalização técnica (BTUs/números) e marcas conflitantes
                    double escoreFinal = calcularSimilaridadeComPenalizacao(
                            inv.getProdutoBase().getDescricao(),
                            item.getDescricaoProduto(),
                            escoreBase
                    );

                    if (escoreFinal > maiorEscore) {
                        maiorEscore = escoreFinal;
                        melhorCandidato = item;
                        melhorIndice = i;
                    }
                }

                if (maiorEscore >= 0.30 && melhorCandidato != null) {
                    itemMatch = melhorCandidato;
                    indicesItensNotaConsumidos.add(melhorIndice);

                    // CAMADA 3: Ambiguidade de Medidas via Regex
                    Set<String> dimensoesInventario = MatchUtils.extrairDimensoes(inv.getProdutoBase().getDescricao());
                    Set<String> dimensoesNota = MatchUtils.extrairDimensoes(itemMatch.getDescricaoProduto());

                    if (!dimensoesInventario.isEmpty() && !dimensoesNota.isEmpty() && !dimensoesInventario.equals(dimensoesNota)) {
                        status = "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA";
                    } else if (maiorEscore >= 0.85) {
                        aprenderSinonimoSeNovo(inv.getProdutoBase().getDescricao(), itemMatch.getDescricaoProduto());
                    }
                }
            }

            // 3. Avaliação Quantitativa Final (Inventário Agrupado vs Quantidade da NF)
            if (itemMatch != null) {
                qtdNota = itemMatch.getQuantidade(); // Ex: 3 da Samsung
                if (!status.startsWith("REVISÃO")) {
                    if (qtdInventario > qtdNota) {
                        status = "DIVERGENTE - SOBRA";
                    } else if (qtdInventario < qtdNota) {
                        status = "DIVERGENTE - FALTA";
                    }
                }
            } else {
                status = "NÃO ENCONTRADO NO DOCUMENTO";
            }

            resultados.add(new ResultadoConciliacao(
                    inv.getProdutoBase().getCodigo(),
                    inv.getProdutoBase().getDescricao(),
                    itemMatch != null ? itemMatch.getDescricaoProduto() : null,
                    inv.getProdutoBase().getEstado(),
                    (int) qtdInventario, // Quantidade total contada (ex: 2)
                    qtdNota,             // Quantidade da Nota Fiscal (ex: 3)
                    itemMatch != null ? itemMatch.getNumeroNota() : null,
                    itemMatch != null ? itemMatch.getNomeArquivoOrigem() : null,
                    status
            ));
        }

        return resultados;
    }

    /**
     * Classe auxiliar interna para agrupar o inventário por item.
     */
    private static class InventarioAgrupado {
        private final ProdutoInventario produtoBase;
        private int quantidadeTotal;

        public InventarioAgrupado(ProdutoInventario produtoBase) {
            this.produtoBase = produtoBase;
            this.quantidadeTotal = 0;
        }

        public void incrementarQuantidade() {
            this.quantidadeTotal += 1;
        }

        public ProdutoInventario getProdutoBase() {
            return produtoBase;
        }

        public int getQuantidadeTotal() {
            return quantidadeTotal;
        }
    }

    private double calcularSimilaridadeComPenalizacao(String descInventario, String descNotaFiscal, double escoreBase) {
        if (descInventario == null || descNotaFiscal == null) return escoreBase;

        String inv = descInventario.toLowerCase();
        String nota = descNotaFiscal.toLowerCase();

        // Blindagem de Marcas
        String[] marcasConhecidas = {"daikin", "samsung", "lg", "intelbras", "cisco", "dell", "hp", "lenovo", "positivo", "tp-link", "d-link"};
        for (String marca : marcasConhecidas) {
            if (inv.contains(marca) != nota.contains(marca)) {
                return 0.0; // Zera o match se as marcas baterem cruzadas (ex: Daikin vs Samsung)
            }
        }

        // Blindagem de Especificações Numéricas (BTUs, potências, etc.)
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
}