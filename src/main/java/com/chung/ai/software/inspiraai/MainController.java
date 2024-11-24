package com.chung.ai.software.inspiraai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiImageModel;

import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_3;


@Controller
@Slf4j
public class MainController {


    @Value("${openai.api.key}")
    private String openaiApiKey;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/generateImage")
    public String generateImage(@RequestParam("prompt") String prompt, Model model) {
        ImageModel imageModel = OpenAiImageModel.builder()
                .apiKey(openaiApiKey)
                .modelName(DALL_E_3)
                .build();

        Response<Image> response = imageModel.generate(prompt);
        String imageUrl = response.content().url().toString();
        log.info("Generated image URL: {}", imageUrl);
        model.addAttribute("imageUrl", imageUrl);
        return "result";
    }

}
