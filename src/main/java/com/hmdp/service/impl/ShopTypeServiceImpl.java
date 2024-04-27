package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result shopList() {
        String key = "shoplist";
        //redis 取list
        String listJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(listJson)){
            //存在，直接返回
            List<ShopType> typeList  = JSONUtil.toList(listJson,ShopType.class);
            return Result.ok(typeList);
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList == null){
            return Result.fail("店铺列表不存在！");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList));

        return Result.ok(typeList);
    }
}
