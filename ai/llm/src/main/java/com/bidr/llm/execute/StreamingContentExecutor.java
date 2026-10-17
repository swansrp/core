package com.bidr.llm.execute;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 通用流式内容执行器。
 * <p>
 * 负责通过流式方式调用大模型生成长文本内容，支持实时推送草稿内容到调用方。
 * 具备超时控制、任务取消检测、异常降级等功能，确保生成的稳定性和用户体验。
 * </p>
 *
 * @author Sharp
 */
@Service
@Slf4j
public class StreamingContentExecutor {

    /**
     * 空闲收口超时：已有内容但连续该时长无新 token 时，按当前草稿收口（毫秒）
     */
    @Value("${llm.streaming.idle-timeout-ms:15000}")
    private long idleTimeoutMs;

    /**
     * 最大等待时长：整段流式生成的硬超时（毫秒）
     */
    @Value("${llm.streaming.max-wait-ms:3600000}")
    private long maxWaitMs;

    /**
     * 生成内容，优先使用流式生成，失败时自动降级为普通生成。
     * <p>
     * 该方法首先尝试使用流式模型生成内容，如果流式生成失败（网络异常、超时等），
     * 则自动回退到普通同步模型进行生成，确保内容生成的可靠性。
     * </p>
     *
     * @param contentId       内容标识（如章节ID），仅用于日志输出
     * @param contentLabel    内容名称（如章节标题），仅用于日志输出
     * @param prompt          提示词，用于指导模型生成
     * @param streamingModel  流式聊天语言模型
     * @param fallbackModel   降级使用的普通聊天语言模型
     * @param cancelChecker   取消检查回调，返回 true 表示任务已被取消
     * @param draftUpdater    草稿更新回调函数，用于实时推送生成的内容片段
     * @param finalNormalizer 最终内容标准化函数，用于清理和格式化生成的内容
     * @return 生成的内容字符串
     * @throws InterruptedException 当任务被取消或线程中断时抛出
     */
    public String generateContent(String contentId,
                                  String contentLabel,
                                  String prompt,
                                  StreamingChatLanguageModel streamingModel,
                                  ChatLanguageModel fallbackModel,
                                  Supplier<Boolean> cancelChecker,
                                  Consumer<String> draftUpdater,
                                  Function<String, String> finalNormalizer) throws InterruptedException {
        try {
            // 尝试使用流式方式生成内容
            return generateStreamingContent(contentId, contentLabel, prompt, streamingModel, cancelChecker, draftUpdater, finalNormalizer);
        } catch (InterruptedException interrupted) {
            // 如果是中断异常，直接向上抛出
            throw interrupted;
        } catch (Exception streamError) {
            // 检查任务是否被取消
            checkCanceled(cancelChecker);
            // 记录流式生成失败的警告日志
            log.warn("流式生成失败，回退普通生成: [{}] {}", contentId, contentLabel, streamError);
            // 使用降级模型进行普通生成，并对结果进行 sanitization 和标准化处理
            String fallback = finalNormalizer.apply(ModelOutputSanitizer.sanitize(fallbackModel.generate(prompt)));
            // 再次检查任务是否被取消
            checkCanceled(cancelChecker);
            // 将降级生成的内容推送到草稿更新器
            draftUpdater.accept(fallback);
            // 返回降级生成的内容
            return fallback;
        }
    }

