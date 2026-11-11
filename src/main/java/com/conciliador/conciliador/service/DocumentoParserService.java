package com.conciliador.conciliador.service;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DocumentoParserService {

    public String extrairTexto(MultipartFile arquivo) {
        String nomeOriginal = arquivo.getOriginalFilename();
        if (nomeOriginal == null) return "";

        try {
            Path tempFile = Files.createTempFile("upload-", nomeOriginal);
            arquivo.transferTo(tempFile.toFile());
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

            Files.deleteIfExists(tempFile);
            return textoExtraido;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar o documento digital/imagem: " + e.getMessage(), e);
        }
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