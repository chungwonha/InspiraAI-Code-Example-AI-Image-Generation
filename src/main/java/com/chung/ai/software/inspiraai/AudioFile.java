package com.chung.ai.software.inspiraai;

public class AudioFile {
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
