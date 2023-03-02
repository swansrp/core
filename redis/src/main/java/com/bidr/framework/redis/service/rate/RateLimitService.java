package com.bidr.framework.redis.service.rate;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.validate.Validator;
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
 * @date 2021/9/8 15:09
 */
@Service
public class RateLimitService {

    @Resource
    private RedissonClient redissonClient;

    public void validateRateLimit(String key, int rate, ErrCode errCode) {
        if (rate > 0) {
            validateRateLimit(key, rate, 1, RateIntervalUnit.DAYS, errCode);
        }
    }

    public void validateRateLimit(String key, int rate, int rateInterval, RateIntervalUnit unit, ErrCode errCode) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        if (rateLimiter.getConfig().getRate() != rate) {
            rateLimiter.delete();
        }
        rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, unit);
        Validator.assertTrue(rateLimiter.tryAcquire(1), errCode);
    }
}
