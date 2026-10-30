package com.conciliador.conciliador.model;

import com.opencsv.bean.CsvBindByName;

public class ProdutoInventario {

    @CsvBindByName(column = "Codigo")
    private String codigo;

    @CsvBindByName(column = "Descricao")
    private String descricao;

    @CsvBindByName(column = "Marca")
    private String marca;

    @CsvBindByName(column = "Modelo")
    private String modelo;

    @CsvBindByName(column = "Estado")
    private String estado; // ex: novo, bom, ruim

    @CsvBindByName(column = "Imagem")
    private String imagem;

    // Getters e Setters
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getImagem() { return imagem; }
    public void setImagem(String imagem) { this.imagem = imagem; }

    @Override
    public String toString() {
        return "ProdutoInventario [Codigo=" + codigo + ", Descricao=" + descricao + ", Estado=" + estado + "]";
    }
}