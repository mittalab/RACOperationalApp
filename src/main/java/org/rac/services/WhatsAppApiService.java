package org.rac.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public class WhatsAppApiService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppApiService.class);

    private static final String PHONE_ID = "1029511780256148";
    private static final String BEARER_TOKEN = "EAAUtFTmwoHUBRelo3CT301ebfjPuS1rojQZBZBrqxTswn6F5kETIxXQUm12uMm51aeMZBKbgzEQCVbb7hgihzRKXZBZCbnZAskw5nhRD7JMfXHIkjxrKzqZA2hq6mGMB2k0FRjcCS500ZBicxUryp53NyQuRDKJgrNPZCLkOrO80xarGYuQOzYs4pco3DTg9CNOyTHgZDZD";
    private static final String ADMIN_PHONE = "918527940091";
    private static final String BASE_URL = "https://graph.facebook.com/v19.0/";

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 4000};

    // WA error codes that are safe to retry
    private static final java.util.Set<Integer> RETRYABLE_WA_CODES = new java.util.HashSet<>(
            java.util.Arrays.asList(1, 2, 4, 80007, 131016, 131056));

    // WA error code for daily/tier send limit exceeded — non-retryable, stops the batch
    private static final int QUOTA_EXCEEDED_CODE = 131064;

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws IOException, InterruptedException;
    }

    public static class WhatsAppApiException extends RuntimeException {
        public final int statusCode;
        public final String responseBody;

        public WhatsAppApiException(int statusCode, String responseBody) {
            super("WhatsApp API error " + statusCode + ": " + responseBody);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
    }

    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String msg) { super(msg); }
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private <T> T executeWithRetry(ThrowingSupplier<T> action) throws IOException, InterruptedException {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (WhatsAppApiException e) {
                int waCode = parseWhatsAppErrorCode(e.responseBody);
                if (waCode == QUOTA_EXCEEDED_CODE) {
                    throw new QuotaExceededException(
                            "Daily/tier message limit reached (WA code 131064): " + e.responseBody);
                }
                boolean retryable = RETRYABLE_WA_CODES.contains(waCode)
                        || e.statusCode == 429
                        || e.statusCode >= 500;
                if (!retryable) throw e;
                lastException = e;
            }
            if (attempt < MAX_RETRIES) {
                long delay = RETRY_DELAYS_MS[attempt];
                logger.warn("WhatsApp API call failed, retrying in {}ms (attempt {}/{})", delay, attempt + 1, MAX_RETRIES);
                Thread.sleep(delay);
            }
        }
        if (lastException instanceof IOException) throw (IOException) lastException;
        if (lastException instanceof InterruptedException) throw (InterruptedException) lastException;
        throw new RuntimeException("WhatsApp API failed after " + MAX_RETRIES + " retries", lastException);
    }

    /**
     * Uploads a PNG file to the WhatsApp media endpoint and returns the media ID.
     */
    public String uploadMedia(File imageFile) throws IOException, InterruptedException {
        logger.info("Uploading media: {}", imageFile.getName());

        String boundary = "----FormBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] fileBytes = Files.readAllBytes(imageFile.toPath());
        byte[] body = buildMultipartBody(boundary, imageFile.getName(), fileBytes);

        return executeWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + PHONE_ID + "/media"))
                    .header("Authorization", "Bearer " + BEARER_TOKEN)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Media upload response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WhatsAppApiException(response.statusCode(), response.body());
            }

            String mediaId = extractJsonField(response.body(), "id");
            if (mediaId == null || mediaId.isEmpty()) {
                throw new WhatsAppApiException(response.statusCode(), "No 'id' in response: " + response.body());
            }
            logger.info("Media uploaded successfully, id={}", mediaId);
            return mediaId;
        });
    }

    /**
     * Checks the delivery status of a previously sent message.
     * Returns one of: accepted, sent, delivered, read, failed, or "unknown".
     */
    public String checkMessageStatus(String messageId) throws IOException, InterruptedException {
        logger.info("Checking status for message {}", messageId);
        return executeWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + messageId))
                    .header("Authorization", "Bearer " + BEARER_TOKEN)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Status check response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WhatsAppApiException(response.statusCode(), response.body());
            }
            String status = extractMessageStatus(response.body());
            return status != null ? status : "unknown";
        });
    }

    /**
     * Sends the sending_student_result template to a student's phone.
     * Phone must already include country code (e.g. "919811658385").
     * Returns the WhatsApp message ID for delivery tracking.
     */
    public String sendStudentResult(String toPhone, String mediaId, String studentName, String testDate)
            throws IOException, InterruptedException {
        logger.info("Sending student result to {}", toPhone);
        String json = "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":\"" + toPhone + "\","
                + "\"type\":\"template\","
                + "\"template\":{"
                + "\"name\":\"sending_student_result\","
                + "\"language\":{\"code\":\"en\"},"
                + "\"components\":["
                + "{\"type\":\"header\",\"parameters\":["
                + "{\"type\":\"image\",\"image\":{\"id\":\"" + mediaId + "\"}}"
                + "]},"
                + "{\"type\":\"body\",\"parameters\":["
                + "{\"type\":\"text\",\"parameter_name\":\"student_name\",\"text\":\"" + escapeJson(studentName) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"test_date\",\"text\":\"" + escapeJson(testDate) + "\"}"
                + "]}"
                + "]}}";
        return postMessage(json);
    }

    /**
     * Sends the result_summary template to the admin number.
     * Returns the WhatsApp message ID for delivery tracking.
     */
    public String sendResultSummary(String testName, String testDate, String className,
                                    String topicName, int count)
            throws IOException, InterruptedException {
        logger.info("Sending result summary to admin");
        String json = "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":\"" + ADMIN_PHONE + "\","
                + "\"type\":\"template\","
                + "\"template\":{"
                + "\"name\":\"result_summary\","
                + "\"language\":{\"code\":\"en\"},"
                + "\"components\":["
                + "{\"type\":\"body\",\"parameters\":["
                + "{\"type\":\"text\",\"parameter_name\":\"test_name\",\"text\":\"" + escapeJson(testName) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"test_date\",\"text\":\"" + escapeJson(testDate) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"class\",\"text\":\"" + escapeJson(className) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"topic_name\",\"text\":\"" + escapeJson(topicName) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"count\",\"text\":\"" + count + "\"}"
                + "]}"
                + "]}}";
        return postMessage(json);
    }

    /**
     * Sends the topper_result_template to the admin number with an image.
     * Returns the WhatsApp message ID for delivery tracking.
     */
    public String sendTopperResult(String mediaId, String testName, String testDate,
                                   String className, String topicName)
            throws IOException, InterruptedException {
        logger.info("Sending topper result to admin");
        String json = "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":\"" + ADMIN_PHONE + "\","
                + "\"type\":\"template\","
                + "\"template\":{"
                + "\"name\":\"topper_result_template\","
                + "\"language\":{\"code\":\"en\"},"
                + "\"components\":["
                + "{\"type\":\"header\",\"parameters\":["
                + "{\"type\":\"image\",\"image\":{\"id\":\"" + mediaId + "\"}}"
                + "]},"
                + "{\"type\":\"body\",\"parameters\":["
                + "{\"type\":\"text\",\"parameter_name\":\"test_name\",\"text\":\"" + escapeJson(testName) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"test_date\",\"text\":\"" + escapeJson(testDate) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"class\",\"text\":\"" + escapeJson(className) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"topic_name\",\"text\":\"" + escapeJson(topicName) + "\"}"
                + "]}"
                + "]}}";
        return postMessage(json);
    }

    /**
     * Sends the absent_summary template to the admin number with an image header.
     * Returns the WhatsApp message ID.
     */
    public String sendAbsentSummary(String mediaId, String testDate, String className, String topicName, String batch)
            throws IOException, InterruptedException {
        logger.info("Sending absent summary to admin");
        String json = "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":\"" + ADMIN_PHONE + "\","
                + "\"type\":\"template\","
                + "\"template\":{"
                + "\"name\":\"absent_summary\","
                + "\"language\":{\"code\":\"en\"},"
                + "\"components\":["
                + "{\"type\":\"header\",\"parameters\":["
                + "{\"type\":\"image\",\"image\":{\"id\":\"" + mediaId + "\"}}"
                + "]},"
                + "{\"type\":\"body\",\"parameters\":["
                + "{\"type\":\"text\",\"parameter_name\":\"test_date\",\"text\":\"" + escapeJson(testDate) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"class\",\"text\":\"" + escapeJson(className) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"topic_name\",\"text\":\"" + escapeJson(topicName) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"batch\",\"text\":\"" + escapeJson(batch) + "\"}"
                + "]}"
                + "]}}";
        return postMessage(json);
    }

    /** Posts a message and returns the WhatsApp message ID from the response. */
    private String postMessage(String json) throws IOException, InterruptedException {
        logger.info("Sending JSON to WhatsApp API: {}", json);
        return executeWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + PHONE_ID + "/messages"))
                    .header("Authorization", "Bearer " + BEARER_TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Message send response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.error("Error while sending JSON to WhatsApp API: {}, {}", response.statusCode(), response.body());
                throw new WhatsAppApiException(response.statusCode(), response.body());
            }
            String msgId = extractMessageId(response.body());
            logger.info("Message sent, id={}", msgId);
            return msgId;
        });
    }

    /**
     * Extracts the message ID from a send-message response.
     * Handles: {"messages":[{"id":"wamid.xxx",...}],...}
     */
    private String extractMessageId(String json) {
        if (json == null) return null;
        int messagesIdx = json.indexOf("\"messages\"");
        if (messagesIdx < 0) return null;
        int bracketStart = json.indexOf('[', messagesIdx);
        if (bracketStart < 0) return null;
        int braceStart = json.indexOf('{', bracketStart);
        if (braceStart < 0) return null;
        int braceEnd = json.indexOf('}', braceStart);
        if (braceEnd < 0) return null;
        return extractJsonField(json.substring(braceStart, braceEnd + 1), "id");
    }

    /**
     * Extracts message_status from a status-check response.
     * Tries messages[0].message_status, then top-level message_status / status.
     */
    private String extractMessageStatus(String json) {
        if (json == null) return null;
        // Try inside messages array first
        int messagesIdx = json.indexOf("\"messages\"");
        if (messagesIdx >= 0) {
            int bracketStart = json.indexOf('[', messagesIdx);
            if (bracketStart >= 0) {
                int braceStart = json.indexOf('{', bracketStart);
                if (braceStart >= 0) {
                    int braceEnd = json.indexOf('}', braceStart);
                    if (braceEnd >= 0) {
                        String first = json.substring(braceStart, braceEnd + 1);
                        String s = extractJsonField(first, "message_status");
                        if (s != null) return s;
                    }
                }
            }
        }
        // Fall back to top-level fields
        String s = extractJsonField(json, "message_status");
        if (s != null) return s;
        return extractJsonField(json, "status");
    }

    private byte[] buildMultipartBody(String boundary, String filename, byte[] fileBytes) {
        String CRLF = "\r\n";
        String dash = "--";

        StringBuilder sb = new StringBuilder();
        // Part 1: messaging_product
        sb.append(dash).append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"messaging_product\"").append(CRLF).append(CRLF);
        sb.append("whatsapp").append(CRLF);
        // Part 2: type
        sb.append(dash).append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"type\"").append(CRLF).append(CRLF);
        sb.append("image/png").append(CRLF);
        // Part 3: file header
        sb.append(dash).append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"").append(CRLF);
        sb.append("Content-Type: image/png").append(CRLF).append(CRLF);

        byte[] prefix = sb.toString().getBytes(StandardCharsets.UTF_8);
        String suffix = CRLF + dash + boundary + "--" + CRLF;
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[prefix.length + fileBytes.length + suffixBytes.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(fileBytes, 0, result, prefix.length, fileBytes.length);
        System.arraycopy(suffixBytes, 0, result, prefix.length + fileBytes.length, suffixBytes.length);
        return result;
    }

    /**
     * Sends the no_absent template to the admin number.
     * Returns the WhatsApp message ID.
     */
    public String sendNoAbsentSummary(String testDate, String className, String topicName, String batch)
            throws IOException, InterruptedException {
        logger.info("Sending no_absent summary to admin");
        String json = "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":\"" + ADMIN_PHONE + "\","
                + "\"type\":\"template\","
                + "\"template\":{"
                + "\"name\":\"no_absent\","
                + "\"language\":{\"code\":\"en\"},"
                + "\"components\":["
                + "{\"type\":\"body\",\"parameters\":["
                + "{\"type\":\"text\",\"parameter_name\":\"test_date\",\"text\":\"" + escapeJson(testDate) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"class\",\"text\":\"" + escapeJson(className) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"topic_name\",\"text\":\"" + escapeJson(topicName) + "\"},"
                + "{\"type\":\"text\",\"parameter_name\":\"batch\",\"text\":\"" + escapeJson(batch) + "\"}"
                + "]}"
                + "]}}";
        return postMessage(json);
    }

    /**
     * Extracts the WhatsApp error code from {"error": {"code": N, ...}}.
     * Returns -1 if not found.
     */
    private int parseWhatsAppErrorCode(String json) {
        if (json == null) return -1;
        // Find "error" object first, then "code" within it
        int errorIdx = json.indexOf("\"error\"");
        if (errorIdx < 0) return -1;
        int openBrace = json.indexOf('{', errorIdx);
        if (openBrace < 0) return -1;
        int closeBrace = json.indexOf('}', openBrace);
        String errorBlock = json.substring(openBrace, closeBrace + 1);
        String codeKey = "\"code\"";
        int keyIdx = errorBlock.indexOf(codeKey);
        if (keyIdx < 0) return -1;
        int colonIdx = errorBlock.indexOf(':', keyIdx + codeKey.length());
        if (colonIdx < 0) return -1;
        int start = colonIdx + 1;
        while (start < errorBlock.length() && Character.isWhitespace(errorBlock.charAt(start))) start++;
        int end = start;
        while (end < errorBlock.length() && Character.isDigit(errorBlock.charAt(end))) end++;
        if (start == end) return -1;
        try { return Integer.parseInt(errorBlock.substring(start, end)); }
        catch (NumberFormatException e) { return -1; }
    }

    /** Minimal JSON string escaping. */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** Extracts a top-level string field from a simple JSON response without a full parser. */
    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }
}
