package com.parsesite.common.dto;

import java.io.Serializable;

public class AnalyzeTask implements Serializable {
    private String documentId;
    private String text;

    public AnalyzeTask() {
    }

    public AnalyzeTask(String documentId, String text) {
        this.documentId = documentId;
        this.text = text;
    }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
