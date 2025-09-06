package com.chung.ai.software.inspiraai.aws;

import com.chung.ai.software.inspiraai.AudioFile;
import com.chung.ai.software.inspiraai.FileLists;
import com.chung.ai.software.inspiraai.InspiraaiFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
@Slf4j
public class AwsUtil {

    @Value("${aws.s3.access-key-id}")
    private String accessKeyId;

    @Value("${aws.s3.secret-access-key}")
    private String secretAccessKey;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    String region;

    DynamoDbClient dynamoDbClient;
    S3Client s3Client;
    S3Presigner presigner;

    FileLists fileLists;

    public AwsUtil(DynamoDbClient dynamoDbClient,
                   S3Client s3Client,
                   S3Presigner s3Presigner) {
        this.dynamoDbClient = dynamoDbClient;
        this.s3Client = s3Client;
        this.presigner = s3Presigner;
        this.fileLists = new FileLists();
    }

    private static final Map<String, String> AUDIO_EXTENSIONS = new HashMap<>();
    private static final Map<String, String> VIDEO_EXTENSIONS = new HashMap<>();
    private static final Map<String, String> IMAGE_EXTENSIONS = new HashMap<>();

    static {
        AUDIO_EXTENSIONS.put("mp3", "audio/mpeg");
        AUDIO_EXTENSIONS.put("wav", "audio/wav");
        AUDIO_EXTENSIONS.put("ogg", "audio/ogg");
        AUDIO_EXTENSIONS.put("m4a", "audio/mp4");
        AUDIO_EXTENSIONS.put("aac", "audio/aac");
        AUDIO_EXTENSIONS.put("opus", "audio/opus");
        // Add more audio extensions if needed
    }

    static {
        VIDEO_EXTENSIONS.put("mp4", "video/mp4");
        VIDEO_EXTENSIONS.put("webm", "video/webm");
        VIDEO_EXTENSIONS.put("ogg", "video/ogg");
        VIDEO_EXTENSIONS.put("mov", "video/quicktime");
        VIDEO_EXTENSIONS.put("avi", "video/x-msvideo");
        VIDEO_EXTENSIONS.put("flv", "video/x-flv");
        VIDEO_EXTENSIONS.put("wmv", "video/x-ms-wmv");
        // Add more video extensions if needed
    }

    static {
        IMAGE_EXTENSIONS.put("jpg", "image/jpeg");
        IMAGE_EXTENSIONS.put("jpeg", "image/jpeg");
        IMAGE_EXTENSIONS.put("png", "image/png");
        IMAGE_EXTENSIONS.put("gif", "image/gif");
        IMAGE_EXTENSIONS.put("bmp", "image/bmp");
        IMAGE_EXTENSIONS.put("webp", "image/webp");
        // Add more image extensions if needed
    }

public String uploadGeneralAudioToS3(String userid, Resource audioResource) {
    String originalFilename = audioResource.getFilename();
    String fileExtension = originalFilename != null && originalFilename.lastIndexOf('.') != -1 
        ? originalFilename.substring(originalFilename.lastIndexOf('.')) 
        : ".mp3";
    String keyName = userid+"/"+"audio-files/" + System.currentTimeMillis() + fileExtension;
    String extension = getFileExtension(fileExtension);
    String contentType = AUDIO_EXTENSIONS.getOrDefault(extension, "audio/mpeg");
    return this.uploadToS3(keyName, audioResource, contentType);
}

public String uploadToS3(String keyName, Resource resource, String contentType) {

    try (InputStream inputStream = resource.getInputStream()) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .contentType(contentType) // Set content type
                .build();

        this.s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, resource.contentLength()));
        String fileUrl = generatePresignedUrl(bucketName, keyName);
        // Generate and return the file URL
        return fileUrl;
    } catch (IOException e) {
        log.error("Error uploading file to S3", e);
        return null;
    }
}
public String uploadYoutubeAudioToS3(String userid, String audioFileName, Resource audioResource) {
    String keyName = userid+"/"+"audio-files/" + audioFileName; // Generate unique key
    String extension = getFileExtension(audioFileName);
    String contentType = AUDIO_EXTENSIONS.getOrDefault(extension, "audio/mpeg");
    return this.uploadToS3(keyName, audioResource, contentType);
}

public String uploadYoutubeAudioToS3(String userid, MultipartFile audioFile) {
    String keyName = userid+"/"+"audio-files/" + audioFile.getOriginalFilename(); // Generate unique key
    String extension = getFileExtension(audioFile.getOriginalFilename());
    String contentType = AUDIO_EXTENSIONS.getOrDefault(extension, "audio/mpeg");

    try (InputStream inputStream = audioFile.getInputStream()) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .contentType(contentType) // Set appropriate content type based on file extension
                .build();

        this.s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, audioFile.getSize()));
        String audioUrl = generatePresignedUrl(bucketName, keyName);
        // Generate and return the file URL
        return audioUrl;
    } catch (IOException e) {
        log.error("Error uploading audio file to S3", e);
        return null;
    }
}

