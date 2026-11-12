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

@Service
public class ConciliacaoService {

    @Autowired
    private SinonimoDinamicoRepository sinonimoRepository;

    public List<ResultadoConciliacao> conciliarInventarioComDocumentos(
            List<ProdutoInventario> produtosInventario,
            List<ItemNotaFiscal> itensNotas) {

        // 1. Agrupa o inventário por Código, somando a quantidade real contada
        Map<String, InventarioAgrupado> inventarioAgrupadoMap = new LinkedHashMap<>();

        for (ProdutoInventario prod : produtosInventario) {
            String codigo = prod.getCodigo() != null ? prod.getCodigo().trim() : "SEM-CODIGO";
            inventarioAgrupadoMap.putIfAbsent(codigo, new InventarioAgrupado(prod));
            inventarioAgrupadoMap.get(codigo).adicionarQuantidade(prod.getQuantidade());
        }

        List<ResultadoConciliacao> resultados = new ArrayList<>();
        Set<Integer> indicesItensNotaConsumidos = new HashSet<>();

        // 2. Processa cada produto único do inventário contra as notas fiscais
        for (InventarioAgrupado inv : inventarioAgrupadoMap.values()) {
            ItemNotaFiscal itemMatch = null;
            String status = "OK";
            Double qtdNota = 0.0;
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

                    // Validação técnica (especificações numéricas/códigos) sem filtros frágeis de marca
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

            // 3. Avaliação Quantitativa Final
            if (itemMatch != null) {
                qtdNota = itemMatch.getQuantidade();
                if (qtdNota == null) {
                    status = status.startsWith("REVISÃO")
                            ? status + "; QUANTIDADE NÃO IDENTIFICADA"
                            : "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA";
                } else if (!status.startsWith("REVISÃO")) {
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
                    itemMatch != null ? itemMatch.getDescricaoProduto() : "-",
                    inv.getProdutoBase().getEstado(),
                    qtdInventario,
                    qtdNota,
                    itemMatch != null ? itemMatch.getNumeroNota() : null,
                    itemMatch != null ? itemMatch.getNomeArquivoOrigem() : null,
                    status
            ));
        }

        // 4. Adiciona ao relatório APENAS os itens reais da Nota Fiscal que NÃO foram consumidos,
        // exibindo traços nas colunas do inventário físico ausente.
        for (int i = 0; i < itensNotas.size(); i++) {
            if (!indicesItensNotaConsumidos.contains(i)) {
                ItemNotaFiscal itemNaoMapeado = itensNotas.get(i);
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
                            0,
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

        public InventarioAgrupado(ProdutoInventario produtoBase) {
            this.produtoBase = produtoBase;
            this.quantidadeTotal = 0;
        }

        public void adicionarQuantidade(double quantidade) {
            this.quantidadeTotal += quantidade;
        }

        public ProdutoInventario getProdutoBase() {
            return produtoBase;
        }

        public double getQuantidadeTotal() {
            return quantidadeTotal;
        }
    }
}