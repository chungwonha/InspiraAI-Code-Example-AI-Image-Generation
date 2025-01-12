package com.chung.ai.software.inspiraai.aws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    public AwsUtil(DynamoDbClient dynamoDbClient,
                   S3Client s3Client,
                   S3Presigner s3Presigner) {
        this.dynamoDbClient = dynamoDbClient;
        this.s3Client = s3Client;
        this.presigner = s3Presigner;
    }

    private static final Map<String, String> AUDIO_EXTENSIONS = new HashMap<>();

    static {
        AUDIO_EXTENSIONS.put("mp3", "audio/mpeg");
        AUDIO_EXTENSIONS.put("wav", "audio/wav");
        AUDIO_EXTENSIONS.put("ogg", "audio/ogg");
        AUDIO_EXTENSIONS.put("m4a", "audio/mp4");
        AUDIO_EXTENSIONS.put("aac", "audio/aac");
        AUDIO_EXTENSIONS.put("opus", "audio/opus");
        // Add more audio extensions if needed
    }

public String uploadGeneralAudioToS3(String userid, Resource audioResource) {
    String keyName = userid+"/"+"audio-files/" + System.currentTimeMillis() + ".mp3"; // Generate unique key
    return this.uploadToS3(keyName, audioResource,"audio/mpeg");
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
public String uploadYoutubeAudioToS3(String userid, String mp3FileName,Resource audioResource) {
    String keyName = userid+"/"+"audio-files/" + mp3FileName; // Generate unique key
    return this.uploadToS3(keyName, audioResource, "audio/mpeg");
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

    public List<AudioFile> listAudioFiles(String bucketName) {

        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).build();
        ListObjectsV2Response response = this.s3Client.listObjectsV2(request);

        List<AudioFile> audioFiles = new ArrayList<>();
        for (S3Object s3Object : response.contents()) {
            String key = s3Object.key();
            String extension = getFileExtension(key);
            if (AUDIO_EXTENSIONS.containsKey(extension)) {
                String url = this.generatePresignedUrl(bucketName, key);
                audioFiles.add(new AudioFile(key, url));
            }
        }
        return audioFiles;
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
            return new UrlResource(fileUrl);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error retrieving audio file from S3", e);
        }
    }

    public static class AudioFile {
        private final String name;
        private final String url;

        public AudioFile(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }

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
