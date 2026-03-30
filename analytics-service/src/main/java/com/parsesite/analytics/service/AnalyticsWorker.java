package com.parsesite.analytics.service;

import com.parsesite.common.QueueNames;
import com.parsesite.common.dto.AnalyzeTask;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalyticsWorker {
    private static final Set<String> POSITIVE = new HashSet<String>(Arrays.asList("good", "great", "success", "рост", "победа"));
    private static final Set<String> NEGATIVE = new HashSet<String>(Arrays.asList("bad", "crisis", "fail", "падение", "кризис"));
    private final RestHighLevelClient esClient;

    public AnalyticsWorker(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    @RabbitListener(queues = QueueNames.ANALYZE_QUEUE)
    public void analyze(AnalyzeTask task) throws Exception {
        String text = task.getText() == null ? "" : task.getText();
        String sentiment = sentiment(text);
        String summary = summarize(text);
        String topic = classifyTopic(text);
        String simhash = Integer.toHexString(text.hashCode());

        Map<String, Object> doc = new HashMap<String, Object>();
        doc.put("sentiment", sentiment);
        doc.put("summary", summary);
        doc.put("topic", topic);
        doc.put("simhash", simhash);
        esClient.update(new UpdateRequest("documents", task.getDocumentId()).doc(doc), RequestOptions.DEFAULT);
    }

    private String sentiment(String text) {
        int score = 0;
        for (String token : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (POSITIVE.contains(token)) score++;
            if (NEGATIVE.contains(token)) score--;
        }
        if (score > 0) return "positive";
        if (score < 0) return "negative";
        return "neutral";
    }

    private String summarize(String text) {
        if (text.length() <= 280) {
            return text;
        }
        return text.substring(0, 280);
    }

    private String classifyTopic(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("market") || t.contains("econom")) return "economy";
        if (t.contains("sport") || t.contains("match")) return "sport";
        if (t.contains("war") || t.contains("security")) return "security";
        return "general";
    }

}
