package com.conciliador.conciliador.model;

public class ResultadoConciliacao {
    private String codigo;
    private String descricaoInventario;
    private String descricaoNotaFiscal;
    private String estado;
    private int quantidadeInventario;
    private double quantidadeNotaFiscal;
    private String numeroNotaFiscal;
    private String nomeArquivoNota;
    private String status;

    public ResultadoConciliacao(String codigo, String descricaoInventario, String descricaoNotaFiscal, String estado, int quantidadeInventario, double quantidadeNotaFiscal, String getNumeroNota, String nomeArquivoNota, String status) {
        this.codigo = codigo;
        this.descricaoInventario = descricaoInventario;
        this.descricaoNotaFiscal = descricaoNotaFiscal;
        this.estado = estado;
        this.quantidadeInventario = quantidadeInventario;
        this.quantidadeNotaFiscal = quantidadeNotaFiscal;
        this.numeroNotaFiscal = getNumeroNota;
        this.nomeArquivoNota = nomeArquivoNota;
        this.status = status;
    }

    // Getters e Setters
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getDescricaoInventario() { return descricaoInventario; }
    public void setDescricaoInventario(String descricaoInventario) { this.descricaoInventario = descricaoInventario; }

    public String getDescricaoNotaFiscal() { return descricaoNotaFiscal; }
    public void setDescricaoNotaFiscal(String descricaoNotaFiscal) { this.descricaoNotaFiscal = descricaoNotaFiscal; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public int getQuantidadeInventario() { return quantidadeInventario; }
    public void setQuantidadeInventario(int quantidadeInventario) { this.quantidadeInventario = quantidadeInventario; }

    public double getQuantidadeNotaFiscal() { return quantidadeNotaFiscal; }
    public void setQuantidadeNotaFiscal(double quantidadeNotaFiscal) { this.quantidadeNotaFiscal = quantidadeNotaFiscal; }

    public String getNumeroNotaFiscal() { return numeroNotaFiscal; }
    public void setNumeroNotaFiscal(String numeroNotaFiscal) { this.numeroNotaFiscal = numeroNotaFiscal; }

    public String getNomeArquivoNota() { return nomeArquivoNota; }
    public void setNomeArquivoNota(String nomeArquivoNota) { this.nomeArquivoNota = nomeArquivoNota; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}