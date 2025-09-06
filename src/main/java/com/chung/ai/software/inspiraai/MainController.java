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
import org.springframework.core.io.ByteArrayResource;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @PostMapping("/downloadAndTranscribe")
    public String downloadAndTranscribe(@RequestParam("targetUrl") String targetUrl, Model model) {
        try {
            String userId = "1";
            String outputPath = "downloads/test.mp3";
            String fileName = youtubeDownloader.downloadAudio(targetUrl, outputPath, ytDlpHome);
            log.info("Generated audio file: {}", fileName);

            // Upload the generated audio file to S3
            Path audioPath = Paths.get(ytDlpHome + "\\" + fileName);
            Resource audioResource = new FileSystemResource(audioPath.toFile());
            String audioFileUrl = awsUtil.uploadYoutubeAudioToS3(userId, fileName, audioResource);

            if (audioFileUrl == null) {
                model.addAttribute("error", "Failed to upload audio to S3.");
                return "index";
            }

            // Prepare keys and check cache (DynamoDB)
            String videoid = parseAudioFileName(fileName);
            String transcriptionKey = videoid + "_" + userId;
            Map<String, AttributeValue> existingTranscription = awsUtil.getTranscriptionFromDB(transcriptionKey);

            if (existingTranscription != null) {
                model.addAttribute("transcription", existingTranscription.get("transcription").s());
                model.addAttribute("summary", existingTranscription.get("summary").s());
            } else {
                Resource s3Audio = awsUtil.getAudioFileFromS3(audioFileUrl);
                String transcription = transcribeLargeAudio(s3Audio);
                model.addAttribute("transcription", transcription);
                String summary = chatClient.prompt().user(promptToSummarize).call().content();
                awsUtil.storeTranscriptionInDynamoDB(transcriptionKey, "transcriptions", audioFileUrl, transcription, summary);
                model.addAttribute("summary", summary);
            }

            model.addAttribute("userId", userId);
            model.addAttribute("audioUrl", audioFileUrl);
            return "transcription_result";
        } catch (Exception e) {
            log.error("Error in downloadAndTranscribe flow", e);
            model.addAttribute("error", "An error occurred while processing the YouTube URL.");
            return "index";
        }
    }


    @PostMapping("/downloadLatestVideo")
    public String downloadLatestVideo(@RequestParam("channelUrl") String channelUrl, Model model) {
        try {
            String latestVideoFileName = youtubeDownloader.downloadLatestVideo(channelUrl, ytDlpHome);
            log.info("Downloaded latest video file: {}", latestVideoFileName);

            // Upload the downloaded video file to S3
            Path videoPath = Paths.get(ytDlpHome, latestVideoFileName);
            Resource videoResource = new FileSystemResource(videoPath.toFile());
            String videoFileUrl = awsUtil.uploadVideoToS3("1", latestVideoFileName, videoResource);
            log.info("Uploaded video file to S3: {}", videoFileUrl);

            if (videoFileUrl != null) {
                model.addAttribute("videoUrl", videoFileUrl);
                return "redirect:/videodownloadsuccess";
            } else {
                model.addAttribute("error", "Failed to upload video to S3.");
                return "index";
            }
        } catch (Exception e) {
            log.error("Error downloading the latest video", e);
            model.addAttribute("error", "An error occurred while downloading the latest video.");
            return "index";
        }
    }

    @GetMapping("/ytAudioTranscribe")
    public String ytAudioTranscribe(Model model) {
//        List<AudioFile> audioFiles = awsUtil.listAudioFiles(bucketName);
        List<FileLists.File> audioFiles = awsUtil.listAudioFiles(bucketName);

        model.addAttribute("audioFiles", audioFiles);
        return "yt_audio_transcribe";
    }

    String promptToSummarize = "give me good bullet points to summarize this transcription so that I can easily and quickly understand his key points and thought process.\n" +
            "If there is any methods and know-hows to demonstrate by the speaker, please list step-by-step with short description for each step.\n";

    @PostMapping("/transcribeAudio")
    public String transcribeAudio(@RequestParam("selectedAudio") String selectedAudio,
                                  @RequestParam("audioFileName") String audioFileName,
                                  Model model) {
        log.info("Selected audio: {}", selectedAudio);
        log.info("Audio file name: {}", audioFileName);
        String userId = "1";

        String videoid = parseAudioFileName(audioFileName);
        String transcriptionKey = videoid + "_" + userId;

        // Check if transcription and summary already exist in S3
        Map<String, AttributeValue> existingTranscription = awsUtil.getTranscriptionFromDB(transcriptionKey);

        if (existingTranscription !=null) {
            // If transcription and summary exist, use them
            model.addAttribute("transcription", existingTranscription.get("transcription").s());
            model.addAttribute("summary", existingTranscription.get("summary").s());
        }else {
            Resource audioResource = awsUtil.getAudioFileFromS3(selectedAudio);

            // Get the transcription, handling large files by splitting if necessary
            String transcription = transcribeLargeAudio(audioResource);
            model.addAttribute("transcription", transcription);

//            String prompt = "Summarize the provided transcription to 3 sentences. Transcription: " + transcription;
            String summary = chatClient.prompt().user(promptToSummarize).call().content();

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

    public String parseAudioFileName(String filePath) {
        if (filePath == null || (!filePath.endsWith(".mp3") && !filePath.endsWith(".m4a"))) {
            throw new IllegalArgumentException("Invalid audio file path. Only MP3 and M4A formats are supported.");
        }
        int lastSlashIndex = filePath.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return filePath; // No directory structure, return the file name itself
        }
        return filePath.substring(lastSlashIndex + 1);
    }

    /**
     * Transcribes audio, handling large files by pre-splitting them into valid audio chunks.
     * OpenAI has a limit of 25MB per file.
     * 
     * @param audioResource The audio resource to transcribe
     * @return The complete transcription text
     */
    private String transcribeLargeAudio(Resource audioResource) {
        try {
            // OpenAI's limit is 25MB (26214400 bytes), but we'll use 24MB to be safe
            final long MAX_SIZE_BYTES = 24 * 1024 * 1024; // 24MB in bytes
            final int CHUNK_DURATION_SECONDS = 300; // 5 minutes per chunk

            // Check if the file is larger than the limit
            long fileSize = audioResource.contentLength();
            log.info("Audio file size: {} bytes", fileSize);

            // If contentLength() returns -1 (which happens with some Resource implementations like UrlResource),
            // we need to read the file into memory to determine its size
            if (fileSize == -1) {
                log.info("File size unknown, reading into memory to determine size");
                byte[] audioBytes = audioResource.getInputStream().readAllBytes();
                fileSize = audioBytes.length;
                log.info("Actual file size: {} bytes", fileSize);

                // Store the original filename
                final String originalFilename = audioResource.getFilename();

                // Create a temporary resource from the bytes
                audioResource = new ByteArrayResource(audioBytes) {
                    @Override
                    public String getFilename() {
                        return originalFilename;
                    }
                };
            }

            if (fileSize <= MAX_SIZE_BYTES) {
                // If the file is small enough, transcribe it directly
                log.info("File is within size limit, transcribing directly");
                return voiceService.transcribe(audioResource);
            }

            // If the file is too large, pre-split it into valid audio chunks
            log.info("File exceeds size limit ({}MB), pre-splitting into valid audio chunks", 
                    fileSize / (1024 * 1024));

            // Create a temporary directory to store the chunks
            String tempDirPrefix = "audio-chunks-";
            Path tempDir = Files.createTempDirectory(tempDirPrefix);
            log.info("Created temporary directory for audio chunks: {}", tempDir);

            // Save the audio resource to a temporary file
            String originalFilename = audioResource.getFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.'));
            Path tempInputFile = Files.createTempFile(tempDir, "input-", fileExtension);
            Files.copy(audioResource.getInputStream(), tempInputFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved audio resource to temporary file: {}", tempInputFile);

            // Use FFmpeg to split the audio file
            // We'll use the FFmpeg command line directly since the Java wrapper doesn't fully support the segment options
            String ffmpegPath = "ffmpeg"; // Assuming ffmpeg is in the PATH
            String segmentTime = String.valueOf(CHUNK_DURATION_SECONDS);
            String outputFilePattern = tempDir.resolve("chunk-%03d" + fileExtension).toString();

            // Determine the appropriate codec based on file extension
            String codec;
            if (fileExtension.equalsIgnoreCase(".m4a")) {
                codec = "aac"; // Use AAC codec for M4A files
            } else {
                codec = "libmp3lame"; // Default to MP3 codec for other formats
            }

            log.info("Using codec {} for file with extension {}", codec, fileExtension);

            // Build the FFmpeg command to split the audio file
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-i", tempInputFile.toString(),
                    "-f", "segment",
                    "-segment_time", segmentTime,
                    "-c:a", codec,
                    "-q:a", "2", // High quality, lower value means higher quality
                    "-b:a", "128k", // 128kbps
                    "-reset_timestamps", "1",
                    "-map", "0:a", // Only include audio
                    outputFilePattern
            );

            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true);

            // Start the process
            log.info("Executing FFmpeg command to split audio file into chunks");
            Process process = processBuilder.start();

            // Log the output
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg process failed with exit code: " + exitCode);
            }
            log.info("FFmpeg command completed successfully");


            // Get the list of chunk files
            List<Path> chunkFiles = Files.list(tempDir)
                    .filter(path -> path.getFileName().toString().startsWith("chunk-"))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());

            log.info("Found {} chunk files", chunkFiles.size());

            // Create a list of resources from the chunk files
            List<Resource> chunkResources = new ArrayList<>();
            for (Path chunkFile : chunkFiles) {
                chunkResources.add(new FileSystemResource(chunkFile.toFile()));
            }

            // Process each chunk
            StringBuilder fullTranscription = new StringBuilder();
            for (int i = 0; i < chunkResources.size(); i++) {
                Resource chunkResource = chunkResources.get(i);
                log.info("Processing chunk {} of {}: {}", 
                        i + 1, chunkResources.size(), chunkResource.getFilename());

                try {
                    // Transcribe this chunk
                    String chunkTranscription = voiceService.transcribe(chunkResource);
                    log.info("Chunk {} transcription complete: {} characters", i + 1, chunkTranscription.length());

                    // Add this chunk's transcription to the full transcription
                    if (i > 0 && !fullTranscription.toString().endsWith(" ") && !chunkTranscription.startsWith(" ")) {
                        fullTranscription.append(" "); // Add space between chunks if needed
                    }
                    fullTranscription.append(chunkTranscription);
                } catch (Exception e) {
                    log.error("Error processing chunk {} of {}: {}", i + 1, chunkResources.size(), e.getMessage(), e);
                    // Continue with next chunk if possible
                    fullTranscription.append("\n\n[Error transcribing part of the audio file. Some content may be missing.]\n\n");
                }
            }

            // Clean up temporary files
            log.info("Cleaning up temporary files");
            for (Path chunkFile : chunkFiles) {
                Files.deleteIfExists(chunkFile);
            }
            Files.deleteIfExists(tempInputFile);
            Files.deleteIfExists(tempDir);

            log.info("Full transcription complete: {} characters", fullTranscription.length());
            return fullTranscription.toString();

        } catch (Exception e) {
            log.error("Error processing audio file", e);
            throw new RuntimeException("Error processing audio file: " + e.getMessage(), e);
        }
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
