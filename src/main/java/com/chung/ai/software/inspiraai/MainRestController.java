package com.chung.ai.software.inspiraai;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api")
public class MainRestController {

    @Autowired
    private YouTubeDownloader youtubeDownloader;

    @Value("${ytDlpHome}")
    private String ytDlpHome;

    @PostMapping("/getaudio")
    public String getAudioFile(@RequestParam("targetUrl") String targetUrl) {
            youtubeDownloader.downloadAudio(targetUrl,"downloads/test.mp3", ytDlpHome);
            return "success";
    }

}
