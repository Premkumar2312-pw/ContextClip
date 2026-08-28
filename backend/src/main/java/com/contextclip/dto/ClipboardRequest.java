package com.contextclip.dto;

public class ClipboardRequest {
    private String content;

    public ClipboardRequest() {
    }

    public ClipboardRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
