package com.chung.ai.software.inspiraai;

import com.chung.ai.software.inspiraai.springai.VoiceService;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.core.io.Resource;
import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_3;


@Controller
@Slf4j
public class MainController {

    @Autowired
    private VoiceService voiceService;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/generateImage")
    public String generateImage(@RequestParam("prompt") String prompt, Model model) {
        ImageModel imageModel = OpenAiImageModel.builder()
                .apiKey(openaiApiKey)
                .modelName(DALL_E_3)
                .build();

        Response<Image> response = imageModel.generate(prompt);
        String imageUrl = response.content().url().toString();
        log.info("Generated image URL: {}", imageUrl);
        model.addAttribute("imageUrl", imageUrl);
        return "result";
    }

    @PostMapping("/analyzeImage")
    public String analyzeImage(@RequestParam("image") MultipartFile image, Model model) {
        if (image.isEmpty()) {
            model.addAttribute("error", "Please upload an image.");
            return "index";
        }

        try {
            // Convert the uploaded file to a base64-encoded data URL
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:image/png;base64," + base64Image;

            // Create the OpenAI model
            ChatLanguageModel chatModel = OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName("gpt-4o") //gpt-4-turbo-2024-04-09")
                    .maxTokens(500)
                    .build();

            // Create the user message with the image
            UserMessage userMessage = UserMessage.from(
                    TextContent.from("What do you see? give me a description in detail"),
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

//    @PostMapping(path="/audioAsk", produces = "audio/mpeg")
//    public Resource audioAskAudioResponse(@RequestParam("scripts") String scripts) {
//
//      //  String transcription = voiceService.transcribe(blob.getResource());
////        Question transcribedQuestion = new Question(game, transcription);
////        Answer answer = boardGameService.askQuestion(
////                transcribedQuestion, conversationId);
//        return voiceService.textToSpeech(scripts);
//    }

}
