package com.chung.ai.software.inspiraai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Controller
@RequestMapping("/ollama")
@Slf4j
public class OllamaController {
    private static final String OLLAMA_HOST = "http://localhost:11434"; // Replace with your Ollama server address
    private static final String MODEL_NAME = "llama3.2-vision:latest"; // Replace with your desired Ollama model

    @PostMapping("/analyzeImage2")
    public String analyzeImage2(@RequestParam("ollamaImage") MultipartFile ollamaImage, Model model) {
        if (ollamaImage.isEmpty()) {
            model.addAttribute("error", "Please upload an image.");
            return "index";
        }

        try {
            // Read the image file
            byte[] imageBytes = ollamaImage.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = ollamaImage.getContentType();

            // Connect to Ollama model
            ChatLanguageModel chatModel = OllamaChatModel.builder()
                    .baseUrl(OLLAMA_HOST)
                    .modelName(MODEL_NAME)
                    .build();

            // Create the user message with the image
            UserMessage userMessage = UserMessage.from(
                    TextContent.from("What do you see? give me a description in detail"),
                    ImageContent.from(base64Image, mimeType)
            );

            // Generate the response
            Response<AiMessage> response = chatModel.generate(userMessage);
            String extractedText = response.content().text();
            log.info("Extracted text: {}", extractedText);

            // Add the extracted text to the model
            model.addAttribute("imageAnalysis", extractedText);

        } catch (IOException e) {
            log.error("Error processing the uploaded image", e);
            model.addAttribute("error", "An error occurred while processing the image.");
        }

        return "imageanalysis";
    }

    @PostMapping("/summarizeText")
    public String summarizeText(@RequestParam("text") String text, Model model) {
        // Connect to Ollama model
        ChatLanguageModel chatModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_HOST)
                .modelName(MODEL_NAME)
                .build();

        // Create the user message with the text
        UserMessage userMessage = UserMessage.from(
                TextContent.from(text)
        );

        // Generate the response
        Response<AiMessage> response = chatModel.generate(userMessage);
        String extractedText = response.content().text();
        log.info("Extracted text: {}", extractedText);

        // Add the extracted text to the model
        model.addAttribute("summarizedText", extractedText);

        return "textsummary";
    }
//    @PostMapping("/analyzeImage3")
//    public String analyzeImage3(@RequestParam("fileLocation") String fileLocation,Model model) throws IOException {
//        // Connect to Ollama model
//        ChatLanguageModel chatModel = OllamaChatModel.builder()
//                .baseUrl(OLLAMA_HOST)
//                .modelName(MODEL_NAME)
//                .build();
//
//        // Read image file into base64 encoded string
//        String imagePath = "path/to/your/image.jpg";
//        String base64Image = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(fileLocation)));
//
//        // Construct prompt with image data
//        String prompt = "Describe the following image: \n" + "data:image/jpeg;base64," + base64Image;
//
//        // Generate text description using the LLM
//        String response = chatModel.generate(prompt);
//        log.info("Image description: " + response);
//
//        // Add the extracted text to the model
//        model.addAttribute("imageAnalysis", response);
//
//        return "imageanalysis";
//    }
//
//    @PostMapping("/analyzeImage")
//    public String analyzeImage(@RequestParam("image") MultipartFile image, Model model) {
//        if (image.isEmpty()) {
//            model.addAttribute("error", "Please upload an image.");
//            return "index";
//        }
//
//        try {
//
//            // Save the uploaded file to a temporary file
//            File tempFile = File.createTempFile("uploaded-", ".png");
//            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
//                fos.write(image.getBytes());
//            }
//
//            // Get the file URL
//            String fileUrl = tempFile.toURI().toURL().toString();
//
//            // Connect to Ollama model
//            ChatLanguageModel chatModel = OllamaChatModel.builder()
//                    .baseUrl(OLLAMA_HOST)
//                    .modelName(MODEL_NAME)
//                    .build();
//
//            // Create the user message with the image
//            UserMessage userMessage = UserMessage.from(
//                    TextContent.from("what do you see?"),
//                    ImageContent.from(fileUrl)
//            );
//
//            // Generate the response
//            Response<AiMessage> response = chatModel.generate(userMessage);
//            String extractedText = response.content().text();
//            log.info("Extracted text: {}", extractedText);
//
//            // Add the extracted text to the model
//            model.addAttribute("imageAnalysis", extractedText);
//
//        } catch (IOException e) {
//            log.error("Error processing the uploaded image", e);
//            model.addAttribute("error", "An error occurred while processing the image.");
//        }
//
//        return "imageanalysis";
//    }

}
