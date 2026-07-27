package com.schoolai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolai.config.DifyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyClient {

    private final DifyConfig difyConfig;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式调用 Dify 聊天接口。
     *
     * @param query          用户问题
     * @param userId         用户唯一标识
     * @param conversationId 已有会话ID（空表示新对话）
     * @param onChunk        每个文本片段回调
     */
    public void chatStream(String query, String userId, String conversationId,
                            StringBuffer conversationIdOut,
                            java.util.function.Consumer<String> onChunk) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("inputs", new HashMap<>());
        body.put("query", query);
        body.put("response_mode", "streaming");
        body.put("user", userId);
        body.put("conversation_id", conversationId == null ? "" : conversationId);

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(difyConfig.getApiBase() + "/chat-messages")
                .addHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Dify 调用失败：" + response.code() + " " + response.message());
            }
            ResponseBody rb = response.body();
            if (rb == null) {
                throw new IOException("Dify 响应为空");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(rb.byteStream()))) {
                String line;
                StringBuilder answerBuffer = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty()) continue;
                    try {
                        JsonNode node = objectMapper.readTree(payload);
                        String event = node.path("event").asText();
                        if ("message".equals(event) || "agent_message".equals(event)) {
                            String ans = node.path("answer").asText();
                            if (!ans.isEmpty()) {
                                answerBuffer.append(ans);
                                String visible = removeThinking(answerBuffer);
                                if (!visible.isEmpty()) {
                                    onChunk.accept(visible);
                                    answerBuffer.setLength(0);
                                }
                            }
                        } else if ("message_end".equals(event) || "agent_end".equals(event)) {
                            String cid = node.path("conversation_id").asText();
                            if (!cid.isEmpty()) {
                                conversationIdOut.replace(0, conversationIdOut.length(), cid);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析 Dify 流失败：{}", e.getMessage());
                    }
                }
            }
        }
    }

    private String removeThinking(StringBuilder buffer) {
        String value = buffer.toString();
        int start = value.indexOf("<think>");
        if (start < 0) {
            return value;
        }
        int end = value.indexOf("</think>", start + 7);
        if (end < 0) {
            return "";
        }
        return value.substring(0, start) + value.substring(end + 8);
    }
}