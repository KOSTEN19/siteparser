package com.parsesite.common.dto;

import java.io.Serializable;

public class IndexTask implements Serializable {
    private Long rawDocumentId;
    private String url;

    public IndexTask() {
    }

    public IndexTask(Long rawDocumentId, String url) {
        this.rawDocumentId = rawDocumentId;
        this.url = url;
    }

    public Long getRawDocumentId() { return rawDocumentId; }
    public void setRawDocumentId(Long rawDocumentId) { this.rawDocumentId = rawDocumentId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
