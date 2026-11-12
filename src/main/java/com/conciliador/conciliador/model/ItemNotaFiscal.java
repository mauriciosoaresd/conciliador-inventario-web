package com.conciliador.conciliador.model;

public class ItemNotaFiscal {
    private String cnpjEmitente;
    private String numeroNota;
    private String codigoProdutoFornecedor;
    private String descricaoProduto;
    private Double quantidade;
    private String unidadeMedida;
    private double valorUnitario;
    private String nomeArquivoOrigem;

    // Getters e Setters
    public String getCnpjEmitente() { return cnpjEmitente; }
    public void setCnpjEmitente(String cnpjEmitente) { this.cnpjEmitente = cnpjEmitente; }

    public String getNumeroNota() { return numeroNota; }
    public void setNumeroNota(String numeroNota) { this.numeroNota = numeroNota; }

    public String getCodigoProdutoFornecedor() { return codigoProdutoFornecedor; }
    public void setCodigoProdutoFornecedor(String codigoProdutoFornecedor) { this.codigoProdutoFornecedor = codigoProdutoFornecedor; }

    public String getDescricaoProduto() { return descricaoProduto; }
    public void setDescricaoProduto(String descricaoProduto) { this.descricaoProduto = descricaoProduto; }

    public Double getQuantidade() { return quantidade; }
    public void setQuantidade(Double quantidade) { this.quantidade = quantidade; }

    public String getUnidadeMedida() { return unidadeMedida; }
    public void setUnidadeMedida(String unidadeMedida) { this.unidadeMedida = unidadeMedida; }

    public double getValorUnitario() { return valorUnitario; }
    public void setValorUnitario(double valorUnitario) { this.valorUnitario = valorUnitario; }

    public String getNomeArquivoOrigem() { return nomeArquivoOrigem; }
    public void setNomeArquivoOrigem(String nomeArquivoOrigem) { this.nomeArquivoOrigem = nomeArquivoOrigem; }

    @Override
    public String toString() {
        return "ItemNotaFiscal [NF=" + numeroNota + ", Descricao=" + descricaoProduto + ", Qtd=" + quantidade + "]";
    }
}