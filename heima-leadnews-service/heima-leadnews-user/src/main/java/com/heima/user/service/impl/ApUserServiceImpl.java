package com.heima.user.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.mapper.ApUserMapper;
import com.heima.user.service.ApUserService;
import com.heima.utils.common.AppJwtUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;

@Service
public class ApUserServiceImpl extends ServiceImpl<ApUserMapper, com.heima.model.user.pojos.ApUser> implements ApUserService {
/**
 * 用户登录功能实现
 * @param dto 登录参数对象，包含手机号和密码信息
 * @return 登录结果，包含token和用户信息或错误信息
 */
@Override
public ResponseResult login(LoginDto dto) {
    // 验证手机号和密码是否为空
    if (!StringUtils.isBlank(dto.getPhone())&&!StringUtils.isBlank(dto.getPassword())) {
        // 根据手机号查询用户信息
        ApUser apUser = getOne(Wrappers.<ApUser>lambdaQuery().eq(ApUser::getPhone, dto.getPhone()));
        if (apUser == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }
        // 验证用户密码是否正确
        String salt = apUser.getSalt();
        String pswd = dto.getPassword();
        pswd = DigestUtils.md5DigestAsHex((pswd + salt).getBytes());
        if (!pswd.equals(apUser.getPassword())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);
        }
        // 构造登录成功响应数据
        Map<String, Object> map = new HashMap<>();
        map.put("token", AppJwtUtil.getToken((apUser.getId().longValue())));
        apUser.setSalt("");
        apUser.setPassword("");
        map.put("user", apUser);
        return ResponseResult.okResult(map);
    }else {
        // 处理匿名登录情况，生成匿名访问token
        Map<String,Object> map=new HashMap<>();
        map.put("token",AppJwtUtil.getToken(0l));
        return ResponseResult.okResult(map);
    }
}
}
