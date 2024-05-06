package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import  com.hmdp.utils.RedisConstants;

import javax.annotation.PostConstruct;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    public BloomFilter<Long> integerBloomFilter;

    @PostConstruct
    public void init() {

        List<Shop> list = list();
        integerBloomFilter = BloomFilter.create(Funnels.longFunnel(), list.size(), 0.01);

        list.forEach(shop -> {
                    integerBloomFilter.put(shop.getId());
                }
        );
        log.info("布隆过滤器初始化成功");
    }

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {

        if (integerBloomFilter != null) {
            if (!integerBloomFilter.mightContain(id)) {
                System.out.println("从布隆过滤器中检测到该key不存在");
                return Result.fail("无该店铺");
            }
        }
        String key = CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            System.out.println("直接从Redis中返回数据");
            return Result.ok(JSONUtil.toBean(json,Shop.class));
        }

        System.out.println("从DB查询数据");
        Shop shop = getById(id);
        if (shop != null) {
            System.out.println("将Db数据放入到Redis中");
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_NULL_TTL,TimeUnit.MINUTES);
        }
        return Result.ok(shop);

        //缓存穿透代码
//      Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
//        Shop shop1 = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        if (shop == null){
//            return Result.fail("店铺不存在");
//        }
//        return Result.ok(shop);
    }

    private  static final ExecutorService Cache_Rebuild_Excutor = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //redis 取shop
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(shopJson)){
            //不存在，直接返回
            return null;
        }
        //命中，吧json反序列化对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回
            return shop;
        }
        //已过期，缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean b = tryLock(lockKey);
        if (b){
            try {
                //获取锁，重建
                Cache_Rebuild_Excutor.submit(()->{
                    this.saveShopRedis(id,20L);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unLock(lockKey);
            }

        }
        //返回过期数据
        return shop;
    }

    /**
     * 缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //redis 取shop
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)){
            //存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if(shopJson != null){
            //缓存穿透 ，null直接返回失败
            return null;
        }

        //互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock){
                Thread.sleep(50);
                queryWithMutex(id);
            }
            Thread.sleep(200);
            shop = getById(id);
            if (shop == null){
                //缓存穿透 ，缓存null
                stringRedisTemplate.opsForValue().set(key,"",CACHE_SHOP_TTL,TimeUnit.MINUTES);
                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放lock
            unLock(lockKey);
        }

        return shop;
    }
    /**
     * 缓存穿透代码
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //redis 取shop
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)){
            //存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if(shopJson != null){
            //缓存穿透 ，null直接返回失败
            return null;
        }
        Shop shop = getById(id);
        if (shop == null){
            //缓存穿透 ，缓存null
            stringRedisTemplate.opsForValue().set(key,"",CACHE_SHOP_TTL,TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    public boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    public void saveShopRedis(Long id, Long expire){
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expire));

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("失败");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return null;
    }
}
