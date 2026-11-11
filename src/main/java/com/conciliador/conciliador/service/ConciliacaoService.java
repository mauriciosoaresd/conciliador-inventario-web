package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.model.SinonimoDinamico;
import com.conciliador.conciliador.repository.SinonimoDinamicoRepository;
import com.conciliador.conciliador.service.MatchUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        Map<String, Long> contagemInventario = produtosInventario.stream()
                .collect(Collectors.groupingBy(
                        ProdutoInventario::getCodigo,
                        Collectors.counting()
                ));

        List<ResultadoConciliacao> resultados = new ArrayList<>();

        for (ProdutoInventario prod : produtosInventario) {
            ItemNotaFiscal itemMatch = null;
            String status = "OK";
            double qtdNota = 0.0;
            double qtdInventario = contagemInventario.getOrDefault(prod.getCodigo(), 1L);

            // CAMADA 1: Match exato por Código/SKU
            for (ItemNotaFiscal item : itensNotas) {
                if (item.getCodigoProdutoFornecedor() != null &&
                        item.getCodigoProdutoFornecedor().equalsIgnoreCase(prod.getCodigo())) {
                    itemMatch = item;
                    break;
                }
            }

            // CAMADA 2: Motor Híbrido (Base Dinâmica + Stemming Jaccard + Levenshtein + Penalização Técnica)
            if (itemMatch == null) {
                String descInvNorm = aplicarSinonimosDinamicos(MatchUtils.normalizar(prod.getDescricao()));

                double maiorEscore = 0.0;
                ItemNotaFiscal melhorCandidato = null;

                for (ItemNotaFiscal item : itensNotas) {
                    String descNotaNorm = aplicarSinonimosDinamicos(MatchUtils.normalizar(item.getDescricaoProduto()));

                    // Calculo de similaridade combinada (Jaccard + Levenshtein)
                    double escoreJaccard = MatchUtils.calcularSimilaridadeJaccardComStemming(descInvNorm, descNotaNorm);
                    double escoreLevenshtein = MatchUtils.calcularSimilaridadeLevenshtein(descInvNorm, descNotaNorm);

                    double escoreBase = (escoreJaccard * 0.7) + (escoreLevenshtein * 0.3);

                    // Aplica penalização automática para divergências de especificações numéricas (ex: 18000 vs 48000 BTUs)
                    double escoreFinal = calcularSimilaridadeComPenalizacao(
                            prod.getDescricao(),
                            item.getDescricaoProduto(),
                            escoreBase
                    );

                    if (escoreFinal > maiorEscore) {
                        maiorEscore = escoreFinal;
                        melhorCandidato = item;
                    }
                }

                // Limiar de segurança de 30% para aceitação do match
                if (maiorEscore >= 0.30 && melhorCandidato != null) {
                    itemMatch = melhorCandidato;

                    // CAMADA 3: Verificação de Ambiguidade de Medidas via Regex
                    Set<String> dimensoesInventario = MatchUtils.extrairDimensoes(prod.getDescricao());
                    Set<String> dimensoesNota = MatchUtils.extrairDimensoes(itemMatch.getDescricaoProduto());

                    if (!dimensoesInventario.isEmpty() && !dimensoesNota.isEmpty() && !dimensoesInventario.equals(dimensoesNota)) {
                        status = "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA (Inv: " + dimensoesInventario + " vs NF: " + dimensoesNota + ")";
                    } else {
                        // Aprendizado Automático: Salva sinônimo no banco se o match for excelente (>= 85%)
                        if (maiorEscore >= 0.85) {
                            aprenderSinonimoSeNovo(prod.getDescricao(), itemMatch.getDescricaoProduto());
                        }
                    }
                }
            }

            // Definição de Status Quantitativo
            if (itemMatch != null) {
                qtdNota = itemMatch.getQuantidade();
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
                    prod.getCodigo(),
                    prod.getDescricao(),
                    itemMatch != null ? itemMatch.getDescricaoProduto() : null,
                    prod.getEstado(),
                    (int) qtdInventario,
                    qtdNota,
                    itemMatch != null ? itemMatch.getNumeroNota() : null,
                    itemMatch != null ? itemMatch.getNomeArquivoOrigem() : null,
                    status
            ));
        }

        return resultados;
    }

    /**
     * Penaliza drasticamente o escore de similaridade caso ambos os produtos possuam
     * especificações numéricas (ex: BTUs, potências, voltagens) que divirjam.
     */
    private double calcularSimilaridadeComPenalizacao(String descInventario, String descNotaFiscal, double escoreBase) {
        if (descInventario == null || descNotaFiscal == null) return escoreBase;

        String inv = descInventario.toLowerCase();
        String nota = descNotaFiscal.toLowerCase();

        // Extração de números de 2 a 5 dígitos (ex: 18000, 48000, 110, 220)
        Pattern patternNum = Pattern.compile("\\b(\\d{2,5})\\b");

        Matcher matcherInv = patternNum.matcher(inv);
        Matcher matcherNota = patternNum.matcher(nota);

        Set<String> numerosInv = new HashSet<>();
        while (matcherInv.find()) {
            numerosInv.add(matcherInv.group(1));
        }

        Set<String> numerosNota = new HashSet<>();
        while (matcherNota.find()) {
            numerosNota.add(matcherNota.group(1));
        }

        // Se ambos contêm números de especificação técnica, mas nenhum deles é coincidente, zera o escore
        if (!numerosInv.isEmpty() && !numerosNota.isEmpty()) {
            boolean temIntersecao = false;
            for (String num : numerosInv) {
                if (numerosNota.contains(num)) {
                    temIntersecao = true;
                    break;
                }
            }
            if (!temIntersecao) {
                return 0.0;
            }
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