    /**
     * 使用流式方式生成内容。
     * <p>
     * 通过流式API调用大模型，实时接收生成的token并推送到调用方，
     * 同时提供超时控制和空闲检测机制，避免长时间无响应。
     * </p>
     *
     * @param contentId       内容标识，仅用于日志输出
     * @param contentLabel    内容名称，仅用于日志输出
     * @param prompt          提示词
     * @param streamingModel  流式聊天语言模型
     * @param cancelChecker   取消检查回调
     * @param draftUpdater    草稿更新回调函数
     * @param finalNormalizer 最终内容标准化函数
     * @return 生成的内容字符串
     * @throws InterruptedException 当任务被取消或线程中断时抛出
     */
    private String generateStreamingContent(String contentId,
                                            String contentLabel,
                                            String prompt,
                                            StreamingChatLanguageModel streamingModel,
                                            Supplier<Boolean> cancelChecker,
                                            Consumer<String> draftUpdater,
                                            Function<String, String> finalNormalizer) throws InterruptedException {
        // 创建StringBuilder用于累积生成的内容
        StringBuilder builder = new StringBuilder();
        // 创建CountDownLatch用于等待异步生成完成
        CountDownLatch latch = new CountDownLatch(1);
        // 创建AtomicReference用于存储异常信息
        AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
        // 创建AtomicReference用于存储最终标准化后的内容
        AtomicReference<String> finalContentRef = new AtomicReference<String>();
        // 创建AtomicLong用于记录最后一个token到达的时间，用于空闲检测
        AtomicLong lastTokenAt = new AtomicLong(System.currentTimeMillis());

        // 调用流式模型的generate方法，传入自定义的StreamingResponseHandler
        streamingModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
            /**
             * 当接收到新的token片段时调用。
             *
             * @param token 新生成的文本片段
             */
            @Override
            public void onNext(String token) {
                // 检查线程是否被中断或任务是否被取消
                if (Thread.currentThread().isInterrupted() || Boolean.TRUE.equals(cancelChecker.get())) {
                    // 设置中断异常并释放锁
                    errorRef.set(new InterruptedException("任务已取消"));
                    latch.countDown();
                    return;
                }
                // 如果token为空，忽略
                if (token == null) {
                    return;
                }
                // 更新最后收到token的时间戳
                lastTokenAt.set(System.currentTimeMillis());
                // 同步块中追加token到builder并推送草稿更新
                synchronized (builder) {
                    builder.append(token);
                    draftUpdater.accept(builder.toString());
                }
            }

            /**
             * 当流式生成完成时调用。
             *
             * @param response 模型响应对象，包含生成的完整消息
             */
            @Override
            public void onComplete(Response<AiMessage> response) {
                // 从响应中提取AI消息
                AiMessage message = response == null ? null : response.content();
                // 获取消息文本内容
                String finalText = message == null ? null : message.text();
                // 同步块中处理最终内容
                synchronized (builder) {
                    // 如果有最终文本则使用，否则使用builder中的累积内容，并进行标准化处理
                    String normalized = finalNormalizer.apply(StringUtils.hasText(finalText) ? finalText : builder.toString());
                    // 存储标准化后的最终内容
                    finalContentRef.set(normalized);
                    // 推送最终内容到草稿更新器
                    draftUpdater.accept(normalized);
                }
                // 释放锁，表示生成完成
                latch.countDown();
            }

            /**
             * 当流式生成发生错误时调用。
             *
             * @param error 发生的异常对象
             */
            @Override
            public void onError(Throwable error) {
                // 存储异常引用
                errorRef.set(error);
                // 释放锁
                latch.countDown();
            }
        });

        // 记录开始时间
        long startAt = System.currentTimeMillis();

        // 循环等待生成完成或超时
        while (true) {
            // 等待3秒，检查是否完成
            boolean completed = latch.await(3, TimeUnit.SECONDS);
            // 如果已完成，跳出循环
            if (completed) {
                break;
            }

            // 检查任务是否被取消
            checkCanceled(cancelChecker);

            // 获取当前时间
            long now = System.currentTimeMillis();
            // 计算距离最后一个token的空闲时间
            long idleMs = now - lastTokenAt.get();
            // 如果已有内容且空闲时间超过阈值，按当前草稿收口
            if (builder.length() > 0 && idleMs >= idleTimeoutMs) {
                // 记录警告日志
                log.warn("流式生成长时间无新token，按当前草稿收口: [{}] {}, chars={}, idleMs={}",
                        contentId, contentLabel, builder.length(), idleMs);
                // 对当前草稿进行标准化处理
                String fallback = finalNormalizer.apply(builder.toString());
                // 存储为最终内容
                finalContentRef.set(fallback);
                // 推送草稿更新
                draftUpdater.accept(fallback);
                // 返回当前草稿作为最终结果
                return fallback;
            }

            // 检查是否超过最大等待时间
            if (now - startAt >= maxWaitMs) {
                // 抛出超时异常
                throw new IllegalStateException("流式生成超时");
            }
        }

        // 检查是否有异常发生
        if (errorRef.get() != null) {
            Throwable error = errorRef.get();
            // 如果是中断异常，直接抛出
            if (error instanceof InterruptedException) {
                throw (InterruptedException) error;
            }
            // 如果是运行时异常，直接抛出
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            // 其他异常包装为IllegalStateException抛出
            throw new IllegalStateException("流式生成失败", error);
        }

        // 如果有标准化后的最终内容，返回该内容
        if (StringUtils.hasText(finalContentRef.get())) {
            return finalContentRef.get();
        }
        // 否则返回builder中的内容并进行标准化处理
        return finalNormalizer.apply(builder.toString());
    }

    /**
     * 检查任务是否被取消。
     * <p>
     * 检测当前线程是否被中断或任务是否被标记为取消状态，
     * 如果是则抛出InterruptedException异常。
     * </p>
     *
     * @param cancelChecker 取消检查回调
     * @throws InterruptedException 当任务被取消或线程中断时抛出
     */
    private void checkCanceled(Supplier<Boolean> cancelChecker) throws InterruptedException {
        // 检查线程是否被中断或任务是否被取消
        if (Thread.currentThread().isInterrupted() || Boolean.TRUE.equals(cancelChecker.get())) {
            // 抛出中断异常
            throw new InterruptedException("任务已取消");
        }
    }
}
