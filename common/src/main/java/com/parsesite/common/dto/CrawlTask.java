package com.parsesite.common.dto;

import java.io.Serializable;

public class CrawlTask implements Serializable {
    private String url;
    private String kind;
    private String seedId;
    private int attempt;

    public CrawlTask() {
    }

    public CrawlTask(String url, String kind, String seedId, int attempt) {
        this.url = url;
        this.kind = kind;
        this.seedId = seedId;
        this.attempt = attempt;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSeedId() { return seedId; }
    public void setSeedId(String seedId) { this.seedId = seedId; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
}
