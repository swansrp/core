package com.bidr.es.service;

import com.bidr.es.anno.EsIndex;
import com.bidr.es.dao.repository.BaseElasticsearchRepo;
import com.bidr.es.dao.repository.ElasticsearchInitRepoInf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

/**
 * Title: BaseElasticsearchRepo
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 9:41
 */
public abstract class BaseElasticsearchService<T> extends BaseElasticsearchRepo<T> implements CommandLineRunner, ElasticsearchInitRepoInf<T> {

    private static final Logger log = LoggerFactory.getLogger(BaseElasticsearchService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 3000L;

    @Override
    public void run(String... args) {
        EsIndex esIndex = getEntityClass().getAnnotation(EsIndex.class);
        if (esIndex == null) {
            return;
        }
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                init();
                return;
            } catch (Exception e) {
                log.warn("ES索引初始化失败（第{}/{}次）：{}", attempt, MAX_RETRIES, e.getMessage());
                if (!shouldRetry(attempt, e)) {
                    return;
                }
            }
        }
    }

    /**
     * 判断是否需要继续重试，并执行重试间隔等待
     *
     * @return true=继续重试，false=终止重试
     */
    private boolean shouldRetry(int attempt, Exception cause) {
        if (attempt >= MAX_RETRIES) {
            log.error("ES索引初始化重试{}次后仍失败，跳过初始化继续启动应用", MAX_RETRIES, cause);
            return false;
        }
        try {
            Thread.sleep(RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("ES索引初始化重试等待被中断，跳过初始化", ie);
            return false;
        }
    }
}