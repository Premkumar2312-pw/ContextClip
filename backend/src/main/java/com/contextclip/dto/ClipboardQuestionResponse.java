package com.contextclip.dto;

import java.util.List;

public record ClipboardQuestionResponse(String answer, List<Long> sources) {}
