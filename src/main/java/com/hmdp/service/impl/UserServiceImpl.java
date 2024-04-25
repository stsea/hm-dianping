package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.sun.security.auth.UnixNumericGroupPrincipal;
import lombok.val;
import org.apache.ibatis.jdbc.Null;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;
import static org.springframework.beans.BeanUtils.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //不符合条件返回错误
            return Result.fail("手机号格式错误！");
        }

        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //session
        session.setAttribute("code",code);
        //发送验证码
        log.debug("验证码："+code);
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            //不符合条件返回错误
            return Result.fail("手机号格式错误！");
        }
        //校验验证码
        String sessionCode = (String) session.getAttribute("code");
        String code = loginForm.getCode();
        if (sessionCode == null || !code.equals(sessionCode)){
            return Result.fail("验证码错误！");
        }

        //查询用户是否存在 by phone
        User user = query().eq("phone", loginForm.getPhone()).one();

        if (user == null){
            user = createUserWithPhone(loginForm.getPhone());
        }
        //user保存到session
        UserDTO userDTO =new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        session.setAttribute("user",userDTO);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        //创建user
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //save
        save(user);
        return user;

    }
}
