package com.example.dueltower.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui-legacy")
public class UiController {

    @GetMapping({"", "/"})
    public String home() {
        return "ui/home";
    }

    @GetMapping("/lobby")
    public String lobby() {
        return "ui/lobby";
    }

    @GetMapping("/presets")
    public String presets() {
        return "ui/presets";
    }

    @GetMapping("/node")
    public String node() {
        return "ui/node";
    }

    @GetMapping("/combat")
    public String combat() {
        return "ui/combat";
    }
}
