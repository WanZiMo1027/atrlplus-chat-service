package com.atriplus.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** 管理面板单独鉴权，不改变 OneBot 和 RabbitMQ 的现有入口。 */
@Configuration
public class AdminSecurityConfig {
    @Value("${admin.enabled:false}") private boolean enabled;
    @Value("${admin.password:}") private String password;
    @Value("${admin.internal-token:}") private String internalToken;

    @Bean
    public UserDetailsService adminUsers() {
        if (!enabled) return new InMemoryUserDetailsManager();
        if (password.isBlank() || internalToken.isBlank()) {
            throw new IllegalStateException("启用管理面板时必须配置 ADMIN_PASSWORD 和 ADMIN_INTERNAL_TOKEN");
        }
        return new InMemoryUserDetailsManager(User.withUsername("admin")
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
                .roles("ADMIN").build());
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/admin", "/admin/**", "/api/admin/**");
        if (!enabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/admin/login", "/admin/index.html",
                                "/admin/assets/**", "/api/admin/csrf").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/admin/login")
                        .loginProcessingUrl("/api/admin/login")
                        .defaultSuccessUrl("/admin/tencentdb/l1/memories", true)
                        .failureUrl("/admin/login?error"))
                .logout(logout -> logout.logoutUrl("/api/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout"))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain applicationChain(HttpSecurity http) throws Exception {
        // 原有机器接口已有各自的鉴权或内网约束，不能被表单登录和 CSRF 拦截。
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable).build();
    }
}
