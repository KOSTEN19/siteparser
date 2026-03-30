package com.parsesite.api.controller;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class SearchController {
    private final RestHighLevelClient client;

    public SearchController(RestHighLevelClient client) {
        this.client = client;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam("q") String q, @RequestParam(value = "size", defaultValue = "10") int size) throws Exception {
        SearchSourceBuilder source = new SearchSourceBuilder()
                .query(QueryBuilders.multiMatchQuery(q, "content", "summary", "entities", "topic"))
                .size(size);
        SearchRequest request = new SearchRequest("documents").source(source);
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("hits", response.getHits().getHits());
        result.put("total", response.getHits().getTotalHits().value);
        return result;
    }

    @GetMapping("/analytics/topic-count")
    public Map<String, Object> topicCount() {
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("message", "Use Kibana Lens for topic histogram over field `topic`");
        return out;
    }
}
