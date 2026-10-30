package com.conciliador.conciliador.model;

public class ResultadoConciliacao {
    private String codigo;
    private String descricao;
    private String estado;
    private int quantidadeInventario;
    private double quantidadeNotaFiscal;
    private String status; // "OK", "DIVERGENTE - SOBRA", "DIVERGENTE - FALTA"

    public ResultadoConciliacao(String codigo, String descricao, String estado, int quantidadeInventario, double quantidadeNotaFiscal, String status) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.estado = estado;
        this.quantidadeInventario = quantidadeInventario;
        this.quantidadeNotaFiscal = quantidadeNotaFiscal;
        this.status = status;
    }

    // Getters
    public String getCodigo() { return codigo; }
    public String getDescricao() { return descricao; }
    public String getEstado() { return estado; }
    public int getQuantidadeInventario() { return quantidadeInventario; }
    public double getQuantidadeNotaFiscal() { return quantidadeNotaFiscal; }
    public String getStatus() { return status; }
}