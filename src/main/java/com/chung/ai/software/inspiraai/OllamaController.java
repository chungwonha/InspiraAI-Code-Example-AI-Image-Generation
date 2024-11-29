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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

@RestController
@RequestMapping("/ollama")
@Slf4j
public class OllamaController {
    private static final String OLLAMA_HOST = "http://localhost:11434"; // Replace with your Ollama server address
    private static final String MODEL_NAME = "llama3"; // Replace with your desired Ollama model

    @PostMapping("/analyzeImage")
    public String extractText(@RequestParam("image") MultipartFile image, Model model) {
        if (image.isEmpty()) {
            model.addAttribute("error", "Please upload an image.");
            return "index";
        }

        try {
            // Convert the uploaded file to a base64-encoded data URL
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:image/png;base64," + base64Image;

            // Connect to Ollama model
            ChatLanguageModel chatModel = OllamaChatModel.builder()
                    .baseUrl(OLLAMA_HOST)
                    .modelName(MODEL_NAME)
                    .build();

            // Create the user message with the image
            UserMessage userMessage = UserMessage.from(
                    TextContent.from("what do you see?"),
                    ImageContent.from(dataUrl)
            );

            // Generate the response
            Response<AiMessage> response = chatModel.generate(userMessage);
            String extractedText = response.content().text();

            // Add the extracted text to the model
            model.addAttribute("imageAnalysis", extractedText);

        } catch (IOException e) {
            log.error("Error processing the uploaded image", e);
            model.addAttribute("error", "An error occurred while processing the image.");
        }

        return "imageanalysis";
    }

}
