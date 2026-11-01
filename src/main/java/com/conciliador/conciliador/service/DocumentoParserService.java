package com.conciliador.conciliador.service;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DocumentoParserService {

    public String extrairTexto(MultipartFile arquivo) {
        String nomeOriginal = arquivo.getOriginalFilename();
        if (nomeOriginal == null) return "";

        try {
            // Cria um arquivo temporário para processamento
            Path tempFile = Files.createTempFile("upload-", nomeOriginal);
            arquivo.transferTo(tempFile.toFile());
            File file = tempFile.toFile();

            String textoExtraido = "";

            if (nomeOriginal.toLowerCase().endsWith(".pdf")) {
                textoExtraido = extrairTextoDePdf(file);
                // Se o PDF for escaneado (texto vazio), tenta OCR
                if (textoExtraido.trim().isEmpty()) {
                    textoExtraido = executarOcr(file);
                }
            } else if (nomeOriginal.toLowerCase().matches(".*\\.(png|jpg|jpeg)$")) {
                textoExtraido = executarOcr(file);
            }

            // Remove o arquivo temporário
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

    private String executarOcr(File file) {
        try {
            Tesseract tesseract = new Tesseract();
            // Opcional: aponte para o caminho dos dados de treinamento do Tesseract se necessário (ex: "tessdata")
            // tesseract.setDatapath("/path/to/tessdata");
            tesseract.setLanguage("por"); // Define o idioma português para melhor acurácia
            return tesseract.doOCR(file);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao executar OCR na imagem/PDF: " + e.getMessage(), e);
        }
    }
}