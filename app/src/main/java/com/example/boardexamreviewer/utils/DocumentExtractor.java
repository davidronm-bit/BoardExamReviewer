package com.example.boardexamreviewer.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Utility class to extract text from documents (PDF, DOCX, etc.)
 */
public class DocumentExtractor {

    /**
     * Extracts text from the given Uri based on its extension.
     */
    public static String extractText(Context context, Uri uri) {
        String fileName = getFileName(context, uri);
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex != -1) {
            extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        }

        try {
            switch (extension) {
                case "pdf": return extractFromPdf(context, uri);
                case "docx": return extractFromDocx(context, uri);
                case "pptx": return extractFromPptx(context, uri);
                case "txt": return extractFromTxt(context, uri);
                default: return "Unsupported file type: " + extension;
            }
        } catch (Exception e) {
            return "Error extracting text: " + e.getMessage();
        }
    }

    /**
     * Helper to get filename from Uri
     */
    public static String getFileName(Context context, Uri uri) {
        String name = "unknown_file";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index != -1) {
                    name = cursor.getString(index);
                }
            }
        }
        return name;
    }

    private static String extractFromPdf(Context context, Uri uri) throws Exception {
        // Initialize PDFBox
        PDFBoxResourceLoader.init(context);

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return "Could not open PDF file";
            PDDocument document = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();

            String trimmedText = text != null ? text.trim() : "";
            if (trimmedText.isEmpty()) {
                return "This document appears to be empty or an image-based PDF (OCR not supported).";
            }
            return trimmedText;
        }
    }

    /**
     * Extracts text from a DOCX file using Apache POI
     */
    private static String extractFromDocx(Context context, Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return "Could not open DOCX file";
            XWPFDocument doc = new XWPFDocument(inputStream);
            XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
            String text = extractor.getText();
            extractor.close();
            return text.trim();
        }
    }

    /**
     * Extracts text from PowerPoint (.pptx) files
     */
    private static String extractFromPptx(Context context, Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return "Could not open PPTX file";
            XMLSlideShow ppt = new XMLSlideShow(inputStream);
            StringBuilder sb = new StringBuilder();
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        sb.append(((XSLFTextShape) shape).getText()).append("\n");
                    }
                }
            }
            return sb.toString().trim();
        }
    }

    /**
     * Extracts text from plain text (.txt) files
     */
    private static String extractFromTxt(Context context, Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            if (inputStream == null) return "Could not open notes file";
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }
}
