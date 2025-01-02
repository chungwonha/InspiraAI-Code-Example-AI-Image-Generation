package com.chung.ai.software.inspiraai;

import com.chung.ai.software.inspiraai.aws.AwsUtil;
import com.chung.ai.software.inspiraai.springai.VoiceService;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_3;

@Controller
@Slf4j
public class MainController {


    @Autowired
    private AwsUtil awsUtil;

    @Autowired
    private VoiceService voiceService;

    @Autowired
    private YouTubeDownloader youtubeDownloader;

    @Value("${ytDlpHome}")
    private String ytDlpHome;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    ChatClient chatClient;

    public MainController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
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
            // Upload the image to S3 and get the URL
            String imageUrl = awsUtil.uploadImageToS3("1",image);
            if (imageUrl == null) {
                model.addAttribute("error", "Failed to upload image to S3.");
                return "index";
            }
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
            String audio_file_url = awsUtil.uploadGeneralAudioToS3("1",voiceService.textToSpeech(extractedText));
            log.info("audio_file_url: {}", audio_file_url);
            // Add the extracted text to the model
            model.addAttribute("imageAnalysis", extractedText);
            model.addAttribute("audioUrl", audio_file_url);
            model.addAttribute("imageUrl", dataUrl);
        } catch (IOException e) {
            log.error("Error processing the uploaded image", e);
            model.addAttribute("error", "An error occurred while processing the image.");
        }

        return "imageanalysis2";
    }


    @PostMapping("/downloadAudio")
    public String downloadAudioFromYouTube(@RequestParam("targetUrl") String targetUrl, Model model) {
        String outputPath = "downloads/test.mp3";
        String fileName = youtubeDownloader.downloadAudio(targetUrl, outputPath, ytDlpHome);
        log.info("Generated audio file: {}", fileName);

        // Upload the generated audio file to S3
        Path audioPath = Paths.get(ytDlpHome+"\\"+fileName);

        Resource audioResource = new FileSystemResource(audioPath.toFile());
        String audioFileUrl = awsUtil.uploadYoutubeAudioToS3("1",fileName,audioResource);

        if (audioFileUrl != null) {
            model.addAttribute("audioUrl", audioFileUrl);
            return "redirect:/ytAudioTranscribe";
        } else {
            model.addAttribute("error", "Failed to upload audio to S3.");
            return "index";
        }
    }

    @GetMapping("/ytAudioTranscribe")
    public String ytAudioTranscribe(Model model) {
        List<AwsUtil.AudioFile> audioFiles = awsUtil.listAudioFiles(bucketName);
        model.addAttribute("audioFiles", audioFiles);
        return "yt_audio_transcribe";
    }


    @PostMapping("/transcribeAudio")
    public String transcribeAudio(@RequestParam("selectedAudio") String selectedAudio,
                                  @RequestParam("audioFileName") String audioFileName,
                                  Model model) {
        log.info("Selected audio: {}", selectedAudio);
        log.info("Audio file name: {}", audioFileName);
        String userId = "1";

        String videoid = parseMp3FileName(audioFileName);
        String transcriptionKey = videoid + "_" + userId;

        // Check if transcription and summary already exist in S3
        Map<String, AttributeValue> existingTranscription = awsUtil.getTranscriptionFromDB(transcriptionKey);

        if (existingTranscription !=null) {
            // If transcription and summary exist, use them
            model.addAttribute("transcription", existingTranscription.get("transcription").s());
            model.addAttribute("summary", existingTranscription.get("summary").s());
        }else {
            Resource audioResource = awsUtil.getAudioFileFromS3(selectedAudio);
            String transcription = voiceService.transcribe(audioResource);
            model.addAttribute("transcription", transcription);
            String prompt = "Summarize the provided transcription to 3 sentences. Transcription: " + transcription;
            String summary = chatClient.prompt().user(prompt).call().content();

            awsUtil.storeTranscriptionInDynamoDB(videoid + "_" + userId, "transcriptions", selectedAudio, transcription, summary);
            log.info("videoid: {}", videoid);
            // Add attributes to the model
            model.addAttribute("summary", summary);
            //model.addAttribute("transcription", transcription);
        }

        model.addAttribute("userId", userId);
        model.addAttribute("audioUrl", selectedAudio);

        return "transcription_result";
    }

    public String parseMp3FileName(String filePath) {
        if (filePath == null || !filePath.endsWith(".mp3")) {
            throw new IllegalArgumentException("Invalid MP3 file path");
        }
        int lastSlashIndex = filePath.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return filePath; // No directory structure, return the file name itself
        }
        return filePath.substring(lastSlashIndex + 1);
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
