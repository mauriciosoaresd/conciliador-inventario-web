package com.conciliador.conciliador.service;

import org.junit.jupiter.api.Test;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MatchUtilsTest {

    @Test
    void deveNormalizarTextoCorretamente() {
        String texto = "Cadeira de Escritório - 120x60cm!";
        String resultado = MatchUtils.normalizar(texto);
        assertEquals("cadeira de escritorio 120x60cm", resultado);
    }

    @Test
    void deveAplicarStemmingCorretamente() {
        assertEquals("computador", MatchUtils.aplicarStemming("computadores"));
        assertEquals("cadeira", MatchUtils.aplicarStemming("cadeiras"));
    }

    @Test
    void deveExtrairDimensoesCorretamente() {
        Set<String> dimensoes = MatchUtils.extrairDimensoes("Mesa 120x60cm cor preta");
        assertTrue(dimensoes.contains("120x60"));
    }

    @Test
    void deveConsiderarEquivalentesDimensoesEmMetroECentimetro() {
        assertTrue(MatchUtils.dimensoesEquivalentes(
                "Gaveteiro 40x50cm",
                "Gaveteiro 0,40x0,50m",
                5.0
        ));
    }

    @Test
    void deveAceitarPequenaToleranciaDeMedicao() {
        assertTrue(MatchUtils.dimensoesEquivalentes("Mesa 120x60cm", "Mesa 124x64cm", 5.0));
        assertFalse(MatchUtils.dimensoesEquivalentes("Mesa 120x60cm", "Mesa 126x60cm", 5.0));
    }

    @Test
    void deveCalcularSimilaridadeJaccardComStemming() {
        double escore = MatchUtils.calcularSimilaridadeJaccardComStemming("cadeira de escritorio", "cadeiras escritorio");
        assertTrue(escore > 0.5, "O escore de Jaccard com stemming deve ser alto para termos parecidos.");
    }

    @Test
    void deveCalcularSimilaridadeLevenshteinParaErrosDeDigitacao() {
        double escore = MatchUtils.calcularSimilaridadeLevenshtein("notebook", "notbook");
        assertTrue(escore > 0.8, "O escore de Levenshtein deve tolerar erros leves de digitação.");
    }
}