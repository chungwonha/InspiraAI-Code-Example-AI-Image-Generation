package com.chung.ai.software.inspiraai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class YouTubeDownloader {

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private static boolean isUnixLike() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("nix") || os.contains("nux") || os.contains("aix");
    }

    private static String detectYtDlpDirFallback() {
        // Try common install locations
        String[] candidates;
        if (isWindows()) {
            candidates = new String[]{
                    System.getenv("YTDL_HOME"),
                    System.getenv("ProgramFiles") + "\\yt-dlp",
                    System.getenv("LOCALAPPDATA") + "\\Programs\\yt-dlp",
                    System.getenv("USERPROFILE") + "\\bin"
            };
        } else {
            candidates = new String[]{
                    System.getenv("YTDL_HOME"),
                    "/opt/homebrew/bin", // Apple Silicon Homebrew
                    "/usr/local/bin",    // Intel macOS Homebrew / many Linuxes
                    "/usr/bin",
                    System.getProperty("user.home") + "/bin"
            };
        }
        for (String dir : candidates) {
            if (dir == null || dir.isEmpty()) continue;
            File f = new File(dir, isWindows() ? "yt-dlp.exe" : "yt-dlp");
            if (f.exists() && f.canExecute()) {
                return dir;
            }
        }
        return null;
    }

    private static ProcessBuilder buildProcessForScript(File workingDir, String scriptBaseName, List<String> args) {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            // Use .bat via cmd.exe
            command.add("cmd.exe");
            command.add("/c");
            command.add(scriptBaseName + ".bat");
            command.addAll(args);
        } else if (isUnixLike()) {
            // Use .sh via bash
            command.add("bash");
            command.add(scriptBaseName + ".sh");
            command.addAll(args);
        } else {
            // Default to Unix-like behavior
            command.add("bash");
            command.add(scriptBaseName + ".sh");
            command.addAll(args);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        // Merge PATH with workingDir and with detected yt-dlp dir fallback
        try {
            java.util.Map<String, String> env = pb.environment();
            String sep = File.pathSeparator;
            String existingPath = env.getOrDefault("PATH", "");
            StringBuilder newPath = new StringBuilder();
            // Prefer workingDir if provided
            if (workingDir != null) {
                pb.directory(workingDir);
                String homePath = workingDir.getAbsolutePath();
                if (homePath != null && !homePath.isEmpty()) {
                    newPath.append(homePath).append(sep);
                }
            }
            String detected = detectYtDlpDirFallback();
            if (detected != null && !detected.isEmpty()) {
                newPath.append(detected).append(sep);
            }
            newPath.append(existingPath);
            env.put("PATH", newPath.toString());
            // Also export YTDL_HOME for scripts
            if (workingDir != null) {
                env.putIfAbsent("YTDL_HOME", workingDir.getAbsolutePath());
            } else if (detected != null) {
                env.putIfAbsent("YTDL_HOME", detected);
            }
        } catch (Exception ignored) {
            // best-effort only
        }
        return pb;
    }

    public String downloadAudio(String videoUrl, String outputPath, String ytDlpHome) {
        try {
            // Parse the video ID from the URL
            String videoId = null;
            Pattern pattern = Pattern.compile("v=([\\w-]+)");
            String query = new URL(videoUrl).getQuery();
            if (query != null) {
                Matcher matcher = pattern.matcher(query);
                if (matcher.find()) {
                    videoId = matcher.group(1);
                }
            }
            if (videoId == null) {
                throw new IllegalArgumentException("Invalid YouTube URL: " + videoUrl);
            }

            String downloadFileName = videoId + ".mp3";

            File workDir = ytDlpHome != null ? new File(ytDlpHome) : null;
            List<String> args = new ArrayList<>();
            args.add(videoUrl);
            args.add(downloadFileName);

            ProcessBuilder processBuilder = buildProcessForScript(workDir, "run-yt-dlp", args);

            log.info("ytDlpHome: {}", ytDlpHome);
            if (processBuilder.directory() != null) {
                log.info("Working directory: {}", processBuilder.directory().getAbsolutePath());
            }
            log.info("Command: {}", String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();
            // Read both stdout and stderr to avoid blocking
            try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = out.readLine()) != null) {
                    log.info("[yt-dlp OUT] {}", line);
                }
                while ((line = err.readLine()) != null) {
                    log.warn("[yt-dlp ERR] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Failed to download audio. Exit code: " + exitCode);
            }

            return downloadFileName;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error downloading audio: " + e.getMessage(), e);
        }
    }

    public String downloadLatestVideo(String channelUrl, String ytDlpHome) {
        try {
            File workDir = ytDlpHome != null ? new File(ytDlpHome) : null;
            List<String> args = new ArrayList<>();
            args.add(channelUrl);

            ProcessBuilder processBuilder = buildProcessForScript(workDir, "run-yt-dlp-latest-video", args);

            if (processBuilder.directory() != null) {
                log.info("Working directory: {}", processBuilder.directory().getAbsolutePath());
            }
            log.info("Command: {}", String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();
            String latestVideoFileName = null;

            try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = out.readLine()) != null) {
                    log.info("[yt-dlp OUT] {}", line);
                    if (line.contains(".mp4")) {
                        latestVideoFileName = line.trim();
                        log.info("latestVideoFileName for mp4: {}", latestVideoFileName);
                    } else if (line.contains(".webm")) {
                        latestVideoFileName = line.trim();
                        log.info("latestVideoFileName for webm: {}", latestVideoFileName);
                    }
                }
                while ((line = err.readLine()) != null) {
                    log.warn("[yt-dlp ERR] {}", line);
                    // Capture info from error stream too, as yt-dlp sometimes writes to stderr
                    if (line.contains(".mp4")) {
                        latestVideoFileName = line.trim();
                    } else if (line.contains("already is in target format mp4")) {
                        latestVideoFileName = line.trim();
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.info("Exit code: {}", exitCode);
                throw new RuntimeException("Failed to download the latest video. Exit code: " + exitCode);
            }
            if (latestVideoFileName != null && latestVideoFileName.contains("already is in target format mp4")) {
                log.info("latestVideoFileName: {}", latestVideoFileName);
                latestVideoFileName = extractFileName(latestVideoFileName);
                log.info("Video is already in target format mp4. Extracting the file name from the message.");
            }
            return latestVideoFileName;
        } catch (IOException | InterruptedException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("already is in target format mp4")) {
                log.info("Video is already in target format mp4. Extracting the file name from the error message.");
                Pattern pattern = Pattern.compile("\"([^\"]+\\.mp4)\"");
                Matcher matcher = pattern.matcher(msg);
                if (matcher.find()) {
                    return matcher.group(1);
                } else {
                    throw new RuntimeException("Error extracting file name from the error message: " + msg, e);
                }
            } else {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Error downloading the latest video: " + msg, e);
            }
        }
    }

    public String extractFileName(String logMessage) {
        Pattern pattern = Pattern.compile("\\[VideoConvertor\\] Not converting media file \"([^\"]+\\.mp4)\"; already is in target format mp4");
        Matcher matcher = pattern.matcher(logMessage);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new IllegalArgumentException("Log message does not contain the expected format.");
        }
    }
}