package com.hmdp;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl service;


    private static Integer size = 1000000;

    @Test
    public void test(){
        service.saveShopRedis(3L, 10L);
    }

    @Test
    public void testbloom(){

        BloomFilter<Integer> integerBloomFilter = BloomFilter.create(Funnels.integerFunnel(), size, 0.01);
        for (int i = 0; i < size; i++) {
            integerBloomFilter.put(i);
        }
        // 从布隆中查询数据是否存在
        ArrayList<Integer> strings = new ArrayList<>();
        for (int j = size; j < size + 10000; j++) {
            if (integerBloomFilter.mightContain(j)) {
                strings.add(j);
            }
        }
        System.out.println("误判数量:" + strings.size());

    }

}
