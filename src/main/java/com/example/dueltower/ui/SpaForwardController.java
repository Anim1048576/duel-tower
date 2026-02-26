package com.example.dueltower.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/ui", "/ui/"})
    public String uiRoot() {
        return "forward:/ui/index.html";
    }

    // /ui/lobby 같은 1-depth 라우트
    @GetMapping("/ui/{path:(?!assets$)[^\\.]+}")
    public String ui1() {
        return "forward:/ui/index.html";
    }

    // /ui/run/123/combat 같은 N-depth 라우트
    @GetMapping("/ui/{path:(?!assets$)[^\\.]+}/{*rest}")
    public String uiN() {
        return "forward:/ui/index.html";
    }
}