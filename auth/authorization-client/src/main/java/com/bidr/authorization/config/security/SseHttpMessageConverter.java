package com.bidr.authorization.config.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 全面的SSE消息转换器，支持：
 * - 所有Java对象类型（自动序列化）
 * - SSE规范所有字段（id, event, retry, data, comment）
 * - 多行数据
 * - 自动重连配置
 * - 自定义序列化
 * - 事件流分块发送
 * - 心跳机制
 *
 * @author Sharp
 */
public class SseHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    public static final MediaType TEXT_EVENT_STREAM = new MediaType("text", "event-stream");

    // SSE规范字段
    private static final String ID_FIELD = "id";
    private static final String EVENT_FIELD = "event";
    private static final String RETRY_FIELD = "retry";
    private static final String DATA_FIELD = "data";
    private static final String COMMENT_PREFIX = ":";

    private static final String FIELD_END = "\n";
    private static final String EVENT_END = "\n\n";
    private static final long DEFAULT_RETRY_DELAY = 3000L;

    private final ObjectMapper objectMapper;
    private final AtomicInteger eventIdCounter = new AtomicInteger(0);
    private long defaultRetryDelay = DEFAULT_RETRY_DELAY;
    private boolean includeNullValues = false;
    private boolean sendHeartbeats = true;
    private long heartbeatInterval = 15000;
    private String defaultEventName = "message";
    private Function<Object, String> customSerializer;

    public SseHttpMessageConverter() {
        super(TEXT_EVENT_STREAM);
        this.objectMapper = createDefaultObjectMapper();
    }

    private ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    public SseHttpMessageConverter(ObjectMapper objectMapper) {
        super(TEXT_EVENT_STREAM);
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }

    public static SseEventBuilder event() {
        return new SseEventBuilder();
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true; // 支持所有类型
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) {
        throw new UnsupportedOperationException("SSE converter does not support reading");
    }

    // ========== 写入方法 ==========

    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage) throws IOException {
        try (OutputStream outputStream = outputMessage.getBody();
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            // 处理集合/流类型
            if (object instanceof Iterable) {
                writeIterable((Iterable<?>) object, writer);
            } else if (object instanceof Iterator) {
                writeIterator((Iterator<?>) object, writer);
            } else if (object instanceof Stream) {
                try (Stream<?> stream = (Stream<?>) object) {
                    writeStream(stream, writer);
                }
            } else if (object instanceof SseEvent) {
                writeSseEvent((SseEvent) object, writer);
            } else {
                // 单个对象作为单个事件发送
                writeSingleEvent(object, writer);
            }

            // 发送结束标识
            writer.write(EVENT_END);
            writer.flush();
        }
    }

    private void writeIterable(Iterable<?> iterable, BufferedWriter writer) throws IOException {
        for (Object item : iterable) {
            writeEventItem(item, writer);
            writer.flush();
        }
    }

    private void writeIterator(Iterator<?> iterator, BufferedWriter writer) throws IOException {
        while (iterator.hasNext()) {
            writeEventItem(iterator.next(), writer);
            writer.flush();
        }
    }

    private void writeStream(Stream<?> stream, BufferedWriter writer) {
        final long[] lastWriteTime = {System.currentTimeMillis()};

        stream.forEach(item -> {
            try {
                // 发送心跳（如果需要）
                sendHeartbeatIfNeeded(writer, lastWriteTime[0]);

                writeEventItem(item, writer);
                writer.flush();
                lastWriteTime[0] = System.currentTimeMillis();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void writeSseEvent(SseEvent event, BufferedWriter writer) throws IOException {
        // 注释
        if (event.getComment() != null) {
            writer.write(COMMENT_PREFIX + event.getComment() + FIELD_END);
        }

        // 事件ID
        if (event.getId() != null) {
            writer.write(ID_FIELD + ": " + event.getId() + FIELD_END);
        } else if (isGenerateAutoIds()) {
            writer.write(ID_FIELD + ": " + generateEventId() + FIELD_END);
        }

        // 事件类型
        String eventName = event.getEvent() != null ? event.getEvent() : defaultEventName;
        writer.write(EVENT_FIELD + ": " + eventName + FIELD_END);

        // 重连时间
        if (event.getRetry() > 0) {
            writer.write(RETRY_FIELD + ": " + event.getRetry() + FIELD_END);
        } else if (defaultRetryDelay > 0) {
            writer.write(RETRY_FIELD + ": " + defaultRetryDelay + FIELD_END);
        }

        // 数据部分
        writeDataContent(event.getData(), writer);

        // 事件结束
        writer.write(FIELD_END);
    }

    private void writeSingleEvent(Object data, BufferedWriter writer) throws IOException {
        writeSseEvent(new SseEvent(null, defaultEventName, data, defaultRetryDelay), writer);
    }

    private void writeEventItem(Object item, BufferedWriter writer) throws IOException {
        if (item instanceof SseEvent) {
            writeSseEvent((SseEvent) item, writer);
        } else {
            writeSingleEvent(item, writer);
        }
    }

    // ========== 辅助方法 ==========

    private void writeDataContent(Object data, BufferedWriter writer) throws IOException {
        if (data == null) {
            if (includeNullValues) {
                writer.write(DATA_FIELD + ": null" + FIELD_END);
            }
            return;
        }

        String content;
        if (data instanceof String) {
            content = (String) data;
        } else if (customSerializer != null) {
            content = customSerializer.apply(data);
        } else {
            try {
                content = objectMapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                throw new IOException("Failed to serialize data to JSON", e);
            }
        }

        // 处理多行数据
        try (StringReader reader = new StringReader(content);
             BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                writer.write(DATA_FIELD + ": " + line + FIELD_END);
            }
        }
    }

    private void sendHeartbeatIfNeeded(BufferedWriter writer, long lastWriteTime) throws IOException {
        if (sendHeartbeats && (System.currentTimeMillis() - lastWriteTime) > heartbeatInterval) {
            writer.write(COMMENT_PREFIX + " heartbeat" + FIELD_END);
            writer.write(FIELD_END);
            writer.flush();
        }
    }

    private String generateEventId() {
        return String.valueOf(eventIdCounter.incrementAndGet());
    }

    // ========== 配置方法 ==========

    private boolean isGenerateAutoIds() {
        return eventIdCounter.get() > 0;
    }

    public void setDefaultRetryDelay(long delayMs) {
        this.defaultRetryDelay = delayMs;
    }

    public void setIncludeNullValues(boolean include) {
        this.includeNullValues = include;
    }

    public void setHeartbeatEnabled(boolean enabled) {
        this.sendHeartbeats = enabled;
    }

    public void setHeartbeatInterval(long intervalMs) {
        this.heartbeatInterval = intervalMs;
    }

    public void setDefaultEventName(String name) {
        this.defaultEventName = Objects.requireNonNull(name);
    }

    // ========== SSE事件封装类 ==========

    public void setCustomSerializer(Function<Object, String> serializer) {
        this.customSerializer = serializer;
    }

    // ========== 构建器模式 ==========

    public static class SseEvent {
        private final String id;
        private final String event;
        private final Object data;
        private final long retry;
        private final String comment;

        public SseEvent(String id, String event, Object data, long retry) {
            this(id, event, data, retry, null);
        }

        public SseEvent(String id, String event, Object data, long retry, String comment) {
            this.id = id;
            this.event = event;
            this.data = data;
            this.retry = retry;
            this.comment = comment;
        }

        public String getId() {
            return id;
        }

        public String getEvent() {
            return event;
        }

        public Object getData() {
            return data;
        }

        public long getRetry() {
            return retry;
        }

        public String getComment() {
            return comment;
        }
    }

    public static class SseEventBuilder {
        private String id;
        private String event;
        private Object data;
        private long retry = -1;
        private String comment;

        public SseEventBuilder id(String id) {
            this.id = id;
            return this;
        }

        public SseEventBuilder event(String event) {
            this.event = event;
            return this;
        }

        public SseEventBuilder data(Object data) {
            this.data = data;
            return this;
        }

        public SseEventBuilder retry(long milliseconds) {
            this.retry = milliseconds;
            return this;
        }

        public SseEventBuilder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public SseEvent build() {
            return new SseEvent(id, event, data, retry, comment);
        }
    }
}