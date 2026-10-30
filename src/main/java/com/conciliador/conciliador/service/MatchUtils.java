package com.conciliador.conciliador.service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchUtils {

    // Dicionário de Sinônimos e Termos Comerciais Equivalentes
    private static final Map<String, String> SINONIMOS = new HashMap<>();

    static {
        SINONIMOS.put("computador portatil", "notebook");
        SINONIMOS.put("laptop", "notebook");
        SINONIMOS.put("aio", "all in one");
        SINONIMOS.put("computador all in one", "all in one");
        SINONIMOS.put("estante aco", "estante de ferro");
        SINONIMOS.put("estante metalica", "estante de ferro");
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
     * Extrai dinamicamente qualquer padrão de dimensão no formato numérico X numérico (ex: 10x10, 120x60, 0.9x1.2)
     */
    public static Set<String> extrairDimensoes(String texto) {
        Set<String> dimensoes = new HashSet<>();
        if (texto == null) return dimensoes;

        // Regex para capturar números seguidos de 'x' ou 'X' e outro número
        Pattern pattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*[xX]\\s*(\\d+(?:[.,]\\d+)?)");
        Matcher matcher = pattern.matcher(texto);

        while (matcher.find()) {
            // Padroniza a string encontrada removendo espaços internos e colocando em minúsculo
            dimensoes.add(matcher.group().replaceAll("\\s+", "").toLowerCase());
        }

        return dimensoes;
    }
}