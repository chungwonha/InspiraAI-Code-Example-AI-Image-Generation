package com.chung.ai.software.inspiraai.springai;

import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.speech.SpeechModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class OpenAiVoiceService implements VoiceService {

    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final SpeechModel speechModel;

    @Override
    public Resource textToSpeech(String text) {
        byte[] speechBytes = speechModel.call(text);
        return new ByteArrayResource(speechBytes);
    }

    public OpenAiVoiceService(
            OpenAiAudioTranscriptionModel transcriptionModel,
            SpeechModel speechModel) {
        this.transcriptionModel = transcriptionModel;
        this.speechModel = speechModel;
    }

    @Override
    public String transcribe(Resource audioFileResource) {
        return transcriptionModel.call(audioFileResource); // <2>
    }
}
