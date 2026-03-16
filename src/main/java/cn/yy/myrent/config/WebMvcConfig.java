package cn.yy.myrent.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private UserContextInterceptor userContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/ws/chat",
                        "/error",
                        "/favicon.ico",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/knife4j/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/doc.html");
    }
}
