package com.heima.wemedia.interceptor;

import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.thread.WmThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@Slf4j
public class WmTokenInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头中获取用户ID
        String userId = request.getHeader("userId");
        // 将用户ID包装成Optional对象，避免空指针异常
        Optional<String> optional = Optional.ofNullable(userId);
        // 判断用户ID是否存在
        if(optional.isPresent()){
            // 创建自媒体用户对象并设置用户ID
            WmUser wmUser = new WmUser();
            wmUser.setId(Integer.valueOf(userId));
            // 将用户信息存储到ThreadLocal中，供当前线程使用
            WmThreadLocalUtil.setUser(wmUser);
            log.info("wmTokenFilter设置用户信息到threadlocal中...");
        }
        // 返回true表示过滤器处理成功
        return true;

    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        log.info("清理threadlocal...");
        WmThreadLocalUtil.clear();
    }
}
