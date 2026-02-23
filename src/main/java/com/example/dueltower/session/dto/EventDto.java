package com.example.dueltower.session.dto;

import java.util.Map;

public record EventDto(String type, Map<String, Object> payload) {}
