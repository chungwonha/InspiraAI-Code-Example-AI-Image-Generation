package com.chung.ai.software.inspiraai;

import java.util.ArrayList;
import java.util.List;

public class FileLists {
    private List<File> audioFiles;
    private List<File> imageFiles;
    private List<File> videoFiles;

    public FileLists() {
        this.audioFiles = new ArrayList<>();
        this.imageFiles = new ArrayList<>();
        this.videoFiles = new ArrayList<>();
    }

    public List<File> getAudioFiles() {
        return audioFiles;
    }

    public List<File> getImageFiles() {
        return imageFiles;
    }

    public List<File> getVideoFiles() {
        return videoFiles;
    }

    public void addAudioFile(String name, String url) {
        audioFiles.add(new File(name, url));
    }

    public void addImageFile(String name, String url) {
        imageFiles.add(new File(name, url));
    }

    public void addVideoFile(String name, String url) {
        videoFiles.add(new File(name, url));
    }

    public static class File {
        private final String name;
        private final String url;

        public File(String name, String url) {
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
}
