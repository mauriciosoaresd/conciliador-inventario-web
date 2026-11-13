package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.model.SinonimoDinamico;
import com.conciliador.conciliador.repository.SinonimoDinamicoRepository;
import com.conciliador.conciliador.service.MatchUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConciliacaoService {

    private static final double TOLERANCIA_DIMENSOES_CM = 5.0;
    private static final Pattern QUANTIDADE_PORTAS = Pattern.compile("\\b(\\d+)\\s*portas?\\b");
    private static final Pattern UNIDADES_RACK = Pattern.compile("\\b(\\d+)\\s*u\\b");
    private static final Pattern ESPECIFICACAO_COMPUTADOR = Pattern.compile(
            "\\b(?:i[3579]|ryzen\\s*\\d+|\\d+\\s*gb|ram|ddr\\d*|ssd|hdd)\\b.*$"
    );
    private static final Pattern NUMERO_MODELO = Pattern.compile("\\b\\d{2,}\\b");

    private final SinonimoDinamicoRepository sinonimoRepository;

    public ConciliacaoService(SinonimoDinamicoRepository sinonimoRepository) {
        this.sinonimoRepository = sinonimoRepository;
    }

    public List<ResultadoConciliacao> conciliarInventarioComDocumentos(
            List<ProdutoInventario> produtosInventario,
            List<ItemNotaFiscal> itensNotas) {

        // 1. Agrupa o inventário pela descrição, ignorando códigos como identidade do produto.
        Map<String, InventarioAgrupado> inventarioAgrupadoMap = new LinkedHashMap<>();
        int produtoSemDescricao = 0;

        for (ProdutoInventario prod : produtosInventario) {
            String descricao = MatchUtils.normalizar(prod.getDescricao());
            String chave = descricao.isEmpty() ? "SEM-DESCRICAO:" + produtoSemDescricao++ : descricao;
            inventarioAgrupadoMap.putIfAbsent(chave, new InventarioAgrupado(prod));
            inventarioAgrupadoMap.get(chave).adicionar(prod);
        }

        List<ItemNotaFiscal> itensNotaAgrupados = agruparItensNota(itensNotas);
        List<ResultadoConciliacao> resultados = new ArrayList<>();
        Set<Integer> indicesItensNotaConsumidos = new HashSet<>();

        // 2. Em cada documento, seleciona no máximo uma correspondência para cada grupo.
        for (InventarioAgrupado inv : inventarioAgrupadoMap.values()) {
            String descInvNorm = aplicarSinonimosDinamicos(
                    MatchUtils.normalizar(inv.getProdutoBase().getDescricao())
            );
            Map<String, CandidatoFiscal> melhorCandidatoPorDocumento = new LinkedHashMap<>();

            for (int i = 0; i < itensNotaAgrupados.size(); i++) {
                if (indicesItensNotaConsumidos.contains(i)) continue;
                ItemNotaFiscal item = itensNotaAgrupados.get(i);
                if (item.getDescricaoProduto() == null || item.getDescricaoProduto().isBlank()) continue;
                String descNotaNorm = aplicarSinonimosDinamicos(
                        MatchUtils.normalizar(item.getDescricaoProduto())
                );
                boolean computadores = ehComputador(descInvNorm) && ehComputador(descNotaNorm);
                String textoInvComparacao = computadores
                        ? removerEspecificacoesComputador(descInvNorm)
                        : descInvNorm;
                String textoNotaComparacao = computadores
                        ? removerEspecificacoesComputador(descNotaNorm)
                        : descNotaNorm;
                double escoreJaccard = MatchUtils.calcularSimilaridadeJaccardComStemming(
                        textoInvComparacao,
                        textoNotaComparacao
                );
                double escoreLevenshtein = MatchUtils.calcularSimilaridadeLevenshtein(
                        textoInvComparacao,
                        textoNotaComparacao
                );
                double escoreBase = (escoreJaccard * 0.7) + (escoreLevenshtein * 0.3);
                double escoreFinal = calcularSimilaridadeComPenalizacao(
                        textoInvComparacao,
                        textoNotaComparacao,
                        escoreBase
                );
                if (atributosCriticosDivergem(descInvNorm, descNotaNorm)) {
                    escoreFinal = 0;
                }
                String documento = item.getIdentificadorDocumentoOrigem();
                if (documento == null || documento.isBlank()) {
                    documento = "ITEM:" + i;
                }
                CandidatoFiscal atual = melhorCandidatoPorDocumento.get(documento);
                if (atual == null || escoreFinal > atual.escore()) {
                    melhorCandidatoPorDocumento.put(documento, new CandidatoFiscal(i, escoreFinal));
                }
            }

            List<ItemNotaFiscal> correspondencias = new ArrayList<>();
            for (CandidatoFiscal candidato : melhorCandidatoPorDocumento.values()) {
                if (candidato.escore() < 0.30) continue;
                ItemNotaFiscal item = itensNotaAgrupados.get(candidato.indice());
                indicesItensNotaConsumidos.add(candidato.indice());
                correspondencias.add(item);
                if (candidato.escore() >= 0.85
                        && !ehComputador(MatchUtils.normalizar(inv.getProdutoBase().getDescricao()))
                        && !dimensoesDivergem(
                                inv.getProdutoBase().getDescricao(),
                                item.getDescricaoProduto()
                        )) {
                    aprenderSinonimoSeNovo(inv.getProdutoBase().getDescricao(), item.getDescricaoProduto());
                }
            }

            adicionarAlocacoes(inv, correspondencias, resultados);
        }

        // 4. Adiciona ao relatório APENAS os itens reais da Nota Fiscal que NÃO foram consumidos,
        // exibindo traços nas colunas do inventário físico ausente.
        for (int i = 0; i < itensNotaAgrupados.size(); i++) {
            if (!indicesItensNotaConsumidos.contains(i)) {
                ItemNotaFiscal itemNaoMapeado = itensNotaAgrupados.get(i);
                String descNota = itemNaoMapeado.getDescricaoProduto();

                if (isDescricaoProdutoValida(descNota)) {
                    String statusItemNaoMapeado = itemNaoMapeado.getQuantidade() == null
                            ? "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA; ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)"
                            : "ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)";
                    resultados.add(new ResultadoConciliacao(
                            "-",
                            "-",
                            descNota,
                            "N/A",
                            null,
                            itemNaoMapeado.getQuantidade(),
                            itemNaoMapeado.getNumeroNota(),
                            itemNaoMapeado.getNomeArquivoOrigem(),
                            statusItemNaoMapeado
                    ));
                }
            }
        }

        return resultados;
    }

    private void adicionarAlocacoes(
            InventarioAgrupado inventario,
            List<ItemNotaFiscal> correspondencias,
            List<ResultadoConciliacao> resultados
    ) {
        double quantidadeFisicaRestante = inventario.getQuantidadeTotal();
        String descricaoInventario = inventario.getProdutoBase().getDescricao();
        List<ItemNotaFiscal> correspondenciasOrdenadas = new ArrayList<>(correspondencias);
        correspondenciasOrdenadas.sort(Comparator.comparing(
                item -> item.getQuantidade() == null
                        || dimensoesDivergem(descricaoInventario, item.getDescricaoProduto())
        ));

        for (ItemNotaFiscal item : correspondenciasOrdenadas) {
            boolean dimensoesDivergentes = dimensoesDivergem(
                    descricaoInventario,
                    item.getDescricaoProduto()
            );
            if (item.getQuantidade() == null || dimensoesDivergentes) {
                if (quantidadeFisicaRestante > 0) {
                    String status = dimensoesDivergentes
                            ? "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA"
                            : "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA";
                    if (item.getQuantidade() == null && dimensoesDivergentes) {
                        status += "; QUANTIDADE NÃO IDENTIFICADA";
                    }
                    resultados.add(criarLinha(
                            inventario,
                            item,
                            quantidadeFisicaRestante,
                            item.getQuantidade(),
                            status
                    ));
                    quantidadeFisicaRestante = 0;
                } else {
                    resultados.add(criarItemApenasNaNota(item, item.getQuantidade(), dimensoesDivergentes));
                }
                continue;
            }

            double quantidadeFiscalRestante = item.getQuantidade();
            double quantidadeAlocada = Math.min(quantidadeFisicaRestante, quantidadeFiscalRestante);
            if (quantidadeAlocada > 0) {
                resultados.add(criarLinha(
                        inventario,
                        item,
                        quantidadeAlocada,
                        quantidadeAlocada,
                        "OK"
                ));
                quantidadeFisicaRestante = subtrairQuantidade(quantidadeFisicaRestante, quantidadeAlocada);
                quantidadeFiscalRestante = subtrairQuantidade(quantidadeFiscalRestante, quantidadeAlocada);
            }
            if (quantidadeFiscalRestante > 0) {
                resultados.add(criarItemApenasNaNota(
                        item,
                        quantidadeFiscalRestante,
                        "ITEM APENAS NA NF (EXCEDENTE NÃO ALOCADO AO INVENTÁRIO)"
                ));
            } else if (item.getQuantidade() == 0) {
                resultados.add(criarItemApenasNaNota(
                        item,
                        0.0,
                        "REVISÃO MANUAL - QUANTIDADE FISCAL ZERO"
                ));
            }
        }

        if (quantidadeFisicaRestante > 0) {
            resultados.add(new ResultadoConciliacao(
                    inventario.getCodigos(),
                    inventario.getProdutoBase().getDescricao(),
                    "-",
                    inventario.getEstado(),
                    quantidadeFisicaRestante,
                    null,
                    null,
                    null,
                    "SEM NF CORRESPONDENTE"
            ));
        }
    }

    private boolean dimensoesDivergem(String descricaoInventario, String descricaoNota) {
        Set<String> dimensoesNota = MatchUtils.extrairDimensoes(descricaoNota);
        return !MatchUtils.extrairDimensoes(descricaoInventario).isEmpty()
                && !dimensoesNota.isEmpty()
                && !MatchUtils.dimensoesEquivalentes(
                        descricaoInventario,
                        descricaoNota,
                        TOLERANCIA_DIMENSOES_CM
                );
    }

    private boolean atributosCriticosDivergem(String descricaoInventario, String descricaoNota) {
        if (valoresRotuladosDivergem(descricaoInventario, descricaoNota, QUANTIDADE_PORTAS)
                || valoresRotuladosDivergem(descricaoInventario, descricaoNota, UNIDADES_RACK)) {
            return true;
        }

        if (descricaoInventario.contains("cadeira") && descricaoNota.contains("cadeira")) {
            Set<String> tiposInventario = tiposCadeira(descricaoInventario);
            Set<String> tiposNota = tiposCadeira(descricaoNota);
            if (!tiposInventario.isEmpty() && !tiposNota.isEmpty()
                    && Collections.disjoint(tiposInventario, tiposNota)) {
                return true;
            }
        }

        boolean inventarioComputador = ehComputador(descricaoInventario);
        boolean notaComputador = ehComputador(descricaoNota);
        if (inventarioComputador != notaComputador) return true;
        return inventarioComputador
                && !modelosComputadorCompativeis(descricaoInventario, descricaoNota);
    }

    private boolean valoresRotuladosDivergem(String descricao1, String descricao2, Pattern padrao) {
        String valor1 = capturarValor(descricao1, padrao);
        String valor2 = capturarValor(descricao2, padrao);
        return valor1 != null && valor2 != null && !valor1.equals(valor2);
    }

    private String capturarValor(String descricao, Pattern padrao) {
        Matcher matcher = padrao.matcher(descricao);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Set<String> tiposCadeira(String descricao) {
        Set<String> tipos = new LinkedHashSet<>();
        for (String tipo : List.of("presidente", "fixa", "giratoria", "empilhavel")) {
            if (descricao.contains(tipo)) tipos.add(tipo);
        }
        return tipos;
    }

    private boolean ehComputador(String descricao) {
        return descricao.matches(".*\\b(desktop|notebook|laptop|computador|pc)\\b.*");
    }

    private String removerEspecificacoesComputador(String descricao) {
        return descricao
                .replaceAll("\\b(?:i[3579]|ryzen\\s*\\d+)\\s+\\d+\\s*gb\\b", " ")
                .replaceAll("\\b(?:i[3579]|ryzen\\s*\\d+)\\b", " ")
                .replaceAll("\\b\\d+\\s*gb\\s*(?:ram|ddr\\d*)\\b", " ")
                .replaceAll("\\b(?:ram|ddr\\d*)\\s*\\d+\\s*gb\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean modelosComputadorCompativeis(String descricao1, String descricao2) {
        String identificador1 = extrairIdentificadorComputador(descricao1);
        String identificador2 = extrairIdentificadorComputador(descricao2);
        if (identificador1.isBlank() || identificador2.isBlank()) return false;

        String[] tokens1 = identificador1.split("\\s+");
        String[] tokens2 = identificador2.split("\\s+");
        if (!tokens1[0].equals(tokens2[0])) return false;

        Set<String> numeros1 = extrairNumerosModelo(identificador1);
        Set<String> numeros2 = extrairNumerosModelo(identificador2);
        if (!numeros1.isEmpty() && !numeros2.isEmpty() && Collections.disjoint(numeros1, numeros2)) {
            return false;
        }

        String modelo1 = identificador1.substring(tokens1[0].length()).trim();
        String modelo2 = identificador2.substring(tokens2[0].length()).trim();
        if (modelo1.isBlank() || modelo2.isBlank()) return false;
        return MatchUtils.calcularSimilaridadeJaccardComStemming(modelo1, modelo2) >= 0.5
                || MatchUtils.calcularSimilaridadeLevenshtein(modelo1, modelo2) >= 0.75;
    }

    private String extrairIdentificadorComputador(String descricao) {
        String semEspecificacoes = ESPECIFICACAO_COMPUTADOR.matcher(descricao).replaceFirst("").trim();
        return semEspecificacoes
                .replaceFirst("^(?:desktop|notebook|laptop|computador|pc)\\s+", "")
                .trim();
    }

    private Set<String> extrairNumerosModelo(String identificador) {
        Set<String> numeros = new HashSet<>();
        Matcher matcher = NUMERO_MODELO.matcher(identificador);
        while (matcher.find()) numeros.add(matcher.group());
        return numeros;
    }

    private double subtrairQuantidade(double quantidade, double quantidadeAlocada) {
        double restante = quantidade - quantidadeAlocada;
        return Math.abs(restante) < 0.000001 ? 0 : restante;
    }

    private ResultadoConciliacao criarLinha(
            InventarioAgrupado inventario,
            ItemNotaFiscal item,
            Double quantidadeInventario,
            Double quantidadeNotaFiscal,
            String status
    ) {
        return new ResultadoConciliacao(
                inventario.getCodigos(),
                inventario.getProdutoBase().getDescricao(),
                item.getDescricaoProduto(),
                inventario.getEstado(),
                quantidadeInventario,
                quantidadeNotaFiscal,
                item.getNumeroNota(),
                item.getNomeArquivoOrigem(),
                status
        );
    }

    private ResultadoConciliacao criarItemApenasNaNota(
            ItemNotaFiscal item,
            Double quantidadeNota,
            boolean dimensoesDivergentes
    ) {
        String status;
        if (dimensoesDivergentes && quantidadeNota == null) {
            status = "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA E QUANTIDADE NÃO IDENTIFICADA; "
                    + "ITEM APENAS NA NF (NÃO ALOCADO)";
        } else if (dimensoesDivergentes) {
            status = "REVISÃO MANUAL - AMBIGUIDADE DE MEDIDA; ITEM APENAS NA NF (NÃO ALOCADO)";
        } else if (quantidadeNota == null) {
            status = "REVISÃO MANUAL - QUANTIDADE NÃO IDENTIFICADA; "
                    + "ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)";
        } else {
            status = "ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)";
        }
        return criarItemApenasNaNota(item, quantidadeNota, status);
    }

    private ResultadoConciliacao criarItemApenasNaNota(
            ItemNotaFiscal item,
            Double quantidadeNota,
            String status
    ) {
        return new ResultadoConciliacao(
                "-",
                "-",
                item.getDescricaoProduto(),
                "N/A",
                null,
                quantidadeNota,
                item.getNumeroNota(),
                item.getNomeArquivoOrigem(),
                status
        );
    }

    private List<ItemNotaFiscal> agruparItensNota(List<ItemNotaFiscal> itensNota) {
        Map<String, ItemNotaFiscalAgrupado> grupos = new LinkedHashMap<>();
        int itemSemDocumento = 0;

        for (ItemNotaFiscal item : itensNota) {
            String descricao = MatchUtils.normalizar(item.getDescricaoProduto());
            String documento = item.getIdentificadorDocumentoOrigem();
            if (documento == null || documento.isBlank()) {
                String nomeArquivo = item.getNomeArquivoOrigem() == null ? "" : item.getNomeArquivoOrigem();
                String numeroNota = item.getNumeroNota() == null ? "" : item.getNumeroNota();
                documento = nomeArquivo.isBlank() && numeroNota.isBlank()
                        ? "ITEM-SEM-DOCUMENTO:" + itemSemDocumento++
                        : "DOCUMENTO:" + nomeArquivo + "|" + numeroNota;
            }
            String chave = documento + "|" + (descricao.isEmpty()
                    ? "SEM-DESCRICAO:" + itemSemDocumento++
                    : descricao);

            ItemNotaFiscalAgrupado grupo = grupos.get(chave);
            if (grupo == null) {
                grupos.put(chave, new ItemNotaFiscalAgrupado(item, documento));
            } else {
                grupo.adicionar(item);
            }
        }

        return grupos.values().stream().map(ItemNotaFiscalAgrupado::paraItemNotaFiscal).toList();
    }

    /**
     * Valida se o texto extraído é de facto um produto da NF e não lixo de cabeçalho/rodapé.
     */
    private boolean isDescricaoProdutoValida(String descricao) {
        if (descricao == null || descricao.trim().length() < 5) return false;
        String descUpper = descricao.toUpperCase().trim();

        if (descUpper.startsWith("COD") || descUpper.contains("DESCRICAO") || descUpper.contains("VL.UNIT")) {
            return false;
        }

        String[] termosIgnorados = {
                "EMITENTE", "DESTINATARIO", "CNPJ", "CPF", "TOTAL", "DANFE", "DOCUMENTO AUXILIAR",
                "SERIE", "NF-E", "NOTA FISCAL", "CHAVE DE ACESSO", "PROTOCOLO", "INSCRICAO",
                "VALOR", "ICMS", "PIS", "COFINS", "FRETE", "DESCONTO", "---------------", "—"
        };

        for (String termo : termosIgnorados) {
            if (descUpper.contains(termo)) {
                return false;
            }
        }
        return true;
    }

    private double calcularSimilaridadeComPenalizacao(String descInventario, String descNotaFiscal, double escoreBase) {
        if (descInventario == null || descNotaFiscal == null) return escoreBase;

        String inv = descInventario.toLowerCase();
        String nota = descNotaFiscal.toLowerCase();

        // Blindagem de Especificações Numéricas (BTUs, modelos, capacidades, potências)
        // Como o motor Jaccard + Levenshtein já pontua os nomes dos fabricantes pela similaridade textual,
        // esta validação numérica impede que modelos com especificações/códigos diferentes se cruzem.
        Pattern patternNum = Pattern.compile("\\b(\\d{2,5})\\b");
        Matcher matcherInv = patternNum.matcher(inv);
        Matcher matcherNota = patternNum.matcher(nota);

        Set<String> numerosInv = new HashSet<>();
        while (matcherInv.find()) numerosInv.add(matcherInv.group(1));

        Set<String> numerosNota = new HashSet<>();
        while (matcherNota.find()) numerosNota.add(matcherNota.group(1));

        if (!numerosInv.isEmpty() && !numerosNota.isEmpty()) {
            boolean temIntersecao = false;
            for (String num : numerosInv) {
                if (numerosNota.contains(num)) {
                    temIntersecao = true;
                    break;
                }
            }
            if (!temIntersecao) return 0.0;
        }

        return escoreBase;
    }

    private String aplicarSinonimosDinamicos(String texto) {
        if (texto == null) return "";
        try {
            List<SinonimoDinamico> sinonimos = sinonimoRepository.findAll();
            String textoProcessado = texto;
            for (SinonimoDinamico s : sinonimos) {
                if (textoProcessado.contains(s.getTermoOrigem())) {
                    textoProcessado = textoProcessado.replace(s.getTermoOrigem(), s.getTermoPadronizado());
                }
            }
            return textoProcessado;
        } catch (Exception e) {
            return texto;
        }
    }

    private void aprenderSinonimoSeNovo(String origem, String padrao) {
        try {
            String origNorm = MatchUtils.normalizar(origem);
            String padNorm = MatchUtils.normalizar(padrao);
            if (!origNorm.equals(padNorm) && sinonimoRepository.findByTermoOrigem(origNorm).isEmpty()) {
                sinonimoRepository.save(new SinonimoDinamico(origNorm, padNorm));
            }
        } catch (Exception ignored) {}
    }

    private static class InventarioAgrupado {
        private final ProdutoInventario produtoBase;
        private double quantidadeTotal;
        private final Set<String> codigos = new LinkedHashSet<>();
        private final Set<String> estados = new LinkedHashSet<>();

        public InventarioAgrupado(ProdutoInventario produtoBase) {
            this.produtoBase = produtoBase;
            this.quantidadeTotal = 0;
            adicionarCodigo(produtoBase.getCodigo());
            adicionarEstado(produtoBase.getEstado());
        }

        public void adicionar(ProdutoInventario produto) {
            quantidadeTotal += produto.getQuantidade();
            adicionarCodigo(produto.getCodigo());
            adicionarEstado(produto.getEstado());
        }

        private void adicionarCodigo(String codigo) {
            if (codigo != null && !codigo.isBlank()) codigos.add(codigo.trim());
        }

        private void adicionarEstado(String estado) {
            if (estado != null && !estado.isBlank()) estados.add(estado.trim());
        }

        public ProdutoInventario getProdutoBase() {
            return produtoBase;
        }

        public double getQuantidadeTotal() {
            return quantidadeTotal;
        }

        public String getCodigos() {
            return String.join(", ", codigos);
        }

        public String getEstado() {
            return String.join(", ", estados);
        }
    }

    private static class ItemNotaFiscalAgrupado {
        private final ItemNotaFiscal itemBase;
        private final String identificadorDocumento;
        private Double quantidadeTotal;
        private final Set<String> numerosNota = new LinkedHashSet<>();
        private final Set<String> nomesArquivo = new LinkedHashSet<>();

        private ItemNotaFiscalAgrupado(ItemNotaFiscal itemBase, String identificadorDocumento) {
            this.itemBase = itemBase;
            this.identificadorDocumento = identificadorDocumento;
            this.quantidadeTotal = itemBase.getQuantidade();
            adicionarRastreabilidade(itemBase);
        }

        private void adicionar(ItemNotaFiscal item) {
            if (quantidadeTotal == null || item.getQuantidade() == null) {
                quantidadeTotal = null;
            } else {
                quantidadeTotal += item.getQuantidade();
            }
            adicionarRastreabilidade(item);
        }

        private void adicionarRastreabilidade(ItemNotaFiscal item) {
            if (item.getNumeroNota() != null && !item.getNumeroNota().isBlank()) {
                numerosNota.add(item.getNumeroNota());
            }
            if (item.getNomeArquivoOrigem() != null && !item.getNomeArquivoOrigem().isBlank()) {
                nomesArquivo.add(item.getNomeArquivoOrigem());
            }
        }

        private ItemNotaFiscal paraItemNotaFiscal() {
            ItemNotaFiscal itemAgrupado = new ItemNotaFiscal();
            itemAgrupado.setCnpjEmitente(itemBase.getCnpjEmitente());
            itemAgrupado.setIdentificadorDocumentoOrigem(identificadorDocumento);
            itemAgrupado.setNumeroNota(String.join(", ", numerosNota));
            itemAgrupado.setCodigoProdutoFornecedor(itemBase.getCodigoProdutoFornecedor());
            itemAgrupado.setDescricaoProduto(itemBase.getDescricaoProduto());
            itemAgrupado.setQuantidade(quantidadeTotal);
            itemAgrupado.setUnidadeMedida(itemBase.getUnidadeMedida());
            itemAgrupado.setValorUnitario(itemBase.getValorUnitario());
            itemAgrupado.setNomeArquivoOrigem(String.join(", ", nomesArquivo));
            return itemAgrupado;
        }
    }

    private record CandidatoFiscal(int indice, double escore) {}
}