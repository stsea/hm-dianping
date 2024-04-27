
package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class RefreshInterceptor implements HandlerInterceptor {



    private StringRedisTemplate stringRedisTemplate;
    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取用户信息
//        HttpSession session = request.getSession();
//        Object user = session.getAttribute("user");
        String token = request.getHeader("authorization");
        if (token == null){
            return true;
        }

        //user放入localthread
//        UserHolder.saveUser((UserDTO) user);
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        if (userMap.isEmpty()){
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

}

