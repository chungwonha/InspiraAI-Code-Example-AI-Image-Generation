package com.chung.ai.software.inspiraai;

import com.chung.ai.software.inspiraai.aws.AwsUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
@RequestMapping("/api")
public class MainRestController {

    @Autowired
    private YouTubeDownloader youtubeDownloader;

    @Autowired
    private AwsUtil awsUtil;

    @Value("${ytDlpHome}")
    private String ytDlpHome;

    @PostMapping("/getaudio")
    public String getAudioFile(@RequestParam("targetUrl") String targetUrl) {
            youtubeDownloader.downloadAudio(targetUrl,"downloads/test.mp3", ytDlpHome);
            return "success";
    }


    @PostMapping("/uploadAudio")
    public String uploadAudio(@RequestParam("audioFile") MultipartFile audioFile) {
        // Upload the audio file to S3 and get the URL
        String fileName = audioFile.getOriginalFilename();
        String audioUrl = awsUtil.uploadYoutubeAudioToS3("1", audioFile);

        return fileName+ " uploaded successfully to S3 at URL: " + audioUrl;
    }

}
