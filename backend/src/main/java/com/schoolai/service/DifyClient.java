package com.schoolai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolai.config.DifyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.schoolai.model.vo.DifyUploadedFileVO;
import okhttp3.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyClient {

    private final DifyConfig difyConfig;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void ensureDocumentUploadEnabled(String userId, String fileName, long fileSize) throws IOException {
        Request request = new Request.Builder()
                .url(apiUrl("/parameters?user=" + java.net.URLEncoder.encode(userId, java.nio.charset.StandardCharsets.UTF_8)))
                .addHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String payload = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("Dify 应用参数读取失败：" + response.code());
            }
            JsonNode fileUpload = objectMapper.readTree(payload).path("file_upload");
            boolean enabled = fileUpload.path("enabled").asBoolean(false);
            boolean documentAllowed = containsIgnoreCase(fileUpload.path("allowed_file_types"), "document");
            boolean localFileAllowed = containsIgnoreCase(fileUpload.path("allowed_file_upload_methods"), "local_file");
            String extension = fileName != null && fileName.contains(".")
                    ? fileName.substring(fileName.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT)
                    : "";
            boolean extensionAllowed = containsIgnoreCase(fileUpload.path("allowed_file_extensions"), extension);
            long sizeLimitMb = fileUpload.path("fileUploadConfig").path("file_size_limit").asLong(0);
            boolean sizeAllowed = sizeLimitMb <= 0 || fileSize <= sizeLimitMb * 1024 * 1024;
            if (!enabled || !documentAllowed || !localFileAllowed) {
                throw new IOException("Dify 应用未启用文档上传能力");
            }
            if (!extensionAllowed || !sizeAllowed) {
                throw new IOException("Dify 应用不允许该文件格式或大小");
            }
        }
    }

    public DifyUploadedFileVO uploadFile(MultipartFile file, String userId) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String declaredType = file.getContentType();
        MediaType mediaType = declaredType == null || declaredType.isBlank()
                ? null
                : MediaType.parse(declaredType);
        RequestBody fileBody = RequestBody.create(file.getBytes(), mediaType);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .addFormDataPart("user", userId)
                .build();
        Request request = new Request.Builder()
                .url(apiUrl("/files/upload"))
                .addHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String payload = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("Dify 文件上传失败：" + response.code() + " " + payload);
            }
            JsonNode node = objectMapper.readTree(payload);
            DifyUploadedFileVO result = new DifyUploadedFileVO();
            result.setId(node.path("id").asText());
            result.setName(node.path("name").asText(fileName));
            result.setSize(node.path("size").asLong(file.getSize()));
            result.setMimeType(node.path("mime_type").asText(file.getContentType()));
            if (result.getId() == null || result.getId().isBlank()) {
                throw new IOException("Dify 文件上传响应缺少文件ID");
            }
            return result;
        }
    }

    /**
     * 流式调用 Dify 聊天接口。
     *
     * @param query          用户问题
     * @param userId         用户唯一标识
     * @param conversationId 已有会话ID（空表示新对话）
     * @param onChunk        每个文本片段回调
     */
    public void chatStream(String query, String userId, String conversationId,
                            List<Map<String, String>> files,
                            StringBuffer conversationIdOut,
                            java.util.function.Consumer<String> onSources,
                            java.util.function.Consumer<String> onChunk) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("inputs", new HashMap<>());
        body.put("query", query);
        body.put("response_mode", "streaming");
        body.put("user", userId);
        body.put("conversation_id", conversationId == null ? "" : conversationId);
        if (files != null && !files.isEmpty()) {
            body.put("files", new ArrayList<>(files));
        }

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(apiUrl("/chat-messages"))
                .addHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() == null ? "" : response.body().string();
                throw new IOException("Dify 调用失败：" + response.code() + " " + errorBody);
            }
            ResponseBody rb = response.body();
            if (rb == null) {
                throw new IOException("Dify 响应为空");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(rb.byteStream()))) {
                String line;
                boolean completed = false;
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
                        } else if ("node_finished".equals(event)
                                && "knowledge-retrieval".equals(node.path("data").path("node_type").asText())) {
                            JsonNode result = node.path("data").path("outputs").path("result");
                            if (result.isArray() && result.size() > 0) {
                                List<Map<String, Object>> sources = new ArrayList<>();
                                for (JsonNode item : result) {
                                    JsonNode metadata = item.path("metadata");
                                    Map<String, Object> source = new HashMap<>();
                                    source.put("documentName", metadata.path("document_name").asText(item.path("title").asText("未知文档")));
                                    source.put("score", metadata.path("score").asDouble(0));
                                    source.put("content", item.path("content").asText(""));
                                    sources.add(source);
                                }
                                onSources.accept(objectMapper.writeValueAsString(sources));
                            }
                        } else if ("message_end".equals(event) || "agent_end".equals(event)) {
                            completed = true;
                            String cid = node.path("conversation_id").asText();
                            if (!cid.isEmpty()) {
                                conversationIdOut.replace(0, conversationIdOut.length(), cid);
                            }
                        } else if ("error".equals(event)) {
                            throw new IOException("Dify 流式响应失败");
                        }
                    } catch (IOException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IOException("Dify 流式响应解析失败", e);
                    }
                }
                if (!completed) {
                    throw new IOException("Dify 流式响应未正常结束");
                }
            }
        }
    }

    private boolean containsIgnoreCase(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equalsIgnoreCase(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private String apiUrl(String path) {
        String base = difyConfig.getApiBase();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
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
