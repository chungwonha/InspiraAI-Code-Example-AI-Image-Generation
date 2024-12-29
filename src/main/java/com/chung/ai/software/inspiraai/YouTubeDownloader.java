package com.chung.ai.software.inspiraai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class YouTubeDownloader {
    public String downloadAudio(String videoUrl, String outputPath, String ytDlpHome) {
        try {
            // Parse the video ID from the URL
            String videoId = null;
            Pattern pattern = Pattern.compile("v=([\\w-]+)");
            Matcher matcher = pattern.matcher(new URL(videoUrl).getQuery());
            if (matcher.find()) {
                videoId = matcher.group(1);
            } else {
                throw new IllegalArgumentException("Invalid YouTube URL: " + videoUrl);
            }

            String downloadFileName = videoId + ".mp3";
            // Command to execute youtube-dlp
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "cmd.exe", "/c", "run-yt-dlp.bat", "\"" + videoUrl + "\"", "\""+downloadFileName+"\""
            );

            processBuilder.directory(new File(ytDlpHome));
            // Log the working directory and command
            log.info("Working directory: " + processBuilder.directory().getAbsolutePath());
            log.info("Command: " + String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String generatedFileName = null;

            while ((line = reader.readLine()) != null) {
                log.info("Output: " + line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Failed to download audio. Exit code: " + exitCode);
            }

            return downloadFileName;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error downloading audio: " + e.getMessage(), e);
        }
    }
}