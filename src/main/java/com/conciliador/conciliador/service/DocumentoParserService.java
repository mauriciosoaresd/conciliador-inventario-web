package com.conciliador.conciliador.service;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentoParserService {

    private static final Pattern QUANTIDADE_ANTES_DO_VALOR = Pattern.compile(
            "(?<![\\p{L}\\d])([0-9]{1,6}(?:[.,][0-9]+)?)\\s+(?:[-—–:|]\\s*)?\\d+(?:[.,]\\d{2})\\b"
    );
    private static final Pattern VALOR_MONETARIO = Pattern.compile("\\d+[.,]\\d{2}");
    private static final Pattern QUANTIDADE_ISOLADA = Pattern.compile("(?<![\\p{L}\\d.,])\\d{1,6}(?:[.,]\\d+)?(?![\\d.,])");
    private static final double QUANTIDADE_COLUNA_X = 0.78;
    private static final double QUANTIDADE_COLUNA_LARGURA = 0.07;
    private static final double QUANTIDADE_COLUNA_Y = 0.2;
    private static final double QUANTIDADE_COLUNA_ALTURA = 0.7;
    private static final int ESCALA_OCR_COLUNA = 3;

    public String extrairTexto(MultipartFile arquivo) {
        String nomeOriginal = arquivo.getOriginalFilename();
        if (nomeOriginal == null) return "";

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("upload-", obterSufixo(nomeOriginal));
            try (InputStream input = arquivo.getInputStream()) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            File file = tempFile.toFile();

            String textoExtraido = "";

            if (nomeOriginal.toLowerCase().endsWith(".pdf")) {
                // 1. Tenta extração de texto digital direto com PDFBox
                textoExtraido = extrairTextoDePdf(file);

                // 2. Se o PDF for escaneado (texto vazio), renderiza as páginas como imagem e roda o OCR
                if (textoExtraido.trim().isEmpty()) {
                    textoExtraido = executarOcrEmPdfScaneado(file);
                }
            } else if (nomeOriginal.toLowerCase().matches(".*\\.(png|jpg|jpeg)$")) {
                textoExtraido = executarOcrEmImagem(file);
            }

            return textoExtraido;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar o documento digital/imagem: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    throw new RuntimeException("Erro ao remover arquivo temporário do documento: " + e.getMessage(), e);
                }
            }
        }
    }

    private String obterSufixo(String nomeArquivo) {
        int indiceExtensao = nomeArquivo.lastIndexOf('.');
        return indiceExtensao >= 0 ? nomeArquivo.substring(indiceExtensao) : ".tmp";
    }

    public List<Double> extrairQuantidadesColuna(MultipartFile arquivo) {
        String nomeOriginal = arquivo.getOriginalFilename();
        if (nomeOriginal == null || !nomeOriginal.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg)$")) {
            return List.of();
        }

        try (InputStream input = arquivo.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                return List.of();
            }

            int x = (int) (image.getWidth() * QUANTIDADE_COLUNA_X);
            int width = (int) (image.getWidth() * QUANTIDADE_COLUNA_LARGURA);
            int y = (int) (image.getHeight() * QUANTIDADE_COLUNA_Y);
            int height = (int) (image.getHeight() * QUANTIDADE_COLUNA_ALTURA);
            BufferedImage column = image.getSubimage(x, y, width, height);
            BufferedImage enlarged = new BufferedImage(
                    width * ESCALA_OCR_COLUNA,
                    height * ESCALA_OCR_COLUNA,
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D graphics = enlarged.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(column, 0, 0, enlarged.getWidth(), enlarged.getHeight(), null);
            } finally {
                graphics.dispose();
            }

            Tesseract tesseract = inicializarTesseract();
            tesseract.setPageSegMode(6);
            String textoColuna = tesseract.doOCR(enlarged);

            List<Double> quantidades = new ArrayList<>();
            Matcher matcher = QUANTIDADE_ISOLADA.matcher(textoColuna);
            while (matcher.find()) {
                quantidades.add(Double.parseDouble(matcher.group().replace(',', '.')));
            }
            return quantidades;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao extrair quantidades da coluna do documento: " + e.getMessage(), e);
        }
    }

    public OptionalDouble extrairQuantidadeDaLinha(String linha) {
        Matcher matcher = QUANTIDADE_ANTES_DO_VALOR.matcher(linha);
        String quantidade = null;
        while (matcher.find()) {
            quantidade = matcher.group(1);
        }
        return quantidade == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(Double.parseDouble(quantidade.replace(',', '.')));
    }

    public boolean contemValorMonetario(String linha) {
        return VALOR_MONETARIO.matcher(linha).find()
                && !linha.stripLeading().toUpperCase(Locale.ROOT).startsWith("TOTAL");
    }

    private String extrairTextoDePdf(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            return "";
        }
    }

    private String executarOcrEmPdfScaneado(File file) {
        StringBuilder sb = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            Tesseract tesseract = inicializarTesseract();

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                // Renderiza a página do PDF em uma imagem a 300 DPI para alta qualidade de OCR
                BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 300);
                String textoPagina = tesseract.doOCR(bim);
                sb.append(textoPagina).append("\n");
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao renderizar PDF para OCR: " + e.getMessage(), e);
        }
        return sb.toString();
    }

    private String executarOcrEmImagem(File file) {
        try {
            Tesseract tesseract = inicializarTesseract();
            return tesseract.doOCR(file);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao executar OCR na imagem: " + e.getMessage(), e);
        }
    }

    /**
     * Inicializa a instância do Tesseract apontando para o diretório "tessdata"
     * e configurando o idioma português.
     */
    private Tesseract inicializarTesseract() {
        Tesseract tesseract = new Tesseract();

        // Verifica se a pasta tessdata existe na raiz ou em src/main/resources
        File tessDataFolder = new File("tessdata");
        if (!tessDataFolder.exists()) {
            tessDataFolder = new File("src/main/resources/tessdata");
        }

        if (tessDataFolder.exists()) {
            tesseract.setDatapath(tessDataFolder.getAbsolutePath());
        }

        tesseract.setLanguage("por"); // Define o idioma português
        return tesseract;
    }
}