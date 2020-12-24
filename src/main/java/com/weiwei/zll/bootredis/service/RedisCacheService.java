package com.weiwei.zll.bootredis.service;

import com.alibaba.fastjson.JSON;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
public class RedisCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final long DEFAULT_EXPIRE_UNUSED = 60000L;

    @Autowired
    public RedisCacheService(RedisTemplate<String, String> redisTemplate) {
        Assert.notNull(redisTemplate, "初始化RedisCacheService异常，redisTemplate是Null");
        this.redisTemplate = redisTemplate;
    }


    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void setValue(String key, String value, Long ttl) {
        if (null != ttl) {
            redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, value);
        }
    }

    public void setValue(String key, String value, Long ttl, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, ttl, timeUnit);
    }

    public Object getObject(String key, Class cls) {
        String jsonStr = redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotEmpty(jsonStr)) {
            return JSON.parseObject(jsonStr, cls);
        }

        return null;
    }

    public void setObject(String key, Object obj, Long ttl) {
        String jsonStr = JSON.toJSONString(obj);
        if (null != ttl) {
            redisTemplate.opsForValue().set(key, jsonStr, ttl, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, jsonStr);
        }
    }

    public void setObject(String key, Object obj, Long ttl, TimeUnit timeUnit) {
        String jsonStr = JSON.toJSONString(obj);
        redisTemplate.opsForValue().set(key, jsonStr, ttl, timeUnit);

    }


    public Long getTime() {
        Long time = 0l;
        RedisConnection redisConn = null;
        try {
            redisConn = redisTemplate.getConnectionFactory().getConnection();
            time = redisConn.time();
        } catch (Exception e) {
            log.error("redis get time exception", e);
        } finally {
            if (null != redisConn) {
                redisConn.close();
            }
        }

        return time;
    }

    /**
     * 利用指定key作为jadis更新锁（锁有过期时长）
     *
     * @return 获得锁则返回true，否则返回false
     */
    public boolean tryLock(String key, int cacheSeconds) {
        boolean setFlag = false;
        RedisConnection redisCon = null;
        try {
            redisCon = redisTemplate.getConnectionFactory().getConnection();
            setFlag = redisCon.setNX(key.getBytes(), "".getBytes());
            redisCon.pExpire(key.getBytes(), cacheSeconds);
        } catch (Exception e) {
            log.error("redis tryLock exception", e);
        } finally {
            if (null != redisCon) {
                redisCon.close();
            }
        }
        return setFlag;
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void expire(String token, long time, TimeUnit timeUnit) {
        redisTemplate.expire(token, time, timeUnit);
    }


    /* ************* redis分布式锁实现 **************  */
    /**
     * 释放锁脚本，原子操作
     */
    public static final String UNLOCK_LUA;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("if redis.call(\"get\",KEYS[1]) == ARGV[1] ");
        sb.append("then ");
        sb.append("    return redis.call(\"del\",KEYS[1]) ");
        sb.append("else ");
        sb.append("    return 0 ");
        sb.append("end ");
        UNLOCK_LUA = sb.toString();
    }

    /**
     * 获取分布式锁，原子操作
     *
     * @param lockKey key
     * @param requestId 唯一ID, 可以使用UUID.randomUUID().toString();
     */
    public boolean tryLock(String lockKey, String requestId) {
        return this.tryLock(lockKey, requestId, DEFAULT_EXPIRE_UNUSED);
    }

    /**
     * 获取分布式锁，原子操作
     *
     * @param lockKey key
     * @param requestId 唯一ID, 可以使用UUID.randomUUID().toString();
     * @param expire 锁超时过期时间，单位毫秒
     */
    public boolean tryLock(String lockKey, String requestId, long expire) {
        try {
            RedisCallback<Boolean> callback = (connection) -> {
                return connection
                        .set(lockKey.getBytes(Charset.forName("UTF-8")), requestId.getBytes(Charset.forName("UTF-8")),
                                Expiration.seconds(TimeUnit.MILLISECONDS.toSeconds(expire)),
                                RedisStringCommands.SetOption.SET_IF_ABSENT);
            };
            return redisTemplate.execute(callback);
        } catch (Exception e) {
            log.error("redis lock error.", e);
        }
        return false;
    }

    /**
     * 释放锁
     *
     * @param requestId 唯一ID
     */
    public boolean releaseLock(String lockKey, String requestId) {
        RedisCallback<Boolean> callback = (connection) -> {
            return connection
                    .eval(UNLOCK_LUA.getBytes(), ReturnType.BOOLEAN, 1, lockKey.getBytes(Charset.forName("UTF-8")),
                            requestId.getBytes(Charset.forName("UTF-8")));
        };
        return (Boolean) redisTemplate.execute(callback);
    }

    /**
     * 获取Redis锁的value值
     */
    public String get(String lockKey) {
        try {
            RedisCallback<String> callback = (connection) -> {
                return new String(connection.get(lockKey.getBytes()), Charset.forName("UTF-8"));
            };
            return (String) redisTemplate.execute(callback);
        } catch (Exception e) {
            log.error("get redis occurred an exception", e);
        }
        return null;
    }
}
