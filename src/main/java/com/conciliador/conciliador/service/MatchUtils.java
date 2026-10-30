package com.conciliador.conciliador.service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchUtils {

    // Dicionário Ampliado de Sinônimos e Termos Comerciais Equivalentes
    private static final Map<String, String> SINONIMOS = new HashMap<>();

    static {
        // Informática e Tecnologia
        SINONIMOS.put("computador portatil", "notebook");
        SINONIMOS.put("laptop", "notebook");
        SINONIMOS.put("nbk", "notebook");
        SINONIMOS.put("aio", "all in one");
        SINONIMOS.put("computador all in one", "all in one");
        SINONIMOS.put("cpu", "gabinete");
        SINONIMOS.put("monitor led", "monitor");
        SINONIMOS.put("monitor lcd", "monitor");
        SINONIMOS.put("tela", "monitor");

        // Mobiliário e Escritório
        SINONIMOS.put("estante aco", "estante de ferro");
        SINONIMOS.put("estante metalica", "estante de ferro");
        SINONIMOS.put("mesa esc", "mesa de escritorio");
        SINONIMOS.put("mesa trabalho", "mesa de escritorio");
        SINONIMOS.put("cadeira giratoria", "cadeira de escritorio");
        SINONIMOS.put("cadeira ergon", "cadeira de escritorio");
    }

    public static String normalizar(String texto) {
        if (texto == null) return "";

        // 1. Remove acentos
        String nfd = Normalizer.normalize(texto, Normalizer.Form.NFD);
        String semAcentos = nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 2. Converte para minúsculas e remove caracteres especiais (mantendo letras, números, ponto e o 'x' de dimensões)
        String limpo = semAcentos.toLowerCase()
                .replaceAll("[^a-z0-9x.]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 3. Aplica sinônimos
        for (Map.Entry<String, String> entry : SINONIMOS.entrySet()) {
            if (limpo.contains(entry.getKey())) {
                limpo = limpo.replace(entry.getKey(), entry.getValue());
            }
        }

        return limpo;
    }

    /**
     * Extrai dinamicamente qualquer padrão de dimensão no formato numérico X numérico (ex: 10x10, 120x60)
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
     * Calcula o escore de similaridade por Índice de Jaccard entre duas descrições normalizadas (0.0 a 1.0)
     */
    public static double calcularSimilaridadeJaccard(String desc1, String desc2) {
        if (desc1 == null || desc2 == null || desc1.isEmpty() || desc2.isEmpty()) {
            return 0.0;
        }

        Set<String> set1 = new HashSet<>();
        for (String palavra : desc1.split("\\s+")) {
            if (palavra.length() > 2) { // Ignora preposições muito curtas ("de", "da", "em")
                set1.add(palavra);
            }
        }

        Set<String> set2 = new HashSet<>();
        for (String palavra : desc2.split("\\s+")) {
            if (palavra.length() > 2) {
                set2.add(palavra);
            }
        }

        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        // Intersecção (palavras comuns)
        Set<String> interseccao = new HashSet<>(set1);
        interseccao.retainAll(set2);

        // União (todas as palavras únicas)
        Set<String> uniao = new HashSet<>(set1);
        uniao.addAll(set2);

        return (double) interseccao.size() / uniao.size();
    }
}