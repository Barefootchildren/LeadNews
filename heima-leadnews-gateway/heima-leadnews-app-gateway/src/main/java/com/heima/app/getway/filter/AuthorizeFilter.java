package com.heima.app.getway.filter;

import com.heima.app.getway.util.AppJwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthorizeFilter implements Ordered, GlobalFilter {
    /**
     * JWT token验证过滤器
     * 该函数用于验证请求中的JWT token，对需要认证的接口进行权限控制
     *
     * @param exchange 服务器请求响应交换对象，包含请求和响应信息
     * @param chain 过滤器链，用于继续执行后续过滤器
     * @return Mono<Void> 异步返回值，表示过滤器处理完成
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // 如果是登录接口，直接放行不进行token验证
        if(request.getURI().getPath().contains("/login")){
            return chain.filter(exchange);
        }

        // 从请求头中获取token
        String token = request.getHeaders().getFirst("token");
        if(StringUtils.isEmpty(token)){
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        // 验证token的有效性
        try {
            Claims claimsBody = AppJwtUtil.getClaimsBody(token);
            int result = AppJwtUtil.verifyToken(claimsBody);
            // result为1表示token过期，2表示token无效
            if(result==1||result==2){
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }
            // 从claims中获取用户ID
            Object userId = claimsBody.get("id");
            // 构造新的请求头，添加用户ID信息
            ServerHttpRequest serverHttpRequest = request.mutate().headers(httpHeaders -> {
                httpHeaders.add("userId", userId + "");
            }).build();
            // 更新请求交换对象中的请求信息
            exchange.mutate().request(serverHttpRequest).build();
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        // token验证通过，继续执行后续过滤器
        return chain.filter(exchange);

    }

    @Override
    public int getOrder() {
        return 0;
    }
}
