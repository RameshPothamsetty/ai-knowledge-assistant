package com.ramesh.knowledge.web;

import com.ramesh.knowledge.embedding.EmbeddingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single page UI. Data is loaded from the REST API by the page's
 * script, so the UI and any future client share one contract.
 */
@Controller
public class HomeController {

    private final EmbeddingService embeddingService;

    public HomeController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("embeddingModel", embeddingService.modelName());
        return "index";
    }
}
