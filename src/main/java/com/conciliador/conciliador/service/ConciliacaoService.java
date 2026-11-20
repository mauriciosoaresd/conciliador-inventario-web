package com.conciliador.conciliador.service;

import com.conciliador.conciliador.model.ItemNotaFiscal;
import com.conciliador.conciliador.model.ProdutoInventario;
import com.conciliador.conciliador.model.ResultadoConciliacao;
import com.conciliador.conciliador.exception.RequisicaoInvalidaException;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConciliacaoService {

    private static final String STATUS_SEM_NF = "SEM NF CORRESPONDENTE";
    private static final String STATUS_ITEM_APENAS_NA_NF = "ITEM APENAS NA NF (NÃO ENCONTRADO NO FÍSICO)";
    private static final String STATUS_FALTANDO_NO_FISICO = "FALTANDO NO FÍSICO";
    private static final String STATUS_REVISAO_MATERIAL = "REVISÃO MANUAL — MATERIAL";
    private static final DateTimeFormatter FORMATO_DATA_EMISSAO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<String> TERMOS_CABECALHO_OCR = Set.of(
            "emitente", "destinatario", "cnpj", "cpf", "total", "danfe", "serie", "nf-e",
            "nota", "fiscal", "chave", "acesso", "protocolo", "inscricao", "valor", "icms",
            "pis", "cofins", "frete", "desconto"
    );
    private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();
    private static final Pattern PADRAO_DIMENSOES = Pattern.compile(
            "\\d+(?:[.,]\\d+)?(?:\\s*[xX]\\s*\\d+(?:[.,]\\d+)?){1,2}"
    );
    private static final Pattern PADRAO_MEDIDA_METRICA = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(mm|cm|m)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PADRAO_UNIDADE = Pattern.compile("^\\s*(mm|cm|m)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ATRIBUTOS_MATERIAL = Set.of(
            "mdf", "mdp", "madeira", "aco", "metal"
    );
    private static final Set<String> ATRIBUTOS_COR = Set.of(
            "preto", "preta", "branco", "branca", "cinza", "grafite",
            "vermelho", "vermelha", "amarelo", "amarela"
    );
    private static final Set<String> TERMOS_SEM_PESO_PARA_SUGESTAO = Set.of(
            "a", "ao", "aos", "as", "com", "da", "das", "de", "do", "dos",
            "e", "em", "na", "nas", "no", "nos", "o", "os", "para", "por",
            "un", "pol", "mm", "cm", "m", "ferro", "carbono", "aco", "metal",
            "mdf", "mdp", "madeira", "preto", "preta", "branco", "branca",
            "cinza", "grafite", "vermelho", "vermelha", "amarelo", "amarela"
    );
    @Value("${conciliador.conciliacao.tolerancia-medida-cm:2}")
    private double toleranciaMedidaCm = 2;
    @Value("${conciliador.conciliacao.limite-similaridade-confirmada:0.35}")
    private double limiteSimilaridadeConfirmada = 0.35;
    @Value("${conciliador.conciliacao.limite-similaridade-sugestao:0.10}")
    private double limiteSimilaridadeSugestao = 0.10;

    public List<ResultadoConciliacao> conciliarInventarioComDocumentos(
            List<ProdutoInventario> produtosInventario,
            List<ItemNotaFiscal> itensNotas) {
        return analisarConciliacao(produtosInventario, itensNotas, List.of()).resultados();
    }

    public AnaliseConciliacao analisarConciliacao(
            List<ProdutoInventario> produtosInventario,
            List<ItemNotaFiscal> itensNotas
    ) {
        return analisarConciliacao(produtosInventario, itensNotas, List.of());
    }

    public AnaliseConciliacao analisarConciliacao(
            List<ProdutoInventario> produtosInventario,
            List<ItemNotaFiscal> itensNotas,
            List<AssociacaoManual> associacoesManuais
    ) {

        // 1. Agrupa o inventário pela descrição, ignorando códigos como identidade do produto.
        Map<String, InventarioAgrupado> inventarioAgrupadoMap = new LinkedHashMap<>();
        int produtoSemDescricao = 0;

        for (ProdutoInventario prod : produtosInventario) {
            String descricao = MatchUtils.normalizar(descricaoCompleta(prod));
            String chave = descricao.isEmpty() ? "SEM-DESCRICAO:" + produtoSemDescricao++ : descricao;
            inventarioAgrupadoMap.putIfAbsent(chave, new InventarioAgrupado(prod));
            inventarioAgrupadoMap.get(chave).adicionar(prod);
        }

        List<String> marcasInventario = produtosInventario.stream()
                .map(ProdutoInventario::getMarca)
                .filter(marca -> marca != null && !marca.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<ItemNotaFiscal> itensNotaAgrupados = agruparItensNota(itensNotas);
        List<ResultadoConciliacao> resultados = new ArrayList<>();
        List<InventarioAgrupado> gruposInventario = inventarioAgrupadoMap.values().stream()
                .sorted(Comparator
                        .comparing((InventarioAgrupado grupo) -> temModelo(grupo.getProdutoBase()))
                        .thenComparing(grupo -> temMarca(grupo.getProdutoBase()))
                        .thenComparingInt(grupo -> MatchUtils.normalizar(
                                descricaoCompleta(grupo.getProdutoBase())
                        ).length())
                        .reversed())
                .toList();
        Map<Integer, List<ItemComStatus>> correspondenciasPorGrupo = new HashMap<>();
        Set<Integer> indicesItensNotaConsumidos = new HashSet<>();
        List<CandidatoAssociacao> candidatos = new ArrayList<>();

        for (int indiceGrupo = 0; indiceGrupo < gruposInventario.size(); indiceGrupo++) {
            InventarioAgrupado inventario = gruposInventario.get(indiceGrupo);
            String modelo = inventario.getProdutoBase().getModelo();
            String descricaoInventario = descricaoCompleta(inventario.getProdutoBase());
            String descricaoInventarioNormalizada = normalizarDescricaoComModelo(descricaoInventario, modelo);

            for (int indiceItem = 0; indiceItem < itensNotaAgrupados.size(); indiceItem++) {
                ItemNotaFiscal item = itensNotaAgrupados.get(indiceItem);
                if (item.getDescricaoProduto() == null || item.getDescricaoProduto().isBlank()) continue;

                AvaliacaoIdentidade identidade = avaliarIdentidade(
                        inventario.getProdutoBase(),
                        item,
                        marcasInventario
                );
                if (!identidade.compativel()) {
                    continue;
                }

                String descricaoNotaNormalizada = normalizarDescricaoComModelo(
                        item.getDescricaoProduto(),
                        modelo
                );
                double similaridade = calcularSimilaridade(
                        descricaoInventarioNormalizada,
                        descricaoNotaNormalizada
                );
                AvaliacaoDimensoes dimensoes = avaliarDimensoes(
                        descricaoInventario,
                        item.getDescricaoProduto()
                );
                boolean revisaoManual = identidade.revisaoManual()
                        || item.getQuantidade() == null
                        || item.getQuantidade() <= 0;
                boolean dimensoesConfirmadas = !extrairDimensoesEmCentimetros(descricaoInventario).isEmpty()
                        && !extrairDimensoesEmCentimetros(item.getDescricaoProduto()).isEmpty()
                        && dimensoes.compativel();
                if (revisaoManual
                        && identidade.marcaConfirmada()
                        && dimensoesConfirmadas
                        && !identidade.atributosRevisaoManual()
                        && similaridade >= limiteSimilaridadeConfirmada) {
                    revisaoManual = false;
                }
                if (!revisaoManual && (similaridade >= 0.30 || !identidade.revisaoManual())) {
                    candidatos.add(new CandidatoAssociacao(
                            indiceGrupo,
                            indiceItem,
                            similaridade,
                            identificadorDocumento(item, indiceItem),
                            "OK"
                    ));
                } else if (identidade.revisaoMaterial()
                        && !dimensoes.revisaoManual()
                        && identidade.marcaConfirmada()
                        && item.getQuantidade() != null
                        && item.getQuantidade() > 0
                        && similaridade >= limiteSimilaridadeConfirmada) {
                    candidatos.add(new CandidatoAssociacao(
                            indiceGrupo,
                            indiceItem,
                            similaridade,
                            identificadorDocumento(item, indiceItem),
                            STATUS_REVISAO_MATERIAL
                    ));
                }
            }
        }

        Map<String, List<CandidatoAssociacao>> candidatosPorDocumento = new LinkedHashMap<>();
        for (CandidatoAssociacao candidato : candidatos) {
            candidatosPorDocumento
                    .computeIfAbsent(candidato.documento(), ignorado -> new ArrayList<>())
                    .add(candidato);
        }

        for (List<CandidatoAssociacao> candidatosDocumento : candidatosPorDocumento.values()) {
            Set<Integer> gruposAtribuidos = new HashSet<>();
            Set<Integer> itensAtribuidos = new HashSet<>();
            Set<Integer> gruposAmbiguos = indicesComMelhorCandidatoAmbiguo(candidatosDocumento, false);
            Set<Integer> itensAmbiguos = indicesComMelhorCandidatoAmbiguo(candidatosDocumento, true);
            candidatosDocumento.sort(Comparator.comparingDouble(CandidatoAssociacao::escore).reversed());

            for (CandidatoAssociacao candidato : candidatosDocumento) {
                if (gruposAmbiguos.contains(candidato.indiceGrupo())
                        || itensAmbiguos.contains(candidato.indiceItem())
                        || gruposAtribuidos.contains(candidato.indiceGrupo())
                        || itensAtribuidos.contains(candidato.indiceItem())) {
                    continue;
                }

                gruposAtribuidos.add(candidato.indiceGrupo());
                itensAtribuidos.add(candidato.indiceItem());
                indicesItensNotaConsumidos.add(candidato.indiceItem());
                ItemNotaFiscal item = itensNotaAgrupados.get(candidato.indiceItem());
                correspondenciasPorGrupo
                        .computeIfAbsent(candidato.indiceGrupo(), ignorado -> new ArrayList<>())
                        .add(new ItemComStatus(item, candidato.status()));
            }
        }

        List<ItemPendenteNotaFiscal> pendencias = criarPendencias(
                itensNotaAgrupados,
                gruposInventario,
                correspondenciasPorGrupo,
                indicesItensNotaConsumidos,
                marcasInventario
        );
        aplicarAssociacoesManuais(
                associacoesManuais,
                pendencias,
                gruposInventario,
                itensNotaAgrupados,
                correspondenciasPorGrupo,
                indicesItensNotaConsumidos
        );

        for (int indiceGrupo = 0; indiceGrupo < gruposInventario.size(); indiceGrupo++) {
            InventarioAgrupado inventario = gruposInventario.get(indiceGrupo);
            adicionarAlocacoes(
                    inventario,
                    correspondenciasPorGrupo.getOrDefault(indiceGrupo, List.of()),
                    resultados
            );
        }

        // 4. Adiciona ao relatório APENAS os itens reais da Nota Fiscal que NÃO foram consumidos,
        // exibindo traços nas colunas do inventário físico ausente.
        for (int i = 0; i < itensNotaAgrupados.size(); i++) {
            if (!indicesItensNotaConsumidos.contains(i)) {
                ItemNotaFiscal itemNaoMapeado = itensNotaAgrupados.get(i);
                String descNota = itemNaoMapeado.getDescricaoProduto();

                if (isDescricaoProdutoValida(itemNaoMapeado)) {
                    resultados.add(new ResultadoConciliacao(
                            "-",
                            "-",
                            descNota,
                            "N/A",
                            null,
                            itemNaoMapeado.getQuantidade(),
                            itemNaoMapeado.getNumeroNota(),
                            itemNaoMapeado.getNomeArquivoOrigem(),
                            STATUS_ITEM_APENAS_NA_NF
                    ));
                }
            }
        }

        return new AnaliseConciliacao(resultados, pendencias);
    }

    private List<ItemPendenteNotaFiscal> criarPendencias(
            List<ItemNotaFiscal> itensNotaAgrupados,
            List<InventarioAgrupado> gruposInventario,
            Map<Integer, List<ItemComStatus>> correspondenciasPorGrupo,
            Set<Integer> indicesItensNotaConsumidos,
            List<String> marcasInventario
    ) {
        List<ItemPendenteNotaFiscal> pendencias = new ArrayList<>();
        for (int indiceItem = 0; indiceItem < itensNotaAgrupados.size(); indiceItem++) {
            if (indicesItensNotaConsumidos.contains(indiceItem)) continue;
            ItemNotaFiscal item = itensNotaAgrupados.get(indiceItem);
            if (!isDescricaoProdutoValida(item)) continue;

            List<SugestaoInventario> sugestoes = new ArrayList<>();
            if (item.getQuantidade() != null && item.getQuantidade() > 0) {
                for (int indiceGrupo = 0; indiceGrupo < gruposInventario.size(); indiceGrupo++) {
                    InventarioAgrupado inventario = gruposInventario.get(indiceGrupo);
                    double quantidadeRestante = calcularQuantidadeFisicaRestante(
                            inventario,
                            correspondenciasPorGrupo.getOrDefault(indiceGrupo, List.of())
                    );
                    if (quantidadeRestante <= 0.000001) continue;

                    AvaliacaoIdentidade identidade = avaliarIdentidade(
                            inventario.getProdutoBase(),
                            item,
                            marcasInventario
                    );
                    if (!identidade.compativel()) continue;

                    double similaridade = calcularSimilaridade(
                            normalizarDescricaoComModelo(
                                    descricaoCompleta(inventario.getProdutoBase()),
                                    inventario.getProdutoBase().getModelo()
                            ),
                            normalizarDescricaoComModelo(
                                    item.getDescricaoProduto(),
                                    inventario.getProdutoBase().getModelo()
                            )
                    );
                    if (similaridade < limiteSimilaridadeSugestao
                            || !compartilhaTermoCaracteristico(
                                    descricaoCompleta(inventario.getProdutoBase()),
                                    item.getDescricaoProduto()
                            )) {
                        continue;
                    }

                    sugestoes.add(new SugestaoInventario(
                            indiceGrupo,
                            inventario.getCodigosRestantes(
                                    inventario.getQuantidadeTotal() - quantidadeRestante
                            ),
                            descricaoRelatorio(inventario.getProdutoBase()),
                            inventario.getEstado(),
                            quantidadeRestante,
                            similaridade
                    ));
                }
            }
            sugestoes.sort(Comparator.comparingDouble(SugestaoInventario::similaridade).reversed());
            pendencias.add(new ItemPendenteNotaFiscal(
                    indiceItem,
                    item.getDescricaoProduto(),
                    item.getQuantidade(),
                    item.getNumeroNota(),
                    item.getNomeArquivoOrigem(),
                    item.getDataEmissao(),
                    sugestoes.stream().limit(4).toList()
            ));
        }
        return List.copyOf(pendencias);
    }

    private void aplicarAssociacoesManuais(
            List<AssociacaoManual> associacoesManuais,
            List<ItemPendenteNotaFiscal> pendencias,
            List<InventarioAgrupado> gruposInventario,
            List<ItemNotaFiscal> itensNotaAgrupados,
            Map<Integer, List<ItemComStatus>> correspondenciasPorGrupo,
            Set<Integer> indicesItensNotaConsumidos
    ) {
        Set<Integer> itensSelecionados = new HashSet<>();
        Map<Integer, ItemPendenteNotaFiscal> pendenciasPorItem = new HashMap<>();
        for (ItemPendenteNotaFiscal pendencia : pendencias) {
            pendenciasPorItem.put(pendencia.indiceNotaFiscal(), pendencia);
        }
        Map<Integer, Double> quantidadeDisponivelPorGrupo = new HashMap<>();
        for (int indiceGrupo = 0; indiceGrupo < gruposInventario.size(); indiceGrupo++) {
            quantidadeDisponivelPorGrupo.put(
                    indiceGrupo,
                    calcularQuantidadeFisicaRestante(
                            gruposInventario.get(indiceGrupo),
                            correspondenciasPorGrupo.getOrDefault(indiceGrupo, List.of())
                    )
            );
        }

        for (AssociacaoManual associacao : associacoesManuais) {
            if (associacao == null || !itensSelecionados.add(associacao.indiceNotaFiscal())) {
                throw new RequisicaoInvalidaException("Um item fiscal foi selecionado mais de uma vez.");
            }
            ItemPendenteNotaFiscal pendencia = pendenciasPorItem.get(associacao.indiceNotaFiscal());
            if (associacao.indiceNotaFiscal() < 0
                    || associacao.indiceNotaFiscal() >= itensNotaAgrupados.size()
                    || associacao.indiceInventario() < 0
                    || associacao.indiceInventario() >= gruposInventario.size()
                    || pendencia == null
                    || pendencia.sugestoes().stream().noneMatch(
                            sugestao -> sugestao.indiceInventario() == associacao.indiceInventario())
                    || indicesItensNotaConsumidos.contains(associacao.indiceNotaFiscal())) {
                throw new RequisicaoInvalidaException("A sugestão selecionada não está disponível para revisão.");
            }

            ItemNotaFiscal item = itensNotaAgrupados.get(associacao.indiceNotaFiscal());
            if (item.getQuantidade() == null || item.getQuantidade() <= 0) {
                throw new RequisicaoInvalidaException("Não é possível associar um item sem quantidade fiscal válida.");
            }
            List<ItemComStatus> correspondenciasGrupo = correspondenciasPorGrupo.computeIfAbsent(
                    associacao.indiceInventario(),
                    ignorado -> new ArrayList<>()
            );
            double quantidadeFisicaRestante = quantidadeDisponivelPorGrupo.get(associacao.indiceInventario());
            if (quantidadeFisicaRestante <= 0.000001) {
                throw new RequisicaoInvalidaException(
                        "Não há saldo físico disponível para essa associação manual."
                );
            }

            correspondenciasGrupo.add(new ItemComStatus(item, "CONCILIADO MANUALMENTE"));
            quantidadeDisponivelPorGrupo.put(
                    associacao.indiceInventario(),
                    subtrairQuantidade(
                            quantidadeFisicaRestante,
                            Math.min(quantidadeFisicaRestante, item.getQuantidade())
                    )
            );
            indicesItensNotaConsumidos.add(associacao.indiceNotaFiscal());
        }
    }

    private double calcularSimilaridade(String descricaoInventario, String descricaoNota) {
        return (MatchUtils.calcularSimilaridadeJaccardComStemming(
                descricaoInventario,
                descricaoNota
        ) * 0.7) + (MatchUtils.calcularSimilaridadeLevenshtein(
                descricaoInventario,
                descricaoNota
        ) * 0.3);
    }

    private boolean compartilhaTermoCaracteristico(String descricaoInventario, String descricaoNota) {
        Set<String> termosInventario = extrairTermosCaracteristicos(descricaoInventario);
        termosInventario.retainAll(extrairTermosCaracteristicos(descricaoNota));
        return !termosInventario.isEmpty();
    }

    private Set<String> extrairTermosCaracteristicos(String descricao) {
        Set<String> termos = new HashSet<>();
        for (String token : MatchUtils.normalizar(descricao).split("\\s+")) {
            if (token.length() <= 2
                    || token.chars().anyMatch(Character::isDigit)
                    || TERMOS_SEM_PESO_PARA_SUGESTAO.contains(token)) {
                continue;
            }
            termos.add(MatchUtils.aplicarStemming(token));
        }
        return termos;
    }

    private double calcularQuantidadeFisicaRestante(
            InventarioAgrupado inventario,
            List<ItemComStatus> correspondencias
    ) {
        double quantidadeRestante = inventario.getQuantidadeTotal();
        for (ItemComStatus correspondencia : correspondencias) {
            Double quantidadeFiscal = correspondencia.item().getQuantidade();
            if (quantidadeFiscal == null || quantidadeFiscal <= 0) continue;
            quantidadeRestante = subtrairQuantidade(
                    quantidadeRestante,
                    Math.min(quantidadeRestante, quantidadeFiscal)
            );
            if (quantidadeRestante == 0) break;
        }
        return quantidadeRestante;
    }

    private Set<Integer> indicesComMelhorCandidatoAmbiguo(
            List<CandidatoAssociacao> candidatos,
            boolean agruparPorItem
    ) {
        Map<Integer, List<CandidatoAssociacao>> porIdentidade = new HashMap<>();
        for (CandidatoAssociacao candidato : candidatos) {
            int chave = agruparPorItem ? candidato.indiceItem() : candidato.indiceGrupo();
            porIdentidade.computeIfAbsent(chave, ignorado -> new ArrayList<>()).add(candidato);
        }

        Set<Integer> ambiguos = new HashSet<>();
        Comparator<CandidatoAssociacao> ordem =
                Comparator.comparingDouble(CandidatoAssociacao::escore).reversed();
        for (Map.Entry<Integer, List<CandidatoAssociacao>> entrada : porIdentidade.entrySet()) {
            List<CandidatoAssociacao> alternativas = entrada.getValue().stream().sorted(ordem).toList();
            if (alternativas.size() < 2) continue;

            CandidatoAssociacao melhor = alternativas.get(0);
            CandidatoAssociacao segundo = alternativas.get(1);
            int identidadeMelhor = agruparPorItem ? melhor.indiceGrupo() : melhor.indiceItem();
            int identidadeSegundo = agruparPorItem ? segundo.indiceGrupo() : segundo.indiceItem();
            if (identidadeMelhor != identidadeSegundo
                    && Math.abs(melhor.escore() - segundo.escore()) < 0.000001) {
                ambiguos.add(entrada.getKey());
            }
        }
        return ambiguos;
    }

    private void adicionarAlocacoes(
            InventarioAgrupado inventario,
            List<ItemComStatus> correspondencias,
            List<ResultadoConciliacao> resultados
    ) {
        double quantidadeFisicaRestante = inventario.getQuantidadeTotal();
        for (ItemComStatus correspondencia : correspondencias) {
            ItemNotaFiscal item = correspondencia.item();
            double quantidadeFiscalRestante = item.getQuantidade();
            double quantidadeAlocada = Math.min(quantidadeFisicaRestante, quantidadeFiscalRestante);
            if (item.getQuantidade() > 0) {
                quantidadeFiscalRestante = subtrairQuantidade(quantidadeFiscalRestante, quantidadeAlocada);
                resultados.add(criarLinha(
                        inventario,
                        inventario.consumirCodigos(quantidadeAlocada),
                        item,
                        quantidadeAlocada,
                        item.getQuantidade(),
                        correspondencia.status().equals("CONCILIADO MANUALMENTE")
                                ? quantidadeFiscalRestante > 0
                                        ? "CONCILIADO MANUALMENTE — FALTANDO NO FÍSICO"
                                        : "CONCILIADO MANUALMENTE"
                                : correspondencia.status().equals(STATUS_REVISAO_MATERIAL)
                                ? STATUS_REVISAO_MATERIAL
                                : quantidadeFiscalRestante > 0 ? STATUS_FALTANDO_NO_FISICO : "OK"
                ));
                quantidadeFisicaRestante = subtrairQuantidade(quantidadeFisicaRestante, quantidadeAlocada);
            } else if (item.getQuantidade() == 0) {
                resultados.add(criarItemApenasNaNota(item, 0.0));
            }
        }

        if (quantidadeFisicaRestante > 0) {
            resultados.add(new ResultadoConciliacao(
                    inventario.consumirCodigos(quantidadeFisicaRestante),
                    descricaoRelatorio(inventario.getProdutoBase()),
                    "-",
                    inventario.getEstado(),
                    quantidadeFisicaRestante,
                    null,
                    null,
                    null,
                    STATUS_SEM_NF
            ));
        }
    }

    private AvaliacaoDimensoes avaliarDimensoes(String descricaoInventario, String descricaoNota) {
        List<Dimensao> dimensoesInventario = extrairDimensoesEmCentimetros(descricaoInventario);
        List<Dimensao> dimensoesNota = extrairDimensoesEmCentimetros(descricaoNota);
        if (dimensoesInventario.isEmpty() && dimensoesNota.isEmpty()) {
            return new AvaliacaoDimensoes(true, false);
        }
        if (dimensoesInventario.isEmpty() || dimensoesNota.isEmpty()) {
            return new AvaliacaoDimensoes(true, true);
        }
        if (dimensoesInventario.size() != dimensoesNota.size()) {
            return new AvaliacaoDimensoes(false, false);
        }

        double maiorDiferenca = 0;
        for (int i = 0; i < dimensoesInventario.size(); i++) {
            Dimensao inventario = dimensoesInventario.get(i);
            Dimensao nota = dimensoesNota.get(i);
            int dimensoesComparadas = Math.min(inventario.valores().size(), nota.valores().size());
            if (Math.abs(inventario.valores().size() - nota.valores().size()) > 1) {
                return new AvaliacaoDimensoes(false, false);
            }
            for (int eixo = 0; eixo < dimensoesComparadas; eixo++) {
                maiorDiferenca = Math.max(
                        maiorDiferenca,
                        Math.abs(inventario.valores().get(eixo) - nota.valores().get(eixo))
                );
            }
        }
        return new AvaliacaoDimensoes(
                maiorDiferenca <= toleranciaMedidaCm,
                maiorDiferenca > 0 && maiorDiferenca <= toleranciaMedidaCm
        );
    }

    private List<Dimensao> extrairDimensoesEmCentimetros(String descricao) {
        if (descricao == null) return List.of();
        List<Dimensao> dimensoes = new ArrayList<>();
        Matcher matcher = PADRAO_DIMENSOES.matcher(descricao);
        while (matcher.find()) {
            Matcher unidadeMatcher = PADRAO_UNIDADE.matcher(descricao.substring(matcher.end()));
            String unidade = unidadeMatcher.find() ? unidadeMatcher.group(1).toLowerCase(Locale.ROOT) : "cm";
            double fator = fatorParaCentimetros(unidade);
            List<Double> valores = Arrays.stream(matcher.group().split("[xX]"))
                    .map(String::trim)
                    .map(this::numero)
                    .map(valor -> valor * fator)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            dimensoes.add(new Dimensao(valores));
        }
        if (dimensoes.isEmpty()) {
            Matcher medidaMatcher = PADRAO_MEDIDA_METRICA.matcher(descricao);
            while (medidaMatcher.find()) {
                double valor = numero(medidaMatcher.group(1))
                        * fatorParaCentimetros(medidaMatcher.group(2));
                dimensoes.add(new Dimensao(List.of(valor, 0.0)));
            }
        }
        return dimensoes;
    }

    private double fatorParaCentimetros(String unidade) {
        return switch (unidade.toLowerCase(Locale.ROOT)) {
            case "m" -> 100;
            case "mm" -> 0.1;
            default -> 1;
        };
    }

    private double numero(String valor) {
        return new BigDecimal(valor.replace(',', '.')).doubleValue();
    }

    private String identificadorDocumento(ItemNotaFiscal item, int indice) {
        String documento = item.getIdentificadorDocumentoOrigem();
        return documento == null || documento.isBlank() ? "ITEM:" + indice : documento;
    }

    private AvaliacaoAtributos avaliarAtributos(ProdutoInventario produto, String descricaoNota) {
        Set<String> atributosInventario = extrairAtributosConfiaveis(descricaoCompleta(produto));
        Set<String> atributosNota = extrairAtributosConfiaveis(descricaoNota);
        boolean revisaoMaterial = materiaisFerrososDivergem(atributosInventario, atributosNota);
        return new AvaliacaoAtributos(
                atributosInventario.isEmpty() != atributosNota.isEmpty() || revisaoMaterial,
                revisaoMaterial
        );
    }

    private Set<String> extrairAtributosConfiaveis(String descricao) {
        Set<String> atributos = new HashSet<>();
        for (String token : MatchUtils.normalizar(descricao).split("\\s+")) {
            if (ATRIBUTOS_MATERIAL.contains(token) || ATRIBUTOS_COR.contains(token)
                    || token.equals("ferro") || token.equals("carbono")) {
                atributos.add(token);
            }
        }
        return atributos;
    }

    private boolean materiaisFerrososDivergem(Set<String> atributosInventario, Set<String> atributosNota) {
        boolean ferroNoInventario = atributosInventario.contains("ferro");
        boolean ferroNaNota = atributosNota.contains("ferro");
        boolean acoNaNota = atributosNota.contains("aco") || atributosNota.contains("carbono");
        boolean acoNoInventario = atributosInventario.contains("aco") || atributosInventario.contains("carbono");
        return (ferroNoInventario && acoNaNota && !ferroNaNota)
                || (ferroNaNota && acoNoInventario && !ferroNoInventario);
    }

    private static double subtrairQuantidade(double quantidade, double quantidadeAlocada) {
        double restante = quantidade - quantidadeAlocada;
        return Math.abs(restante) < 0.000001 ? 0 : restante;
    }

    private ResultadoConciliacao criarLinha(
            InventarioAgrupado inventario,
            String codigos,
            ItemNotaFiscal item,
            Double quantidadeInventario,
            Double quantidadeNotaFiscal,
            String status
    ) {
        return new ResultadoConciliacao(
                codigos,
                descricaoRelatorio(inventario.getProdutoBase()),
                item.getDescricaoProduto(),
                inventario.getEstado(),
                quantidadeInventario,
                quantidadeNotaFiscal,
                item.getNumeroNota(),
                item.getNomeArquivoOrigem(),
                status
        );
    }

    private String descricaoCompleta(ProdutoInventario produto) {
        return String.join(" ", Arrays.asList(
                        produto.getDescricao(),
                        produto.getMarca(),
                        produto.getModelo()
                ).stream()
                .filter(valor -> valor != null && !valor.isBlank())
                .map(String::trim)
                .toList());
    }

    private String descricaoRelatorio(ProdutoInventario produto) {
        return descricaoCompleta(produto);
    }

    private boolean temModelo(ProdutoInventario produto) {
        return produto.getModelo() != null && !produto.getModelo().isBlank();
    }

    private boolean temMarca(ProdutoInventario produto) {
        return produto.getMarca() != null && !produto.getMarca().isBlank();
    }

    private AvaliacaoIdentidade avaliarIdentidade(
            ProdutoInventario produto,
            ItemNotaFiscal item,
            List<String> marcasInventario
    ) {
        String descricaoInventario = descricaoCompleta(produto);
        AvaliacaoDimensoes dimensoes = avaliarDimensoes(
                descricaoInventario,
                item.getDescricaoProduto()
        );
        if (!dimensoes.compativel()) {
            return AvaliacaoIdentidade.incompativel();
        }
        AvaliacaoAtributos atributos = avaliarAtributos(produto, item.getDescricaoProduto());

        Set<String> identificadoresInventario = extrairIdentificadoresModelo(produto);
        Set<String> identificadoresNota = extrairIdentificadoresModelo(item.getDescricaoProduto());
        boolean identificadorConfirmado = !identificadoresInventario.isEmpty()
                && identificadoresInventario.stream().anyMatch(identificadoresNota::contains);
        boolean modeloTextualConfirmado = temModelo(produto)
                && modeloEncontradoNaDescricao(produto.getModelo(), item.getDescricaoProduto());
        identificadorConfirmado = identificadorConfirmado || modeloTextualConfirmado;

        if (!identificadoresInventario.isEmpty()
                && !identificadoresNota.isEmpty()
                && !identificadorConfirmado) {
            return AvaliacaoIdentidade.incompativel();
        }

        String marcaInventario = produto.getMarca();
        String marcaNormalizada = normalizarMarca(marcaInventario);
        boolean marcaConfirmada = marcaNormalizada.isEmpty()
                || marcaEncontradaNaDescricao(marcaInventario, item.getDescricaoProduto());
        boolean outraMarcaConfirmada = !marcaNormalizada.isEmpty() && !marcaConfirmada
                && marcasInventario.stream()
                        .filter(marca -> !marcasEquivalentes(marcaNormalizada, normalizarMarca(marca)))
                        .anyMatch(marca -> marcaEncontradaNaDescricao(marca, item.getDescricaoProduto()));
        if (outraMarcaConfirmada || (!marcaConfirmada && identificadorConfirmado)) {
            return AvaliacaoIdentidade.incompativel();
        }
        boolean descricaoIdentica = MatchUtils.normalizar(descricaoInventario)
                .equals(MatchUtils.normalizar(item.getDescricaoProduto()));
        boolean exigeRevisao = dimensoes.revisaoManual()
                || atributos.revisaoManual()
                || (!identificadorConfirmado && !descricaoIdentica);
        exigeRevisao = exigeRevisao || !marcaConfirmada;

        return new AvaliacaoIdentidade(
                true,
                exigeRevisao,
                marcaNormalizada.isEmpty() || marcaConfirmada,
                atributos.revisaoManual(),
                atributos.revisaoMaterial()
        );
    }

    private boolean modeloEncontradoNaDescricao(String modelo, String descricao) {
        String[] tokensModelo = MatchUtils.normalizar(modelo).split("\\s+");
        String[] tokensDescricao = MatchUtils.normalizar(descricao).split("\\s+");
        String modeloCompacto = String.join("", tokensModelo);
        for (int inicio = 0; inicio < tokensDescricao.length; inicio++) {
            StringBuilder candidato = new StringBuilder();
            for (int i = inicio; i < tokensDescricao.length; i++) {
                candidato.append(tokensDescricao[i]);
                if (candidato.toString().equals(modeloCompacto)) return true;
                if (candidato.length() >= modeloCompacto.length()) break;
            }
        }
        return false;
    }

    private String normalizarDescricaoComModelo(String descricao, String modelo) {
        String normalizada = MatchUtils.normalizar(descricao);
        if (modelo == null || modelo.isBlank()) return normalizada;

        String modeloCompacto = String.join("", MatchUtils.normalizar(modelo).split("\\s+"));
        if (modeloCompacto.isEmpty()) return normalizada;
        String[] tokens = normalizada.split("\\s+");
        List<String> tokensNormalizados = new ArrayList<>();
        for (int inicio = 0; inicio < tokens.length;) {
            StringBuilder candidato = new StringBuilder();
            int fim = inicio;
            while (fim < tokens.length && candidato.length() < modeloCompacto.length()) {
                candidato.append(tokens[fim]);
                fim++;
            }
            if (candidato.toString().equals(modeloCompacto)) {
                tokensNormalizados.add(modeloCompacto);
                inicio = fim;
            } else {
                tokensNormalizados.add(tokens[inicio]);
                inicio++;
            }
        }
        return String.join(" ", tokensNormalizados);
    }

    private Set<String> extrairIdentificadoresModelo(ProdutoInventario produto) {
        if (produto.getModelo() == null || produto.getModelo().isBlank()) return Set.of();
        return extrairIdentificadoresModelo(produto.getModelo());
    }

    private Set<String> extrairIdentificadoresModelo(String texto) {
        Set<String> identificadores = new HashSet<>();
        if (texto == null || texto.isBlank()) return identificadores;

        String[] tokens = MatchUtils.normalizar(texto).split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].matches("(?=.*[a-z])(?=.*\\d)[a-z0-9]+")) {
                identificadores.add(tokens[i]);
            }
            if (i + 1 < tokens.length
                    && tokens[i].matches("[a-z]+")
                    && tokens[i + 1].matches("\\d{2,}")) {
                identificadores.add(tokens[i] + tokens[i + 1]);
            }
        }
        return identificadores;
    }

    private boolean marcaEncontradaNaDescricao(String marca, String descricao) {
        String marcaNormalizada = normalizarMarca(marca);
        String descricaoNormalizada = MatchUtils.normalizar(descricao);
        if (marcaNormalizada.isEmpty() || descricaoNormalizada.isEmpty()) return false;
        String[] tokensMarca = marcaNormalizada.split("\\s+");
        String[] tokensDescricao = descricaoNormalizada.split("\\s+");
        if (tokensMarca.length == 1 && tokensMarca[0].length() <= 3) {
            for (int inicio = 0; inicio < tokensDescricao.length; inicio++) {
                StringBuilder iniciais = new StringBuilder();
                for (int i = inicio; i < tokensDescricao.length; i++) {
                    if (!tokensDescricao[i].isEmpty()) iniciais.append(tokensDescricao[i].charAt(0));
                    if (iniciais.toString().equals(tokensMarca[0])) return true;
                    if (iniciais.length() >= tokensMarca[0].length()) break;
                }
            }
        }
        return Arrays.stream(tokensMarca).allMatch(token -> Arrays.stream(tokensDescricao)
                .anyMatch(tokenDescricao -> correspondeMarca(token, tokenDescricao)));
    }

    private boolean correspondeMarca(String marca, String candidata) {
        return marca.equals(candidata)
                || (marca.length() >= 5
                    && candidata.length() >= 5
                    && (LEVENSHTEIN.apply(marca, candidata) <= 1
                        || diferemPorTrocaAdjacente(marca, candidata)));
    }

    private boolean diferemPorTrocaAdjacente(String texto, String candidato) {
        if (texto.length() != candidato.length()) return false;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) != candidato.charAt(i)) {
                return i + 1 < texto.length()
                        && texto.charAt(i) == candidato.charAt(i + 1)
                        && texto.charAt(i + 1) == candidato.charAt(i)
                        && texto.substring(i + 2).equals(candidato.substring(i + 2));
            }
        }
        return false;
    }

    private String normalizarMarca(String marca) {
        return MatchUtils.normalizar(marca).replaceAll("\\s+", " ").trim();
    }

    private boolean marcasEquivalentes(String marcaA, String marcaB) {
        return marcaEncontradaNaDescricao(marcaA, marcaB) || marcaEncontradaNaDescricao(marcaB, marcaA);
    }

    private ResultadoConciliacao criarItemApenasNaNota(
            ItemNotaFiscal item,
            Double quantidadeNota
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
                STATUS_ITEM_APENAS_NA_NF
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
    private boolean isDescricaoProdutoValida(ItemNotaFiscal item) {
        String descricao = item.getDescricaoProduto();
        if (descricao == null || descricao.isBlank()) return false;
        String nomeArquivo = item.getNomeArquivoOrigem();
        if (nomeArquivo != null && nomeArquivo.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            return true;
        }
        if (descricao.trim().length() < 5) return false;
        String normalizada = MatchUtils.normalizar(descricao);
        String[] tokens = normalizada.split("\\s+");
        if (tokens.length == 0) return false;
        if (tokens[0].equals("cod") || tokens[0].equals("codigo")
                || normalizada.contains("vl unit") || normalizada.contains("descricao dos produtos")) {
            return false;
        }
        return Arrays.stream(tokens).noneMatch(TERMOS_CABECALHO_OCR::contains)
                && !normalizada.contains("documento auxiliar")
                && !normalizada.contains("chave de acesso")
                && !descricao.contains("---------------")
                && !descricao.contains("—");
    }

    private static class InventarioAgrupado {
        private final ProdutoInventario produtoBase;
        private double quantidadeTotal;
        private final Set<String> estados = new LinkedHashSet<>();
        private final List<SaldoInventario> saldos = new ArrayList<>();
        private int indiceSaldoAtual;

        public InventarioAgrupado(ProdutoInventario produtoBase) {
            this.produtoBase = produtoBase;
            this.quantidadeTotal = 0;
        }

        public void adicionar(ProdutoInventario produto) {
            double quantidade = produto.getQuantidade();
            quantidadeTotal += quantidade;
            String codigo = produto.getCodigo() == null ? "" : produto.getCodigo().trim();
            adicionarEstado(produto.getEstado());
            saldos.add(new SaldoInventario(codigo, quantidade));
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

        public String getCodigosRestantes(double quantidadeJaConsumida) {
            double restanteParaIgnorar = quantidadeJaConsumida;
            Set<String> codigosDisponiveis = new LinkedHashSet<>();
            for (SaldoInventario saldo : saldos) {
                if (restanteParaIgnorar >= saldo.quantidadeRestante) {
                    restanteParaIgnorar = subtrairQuantidade(restanteParaIgnorar, saldo.quantidadeRestante);
                    continue;
                }
                if (!saldo.codigo.isBlank()) codigosDisponiveis.add(saldo.codigo);
                restanteParaIgnorar = 0;
            }
            return String.join(", ", codigosDisponiveis);
        }

        public String consumirCodigos(double quantidade) {
            double restante = quantidade;
            Set<String> codigosConsumidos = new LinkedHashSet<>();
            while (restante > 0 && indiceSaldoAtual < saldos.size()) {
                SaldoInventario saldo = saldos.get(indiceSaldoAtual);
                if (saldo.quantidadeRestante <= 0) {
                    indiceSaldoAtual++;
                    continue;
                }

                double consumida = Math.min(restante, saldo.quantidadeRestante);
                if (!saldo.codigo.isBlank()) codigosConsumidos.add(saldo.codigo);
                saldo.quantidadeRestante = subtrairQuantidade(saldo.quantidadeRestante, consumida);
                restante = subtrairQuantidade(restante, consumida);
                if (saldo.quantidadeRestante == 0) indiceSaldoAtual++;
            }
            return String.join(", ", codigosConsumidos);
        }

        public String getEstado() {
            return String.join(", ", estados);
        }
    }

    private static class SaldoInventario {
        private final String codigo;
        private double quantidadeRestante;

        private SaldoInventario(String codigo, double quantidadeRestante) {
            this.codigo = codigo;
            this.quantidadeRestante = quantidadeRestante;
        }
    }

    private static class ItemNotaFiscalAgrupado {
        private final ItemNotaFiscal itemBase;
        private final String identificadorDocumento;
        private Double quantidadeTotal;
        private LocalDate dataEmissao;
        private final Set<String> numerosNota = new LinkedHashSet<>();
        private final Set<String> nomesArquivo = new LinkedHashSet<>();

        private ItemNotaFiscalAgrupado(ItemNotaFiscal itemBase, String identificadorDocumento) {
            this.itemBase = itemBase;
            this.identificadorDocumento = identificadorDocumento;
            this.quantidadeTotal = itemBase.getQuantidade();
            this.dataEmissao = itemBase.getDataEmissao();
            adicionarRastreabilidade(itemBase);
        }

        private void adicionar(ItemNotaFiscal item) {
            if (quantidadeTotal == null || item.getQuantidade() == null) {
                quantidadeTotal = null;
            } else {
                quantidadeTotal += item.getQuantidade();
            }
            if (dataEmissao == null) dataEmissao = item.getDataEmissao();
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
            itemAgrupado.setDataEmissao(dataEmissao);
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

    private record CandidatoAssociacao(
            int indiceGrupo,
            int indiceItem,
            double escore,
            String documento,
            String status
    ) {}

    private record ItemComStatus(ItemNotaFiscal item, String status) {}

    public record AssociacaoManual(int indiceInventario, int indiceNotaFiscal) {}

    public record AnaliseConciliacao(
            List<ResultadoConciliacao> resultados,
            List<ItemPendenteNotaFiscal> pendencias
    ) {}

    public record ItemPendenteNotaFiscal(
            int indiceNotaFiscal,
            String descricao,
            Double quantidade,
            String numeroNotaFiscal,
            String nomeArquivo,
            LocalDate dataEmissao,
            List<SugestaoInventario> sugestoes
    ) {
        public String dataEmissaoFormatada() {
            return dataEmissao == null ? "Não identificada" : dataEmissao.format(FORMATO_DATA_EMISSAO);
        }
    }

    public record SugestaoInventario(
            int indiceInventario,
            String codigo,
            String descricao,
            String estado,
            double quantidadeDisponivel,
            double similaridade
    ) {}

    private record Dimensao(List<Double> valores) {}

    private record AvaliacaoDimensoes(boolean compativel, boolean revisaoManual) {}

    private record AvaliacaoAtributos(boolean revisaoManual, boolean revisaoMaterial) {}

    private record AvaliacaoIdentidade(
            boolean compativel,
            boolean revisaoManual,
            boolean marcaConfirmada,
            boolean atributosRevisaoManual,
            boolean revisaoMaterial
    ) {
        private static AvaliacaoIdentidade incompativel() {
            return new AvaliacaoIdentidade(false, false, false, false, false);
        }
    }
}