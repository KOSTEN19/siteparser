package com.parsesite.common.dto;

import java.io.Serializable;

public class CrawlTask implements Serializable {
    private String url;
    private int depth;
    private String source;

    public CrawlTask() {
    }

    public CrawlTask(String url, int depth, String source) {
        this.url = url;
        this.depth = depth;
        this.source = source;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
