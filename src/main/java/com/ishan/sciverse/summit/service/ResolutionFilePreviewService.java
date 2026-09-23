package com.ishan.sciverse.summit.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ResolutionFilePreviewService {

    private static final int MAX_PREVIEW_CHARS = 20000;

    /** Extracts plain text from a resolution file for in-app preview. Returns null if unsupported or unreadable. */
    public String extractText(Path file, String fileName) {
        if (file == null || !Files.exists(file)) {
            return null;
        }
        String lower = fileName == null ? "" : fileName.toLowerCase();
        try {
            String text;
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {
                text = readPlainText(file);
            } else if (lower.endsWith(".pdf")) {
                text = extractPdf(file);
            } else if (lower.endsWith(".docx")) {
                text = extractDocx(file);
            } else if (lower.endsWith(".doc")) {
                text = extractDoc(file);
            } else {
                return null;
            }
            if (text == null) {
                return null;
            }
            text = text.replace("\u0000", "").replace("\r\n", "\n").trim();
            if (text.isBlank()) {
                return null;
            }
            if (text.length() > MAX_PREVIEW_CHARS) {
                text = text.substring(0, MAX_PREVIEW_CHARS) + "\n… (preview truncated)";
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    private String readPlainText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String extractPdf(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String extractDocx(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             WordExtractor extractor = new WordExtractor(in)) {
            return extractor.getText();
        }
    }
}
