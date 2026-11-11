package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.model.SinonimoDinamico;
import com.conciliador.conciliador.repository.SinonimoDinamicoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

            // CAMADA 2: Motor Híbrido (Base Dinâmica + Stemming Jaccard + Levenshtein)
            if (itemMatch == null) {
                String descInvNorm = aplicarSinonimosDinamicos(MatchUtils.normalizar(prod.getDescricao()));

                double maiorEscore = 0.0;
                ItemNotaFiscal melhorCandidato = null;

                for (ItemNotaFiscal item : itensNotas) {
                    String descNotaNorm = aplicarSinonimosDinamicos(MatchUtils.normalizar(item.getDescricaoProduto()));

                    // Calcula similaridade combinada (Jaccard com Stemming + Levenshtein para erros de digitação)
                    double escoreJaccard = MatchUtils.calcularSimilaridadeJaccardComStemming(descInvNorm, descNotaNorm);
                    double escoreLevenshtein = MatchUtils.calcularSimilaridadeLevenshtein(descInvNorm, descNotaNorm);

                    // Média ponderada dos escores (70% estrutura/palavras, 30% tolerância a digitação)
                    double escoreFinal = (escoreJaccard * 0.7) + (escoreLevenshtein * 0.3);

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
                        // Aprendizado Automático: Salva sinônimo forte no banco se o match for excelente (> 85%)
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
                    itemMatch != null? itemMatch.getNumeroNota() : null,
                    itemMatch != null? itemMatch.getNomeArquivoOrigem() : null,
                    status
            ));
        }

        return resultados;
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
            return texto; // Fallback seguro caso o banco ainda esteja inicializando
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