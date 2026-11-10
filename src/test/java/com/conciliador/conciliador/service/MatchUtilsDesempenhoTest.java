package com.conciliador.conciliador.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MatchUtilsDesempenhoTest {

    @Test
    @DisplayName("Deve processar 5.000 cálculos de similaridade e stemming em menos de 2 segundos")
    void deveValidarDesempenhoAlgoritmosMatchUtils() {
        String textoBaseInventario = "Cadeira Executiva Giratoria com Bracos 120x60cm";
        String textoNotaFiscalErro = "Notbook Lenovo ThinkPad i7 32GB RAM";

        Instant inicio = Instant.now();

        // Simula 5.000 loops de processamento pesado combinando normalização, stemming, Jaccard e Levenshtein
        for (int i = 0; i < 5000; i++) {
            String norm1 = MatchUtils.normalizar(textoBaseInventario);
            String norm2 = MatchUtils.normalizar(textoNotaFiscalErro);

            MatchUtils.aplicarStemming("computadores");
            MatchUtils.aplicarStemming("cadeirinhas");

            MatchUtils.extrairDimensoes(textoBaseInventario);

            double jaccard = MatchUtils.calcularSimilaridadeJaccardComStemming(textoBaseInventario, textoNotaFiscalErro);
            double levenshtein = MatchUtils.calcularSimilaridadeLevenshtein(textoBaseInventario, textoNotaFiscalErro);

            // Validações leves para assegurar que os cálculos ocorrem sem falhas
            assertNotNull(norm1);
            assertNotNull(norm2);
            assertTrue(jaccard >= 0.0 && jaccard <= 1.0);
            assertTrue(levenshtein >= 0.0 && levenshtein <= 1.0);
        }

        Instant fim = Instant.now();
        long duracaoMillis = Duration.between(inicio, fim).toMillis();

        System.out.println("==================================================");
        System.out.println("⏱️  TESTE DE DESEMPENHO DOS ALGORITMOS CONCLUÍDO");
        System.out.println("Total de ciclos executados: 5.000 iterações");
        System.out.println("Tempo total de processamento: " + duracaoMillis + " ms");
        System.out.println("==================================================");

        assertTrue(duracaoMillis < 2000, "O processamento de 5.000 iterações dos algoritmos deve ocorrer em menos de 2 segundos.");
    }
}