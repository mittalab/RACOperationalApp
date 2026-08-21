package org.rac.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WamidStatusService {

    private static final Logger logger = LoggerFactory.getLogger(WamidStatusService.class);
    private static final String BATCH_URL = "https://webhook.rankachieversclasses.in/status/batch";
    private static final int BATCH_LIMIT = 100;

    public record WamidStatus(
            String wamid,
            String status,
            String recipientId,
            Long sentAt,
            Long deliveredAt,
            Long readAt,
            Long failedAt,
            Integer errorCode,
            String errorTitle,
            Long updatedAt
    ) {}

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public Map<String, WamidStatus> checkBatch(List<String> wamids) throws IOException, InterruptedException {
        Map<String, WamidStatus> results = new LinkedHashMap<>();
        for (int offset = 0; offset < wamids.size(); offset += BATCH_LIMIT) {
            List<String> chunk = wamids.subList(offset, Math.min(offset + BATCH_LIMIT, wamids.size()));
            results.putAll(sendBatchRequest(chunk));
        }
        return results;
    }

    public WamidStatus checkSingle(String wamid) throws IOException, InterruptedException {
        return checkBatch(List.of(wamid)).get(wamid);
    }

    private Map<String, WamidStatus> sendBatchRequest(List<String> wamids) throws IOException, InterruptedException {
        String body = buildRequestBody(wamids);
        logger.info("Sending batch status request for {} wamids to {}", wamids.size(), BATCH_URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BATCH_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new IOException("Cannot reach status server (" + BATCH_URL + "): " + e.getMessage(), e);
        }
        logger.info("Batch status response {}: {}", response.statusCode(), response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Batch status API error " + response.statusCode() + ": " + response.body());
        }

        return parseResponse(response.body());
    }

    private String buildRequestBody(List<String> wamids) {
        StringBuilder sb = new StringBuilder("{\"wamids\":[");
        for (int i = 0; i < wamids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(wamids.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private Map<String, WamidStatus> parseResponse(String json) {
        Map<String, WamidStatus> results = new LinkedHashMap<>();
        if (json == null) return results;

        int resultsIdx = json.indexOf("\"results\"");
        if (resultsIdx < 0) return results;
        int arrayStart = json.indexOf('[', resultsIdx);
        if (arrayStart < 0) return results;

        int pos = arrayStart + 1;
        while (pos < json.length()) {
            while (pos < json.length() && (json.charAt(pos) == ',' || Character.isWhitespace(json.charAt(pos)))) pos++;
            if (pos >= json.length() || json.charAt(pos) == ']') break;
            if (json.charAt(pos) != '{') break;

            int braceDepth = 0, objStart = pos, objEnd = -1;
            for (int i = pos; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') braceDepth++;
                else if (c == '}') { braceDepth--; if (braceDepth == 0) { objEnd = i; break; } }
            }
            if (objEnd < 0) break;

            String obj = json.substring(objStart, objEnd + 1);
            WamidStatus ws = parseResultObject(obj);
            if (ws != null) results.put(ws.wamid(), ws);
            pos = objEnd + 1;
        }
        return results;
    }

    private WamidStatus parseResultObject(String obj) {
        String wamid = extractString(obj, "wamid");
        if (wamid == null) return null;
        String status = extractString(obj, "status");
        if (status == null) status = "pending";
        return new WamidStatus(
                wamid, status,
                extractString(obj, "recipient_id"),
                extractLong(obj, "sent_at"),
                extractLong(obj, "delivered_at"),
                extractLong(obj, "read_at"),
                extractLong(obj, "failed_at"),
                extractInt(obj, "error_code"),
                extractString(obj, "error_title"),
                extractLong(obj, "updated_at")
        );
    }

    private String extractString(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;
        int pos = colonIdx + 1;
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        if (pos >= json.length() || json.startsWith("null", pos)) return null;
        if (json.charAt(pos) != '"') return null;
        int quoteEnd = json.indexOf('"', pos + 1);
        if (quoteEnd < 0) return null;
        return json.substring(pos + 1, quoteEnd);
    }

    private Long extractLong(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;
        int pos = colonIdx + 1;
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        if (pos >= json.length() || json.startsWith("null", pos)) return null;
        int end = pos;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == pos) return null;
        try { return Long.parseLong(json.substring(pos, end)); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer extractInt(String json, String field) {
        Long val = extractLong(json, field);
        return val != null ? val.intValue() : null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
