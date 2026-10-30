package com.conciliador.conciliador.service;

import org.junit.jupiter.api.Test;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MatchUtilsTest {

    @Test
    void deveNormalizarTextoCorretamente() {
        String texto = "  Mesa de Escritório 120x60cm  ";
        String normalizado = MatchUtils.normalizar(texto);

        assertTrue(normalizado.contains("mesa"));
        assertTrue(normalizado.contains("120x60"));
    }

    @Test
    void deveAplicarSinonimosCorretamente() {
        String texto = "Computador Portátil Core i5";
        String normalizado = MatchUtils.normalizar(texto);

        assertTrue(normalizado.contains("notebook"));
    }

    @Test
    void deveExtrairDimensoesDinamicamente() {
        String texto1 = "Mesa 120x60";
        String texto2 = "Mesa de centro 10x10cm";

        Set<String> dim1 = MatchUtils.extrairDimensoes(texto1);
        Set<String> dim2 = MatchUtils.extrairDimensoes(texto2);

        assertTrue(dim1.contains("120x60"));
        assertTrue(dim2.contains("10x10"));
    }
}