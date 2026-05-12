package com.example.dueltower.lab.api;

import com.example.dueltower.lab.dto.LabDiceRequest;
import com.example.dueltower.lab.dto.LabDiceResponse;
import com.example.dueltower.lab.service.LabDiceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab")
public class LabDiceController {

    private final LabDiceService labDiceService;

    public LabDiceController(LabDiceService labDiceService) {
        this.labDiceService = labDiceService;
    }

    @PostMapping("/dice")
    public LabDiceResponse calculate(@RequestBody(required = false) LabDiceRequest request) {
        return labDiceService.calculate(request);
    }
}
