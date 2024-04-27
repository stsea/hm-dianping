package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import  com.hmdp.utils.RedisConstants;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //redis 取shop
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)){
            //存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if(shopJson != null){
            //缓存击穿 ，null直接返回失败
            return Result.fail("店铺不存在");
        }
        Shop shop = getById(id);
        if (shop == null){
            //缓存击穿 ，缓存null
            stringRedisTemplate.opsForValue().set(key,"",CACHE_SHOP_TTL,TimeUnit.MINUTES);
            return Result.fail("店铺不存在！");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shop);
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
