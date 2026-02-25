package com.example.dueltower.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/ui", "/ui/",
            "/ui/{path:[^\\.]*}",
            "/ui/**/{path:[^\\.]*}"
    })
    public String forwardUi() {
        return "forward:/ui/index.html";
    }
}