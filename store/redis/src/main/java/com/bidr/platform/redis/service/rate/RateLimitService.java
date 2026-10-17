package com.bidr.platform.redis.service.rate;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.redis.service.RedisServiceImpl;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: RateLimitService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/9/8 15:09
 */
@Service
public class RateLimitService {

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedisServiceImpl redisServiceImpl;


    public void validateRateLimit(String key, int rate, ErrCode errCode) {
        if (rate > 0) {
            validateRateLimit(key, rate, 1, RateIntervalUnit.DAYS, errCode);
        }
    }

    public void validateRateLimit(String key, int rate, int rateInterval, RateIntervalUnit unit, ErrCode errCode) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(redisServiceImpl.getKey(key));
        if (rateLimiter.getConfig().getRate() != rate) {
            rateLimiter.delete();
        }
        rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, unit);
        Validator.assertTrue(rateLimiter.tryAcquire(1), errCode);
    }
}
