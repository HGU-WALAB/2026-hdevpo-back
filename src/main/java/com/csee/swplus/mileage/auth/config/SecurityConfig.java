package com.csee.swplus.mileage.auth.config;

import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.csee.swplus.mileage.auth.filter.ExceptionHandlerFilter;
import com.csee.swplus.mileage.auth.filter.JwtTokenFilter;
import com.csee.swplus.mileage.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import com.csee.swplus.mileage.auth.util.JwtUtil;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private final AuthService authService;

    @Value("${custom.host.client-walab:http://walab.handong.edu}")
    private String client_walab;

    @Value("${custom.host.client-local:http://localhost:5173}")
    private String client_local;

    @Value("${custom.host.client-walab-https:https://walab.info}")
    private String client_walab_https;

    @Value("${custom.jwt.secret}")
    private String SECRET_KEY;

    @Value("${swagger.auth.user:admin}")
    private String swaggerUser;

    @Value("${swagger.auth.password:changeme}")
    private String swaggerPassword;

    @PostConstruct
    public void init() {
        log.info("🚀 Allowed CORS Clients: {} {}", client_walab, client_local);
    }

    /**
     * Swagger paths: use httpBasic so browser shows auth dialog.
     * Higher priority (Order 1) so it runs before the main API chain.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .requestMatchers()
                .antMatchers("/mileage/swagger-ui/**", "/mileage/swagger-ui.html",
                        "/mileage/v3/api-docs/**", "/swagger-ui/**", "/v3/api-docs/**")
                .and()
                .authorizeRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic()
                .and()
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf().disable();

        return http.build();
    }

    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        org.springframework.security.core.userdetails.UserDetails swaggerUserDetails =
                org.springframework.security.core.userdetails.User.builder()
                        .username(swaggerUser)
                        .password("{noop}" + swaggerPassword)
                        .roles("SWAGGER")
                        .build();
        return new org.springframework.security.provisioning.InMemoryUserDetailsManager(swaggerUserDetails);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        Key key = JwtUtil.getSigningKey(SECRET_KEY);
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .frameOptions(frame -> frame.deny()))
                .authorizeRequests(auth -> auth
                        .antMatchers("/api/mileage/auth/**", "/mileage/api/mileage/auth/**",
                                "/api/mileage/share/**", "/mileage/api/mileage/share/**",
                                "/api/mileage/contact", "/mileage/api/mileage/contact",
                                "/api/mileage/announcement", "/mileage/api/mileage/announcement",
                                "/api/mileage/maintenance", "/mileage/api/mileage/maintenance",
                                "/api/mileage/profile/image/**", "/mileage/api/mileage/profile/image/**",
                                "/api/mileage/project/image/**", "/mileage/api/mileage/project/image/**",
                                "/api/mileage/github/callback", "/mileage/api/mileage/github/callback")
                        .permitAll()
                        .antMatchers("/api/mileage/**", "/mileage/api/mileage/**").authenticated())
                .addFilterBefore(new ExceptionHandlerFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtTokenFilter(authService, key), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOrigins = new ArrayList<>();
        allowedOrigins.add(client_local);
        allowedOrigins.add(client_walab);
        allowedOrigins.add(client_walab_https);
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(Arrays.asList("POST", "GET", "PATCH", "DELETE", "PUT"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
