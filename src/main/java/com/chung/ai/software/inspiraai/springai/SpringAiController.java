package com.chung.ai.software.inspiraai.springai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpringAiController {
    @Autowired
    private VoiceService voiceService;

    @PostMapping(path="/audioAsk", produces = "audio/mpeg")
    public Resource audioAskAudioResponse(@RequestParam("scripts") String scripts) {

        //  String transcription = voiceService.transcribe(blob.getResource());
//        Question transcribedQuestion = new Question(game, transcription);
//        Answer answer = boardGameService.askQuestion(
//                transcribedQuestion, conversationId);
        return voiceService.textToSpeech(scripts);
    }
}
