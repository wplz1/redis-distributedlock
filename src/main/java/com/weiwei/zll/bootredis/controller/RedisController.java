package com.weiwei.zll.bootredis.controller;

import com.weiwei.zll.bootredis.entity.User;
import com.weiwei.zll.bootredis.service.RedisCacheService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class RedisController {

    private final RedisCacheService redisCacheService;

    @GetMapping("/redis/set")
    public String set(@RequestParam("name") String name) {
        User user = new User();
        user.setId(RandomUtils.nextInt());
        user.setName(name);
        user.setBirthday(new Date());

        List<String> list = new ArrayList<>();
        list.add("唱歌");
        list.add("run");
        user.setInteresting(list);

        Map<String, Object> map = new HashMap<>();
        map.put("hasHouse", "yes");
        map.put("hasCar", "no");
        map.put("hasKid", "no");
        user.setOthers(map);

        redisCacheService.setObject(name, user, 30000l);
        return redisCacheService.getValue(name);
    }

    @GetMapping("/redis/del")
    public String del(@RequestParam("name") String name) {
        redisCacheService.delete(name);
        return "success";
    }

    @GetMapping("/redis/lock")
    public String lock(@RequestParam("key") String key) {
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                String requestId = UUID.randomUUID().toString();
                boolean flag = true;
                while (flag) {
                    flag = !redisCacheService.tryLock(key, requestId);
                }
                System.out.println(Thread.currentThread().getName() + "获取到了锁，开始任务...");
                try {
                    Thread.sleep(5000l);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    redisCacheService.releaseLock(key, requestId);
                    System.out.println("任务结束,线程 " + Thread.currentThread().getName() + "释放锁");
                }
            },"thread" + i).start();
        }
        return "OK";
    }


}
