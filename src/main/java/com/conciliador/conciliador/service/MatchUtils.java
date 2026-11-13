package com.conciliador.conciliador.service;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchUtils {

    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance();

    public static String normalizar(String texto) {
        if (texto == null) return "";

        String nfd = Normalizer.normalize(texto, Normalizer.Form.NFD);
        String semAcentos = nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return semAcentos.toLowerCase()
                .replaceAll("[^a-z0-9x.]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Stemmer simples para português (remove plurais e radicais básicos)
     */
    public static String aplicarStemming(String palavra) {
        if (palavra == null || palavra.length() <= 3) return palavra;

        String p = palavra;
        if (p.endsWith("es") && p.length() > 4) {
            p = p.substring(0, p.length() - 2); // Ex: computadores -> computador
        } else if (p.endsWith("s") && !p.endsWith("is") && p.length() > 3) {
            p = p.substring(0, p.length() - 1); // Ex: cadeiras -> cadeira
        } else if (p.endsWith("inh") && p.length() > 4) {
            p = p.substring(0, p.length() - 3); // Ex: cadeirinha -> cadeir
        }
        return p;
    }

    /**
     * Extrai dimensões no formato 10x10, 120x60
     */
    public static Set<String> extrairDimensoes(String texto) {
        Set<String> dimensoes = new HashSet<>();
        if (texto == null) return dimensoes;

        Pattern pattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*[xX]\\s*(\\d+(?:[.,]\\d+)?)");
        Matcher matcher = pattern.matcher(texto);

        while (matcher.find()) {
            dimensoes.add(matcher.group().replaceAll("\\s+", "").toLowerCase());
        }
        return dimensoes;
    }

    /**
     * Jaccard Combinado com Stemming
     */
    public static double calcularSimilaridadeJaccardComStemming(String desc1, String desc2) {
        if (desc1 == null || desc2 == null || desc1.isEmpty() || desc2.isEmpty()) return 0.0;

        Set<String> set1 = extrairTokensComStemming(desc1);
        Set<String> set2 = extrairTokensComStemming(desc2);

        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> interseccao = new HashSet<>(set1);
        interseccao.retainAll(set2);

        Set<String> uniao = new HashSet<>(set1);
        uniao.addAll(set2);

        return (double) interseccao.size() / uniao.size();
    }

    /**
     * Distância de Levenshtein normalizada (0.0 a 1.0) para tolerar erros leves de digitação
     */
    public static double calcularSimilaridadeLevenshtein(String str1, String str2) {
        if (str1 == null || str2 == null) return 0.0;
        if (str1.equalsIgnoreCase(str2)) return 1.0;

        int distancia = LEVENSHTEIN.apply(str1, str2);
        int maxLen = Math.max(str1.length(), str2.length());

        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) distancia / maxLen);
    }

    private static Set<String> extrairTokensComStemming(String descricao) {
        Set<String> tokens = new HashSet<>();
        for (String p : descricao.split("\\s+")) {
            if (p.length() > 2) {
                tokens.add(aplicarStemming(p));
            }
        }
        return tokens;
    }
}