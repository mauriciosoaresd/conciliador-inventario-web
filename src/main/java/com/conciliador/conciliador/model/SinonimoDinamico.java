package com.conciliador.conciliador.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sinonimos_dinamicos")
public class SinonimoDinamico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String termoOrigem; // Ex: "notbook" ou "nbk"

    @Column(nullable = false)
    private String termoPadronizado; // Ex: "notebook"

    private LocalDateTime dataCriacao;

    public SinonimoDinamico() {
        this.dataCriacao = LocalDateTime.now();
    }

    public SinonimoDinamico(String termoOrigem, String termoPadronizado) {
        this.termoOrigem = termoOrigem;
        this.termoPadronizado = termoPadronizado;
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTermoOrigem() { return termoOrigem; }
    public void setTermoOrigem(String termoOrigem) { this.termoOrigem = termoOrigem; }
    public String getTermoPadronizado() { return termoPadronizado; }
    public void setTermoPadronizado(String termoPadronizado) { this.termoPadronizado = termoPadronizado; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}