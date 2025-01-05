package com.chung.ai.software.inspiraai;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Controller
public class PdfController {

    @PostMapping("/uploadPdf")
    public String uploadPdf(@RequestParam("file") MultipartFile file, Model model) {
        if (file.isEmpty()) {
            model.addAttribute("error", "Please upload a PDF file.");
            return "index";
        }

        try {
            // Save the uploaded file to a temporary location
            Path tempFile = Files.createTempFile("uploaded", ".pdf");
            file.transferTo(tempFile.toFile());

            // Extract text from the PDF
            String extractedText = extractTextFromPdf(tempFile.toFile());

            // Add the extracted text to the model
            model.addAttribute("extractedText", extractedText);

            // Delete the temporary file
            Files.delete(tempFile);

        } catch (IOException | TesseractException e) {
            model.addAttribute("error", "An error occurred while processing the PDF file.");
        }

        return "pdf_extraction_result";
    }

    private String extractTextFromPdf(File file) throws IOException, TesseractException {
        StringBuilder extractedText = new StringBuilder();

        try (PDDocument document = PDDocument.load(file)) {
            // Extract selectable text
            PDFTextStripper pdfStripper = new PDFTextStripper();
            extractedText.append(pdfStripper.getText(document));
            log.info("Extracted text: {}", extractedText);
            // Extract text from images using Tesseract OCR
//            Tesseract tesseract = new Tesseract();
//            tesseract.setDatapath("C://tmp"); // Path to tessdata directory
//            PDFRenderer pdfRenderer = new PDFRenderer(document);
//            for (int page = 0; page < document.getNumberOfPages(); page++) {
//                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
//                File tempImageFile = Files.createTempFile("page_" + page, ".png").toFile();
//                ImageIO.write(image, "png", tempImageFile);
//                extractedText.append(tesseract.doOCR(tempImageFile));
//                tempImageFile.delete();
//            }
        }

        return extractedText.toString();
    }
}