public String uploadVideoToS3(String userid, String mp4FileName, Resource video) {
    String keyName = userid+"/"+"video/" + mp4FileName; // Generate unique key
    return this.uploadToS3(keyName, video,"video/mp4");
}
    public String uploadImageToS3(String userid,MultipartFile image) {
        String keyName = userid+"/"+"images/" + System.currentTimeMillis() + "-" + image.getOriginalFilename(); // Generate unique key

        try (InputStream inputStream = image.getInputStream()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .contentType(image.getContentType()) // Set content type
                    .build();

            this.s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, image.getSize()));
            String imageUrl = generatePresignedUrl(bucketName, keyName);
            // Generate and return the file URL
            return imageUrl;
        } catch (IOException e) {
            log.error("Error uploading image to S3", e);
            return null;
        }
    }

    public String generatePresignedUrl(String bucketName, String keyName) {
//        this.presigner = S3Presigner.builder()
//                .region(Region.of(region))
//                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
//                .build();

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(Duration.ofMinutes(10))
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }

    public List<FileLists.File> listAudioFiles(String bucketName) {
        return this.getAllFiles(bucketName).getAudioFiles();
//        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).build();
//        ListObjectsV2Response response = this.s3Client.listObjectsV2(request);
//
//        List<FileLists.File> audioFiles = new ArrayList<>();
//        for (S3Object s3Object : response.contents()) {
//            String key = s3Object.key();
//            String extension = getFileExtension(key);
//            if (AUDIO_EXTENSIONS.containsKey(extension)) {
//                String url = this.generatePresignedUrl(bucketName, key);
//                audioFiles.add(new FileLists.File(key, url));
//            }
//        }
//        return audioFiles;
    }

    public FileLists getAllFiles(String bucketName) {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).build();
        ListObjectsV2Response response = this.s3Client.listObjectsV2(request);

        for (S3Object s3Object : response.contents()) {
            String key = s3Object.key();
            String extension = getFileExtension(key);
            if (AUDIO_EXTENSIONS.containsKey(extension)) {
                String url = this.generatePresignedUrl(bucketName, key);
                fileLists.addAudioFile(key, url);
            } else if (VIDEO_EXTENSIONS.containsKey(extension)) {
                String url = this.generatePresignedUrl(bucketName, key);
                fileLists.addVideoFile(key, url);
            } else if (IMAGE_EXTENSIONS.containsKey(extension)) {
                String url = this.generatePresignedUrl(bucketName, key);
                fileLists.addImageFile(key, url);
            }
        }
        return fileLists;
    }

    private String getFileExtension(String fileName) {
        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot == -1) {
            return ""; // No extension found
        }
        return fileName.substring(lastIndexOfDot + 1).toLowerCase();
    }

    public Resource getAudioFileFromS3(String fileUrl) {
        try {
            // Extract the filename from the URL
            String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            // If there are query parameters, remove them
            if (fileName.contains("?")) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }

            log.info("Downloading audio file: {}", fileName);

            // Download the file content
            UrlResource urlResource = new UrlResource(fileUrl);
            byte[] fileContent = urlResource.getInputStream().readAllBytes();

            log.info("Downloaded audio file: {} ({} bytes)", fileName, fileContent.length);

            // Create a ByteArrayResource with the original filename
            final String finalFileName = fileName;
            return new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return finalFileName;
                }
            };
        } catch (Exception e) {
            log.error("Error retrieving audio file from S3", e);
            throw new RuntimeException("Error retrieving audio file from S3", e);
        }
    }

//    public static class AudioFile {
//        private final String name;
//        private final String url;
//
//        public AudioFile(String name, String url) {
//            this.name = name;
//            this.url = url;
//        }
//
//        public String getName() {
//            return name;
//        }
//
//        public String getUrl() {
//            return url;
//        }
//    }

    public void storeTranscriptionInDynamoDB(String videoid_userid,
                                             String tableName,
                                             String audioFileName,
                                             String transcription,
                                             String summary) {

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("videoid_userid", AttributeValue.builder().s(videoid_userid).build());
        item.put("audioFileName", AttributeValue.builder().s(audioFileName).build());
        item.put("transcription", AttributeValue.builder().s(transcription).build());
        item.put("summary", AttributeValue.builder().s(summary).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }

    public Map<String, AttributeValue> getTranscriptionFromDB(String transcriptionKey) {
    try {
        log.info("Retrieving transcription from DynamoDB: {}", transcriptionKey);
        // Query DynamoDB to check if the transcription exists
        Map<String, AttributeValue> keyCondition = new HashMap<>();
        keyCondition.put(":v_id", AttributeValue.builder().s(transcriptionKey).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName("transcriptions")
                .keyConditionExpression("videoid_userid = :v_id")
                .expressionAttributeValues(keyCondition)
                .build();

        QueryResponse queryResponse = dynamoDbClient.query(queryRequest);

        if (queryResponse.count() > 0) {
            // Transcription exists, retrieve it from DynamoDB
            Map<String, AttributeValue> item = queryResponse.items().get(0);
            return item;
        } else {
            // Transcription does not exist
            return null;
        }
    } catch (Exception e) {
        log.error("Error retrieving transcription from DynamoDB", e);
        return null;
    }
}


}
