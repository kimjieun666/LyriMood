package com.jieun.lyrimood.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProjectRedirectController {

    @GetMapping("/projects/cos")
    public String redirectCosProject() {
        return "redirect:https://github.com/kimjieun666/Co-s_House";
    }
}

