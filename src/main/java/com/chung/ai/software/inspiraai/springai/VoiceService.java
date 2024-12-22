package com.chung.ai.software.inspiraai.springai;

import org.springframework.core.io.Resource;

public interface VoiceService {

    String transcribe(Resource audioFileResource);

    Resource textToSpeech(String text);

}

