package com.peatroxd.streamcutproject.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.time.Duration;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(OperatorSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiSecurityErrorHandler apiSecurityErrorHandler,
            OperatorSecurityProperties securityProperties
    ) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Plain handler (not the default XOR/BREACH one): the raw token in the
                        // XSRF-TOKEN cookie is the canonical value, so the SPA can read the cookie
                        // and echo it back in X-XSRF-TOKEN as-is. With XorCsrfTokenRequestAttributeHandler
                        // the cookie holds the raw token while the server expects the XOR-encoded one,
                        // which 403'd every mutating request that read the cookie.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/internal/worker/**")
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/health", "/health/**", "/login.html", "/csrf", "/error").permitAll()
                        .requestMatchers("/api/internal/worker/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/").authenticated()
                        .requestMatchers(HttpMethod.GET, "/index.html", "/job.html").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(formLogin -> formLogin
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/index.html")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html?logout")
                        .deleteCookies("remember-me")
                )
                .rememberMe(rememberMe -> {
                    Duration validity = securityProperties.getRememberMeValidity();
                    rememberMe
                            .key(securityProperties.getRememberMeKey())
                            .tokenValiditySeconds((int) validity.toSeconds())
                            .rememberMeParameter("remember-me")
                            .rememberMeCookieName("remember-me");
                })
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                apiSecurityErrorHandler.commence(request, response, authException);
                                return;
                            }
                            response.sendRedirect("/login.html");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                apiSecurityErrorHandler.handle(request, response, accessDeniedException);
                                return;
                            }
                            response.sendRedirect("/login.html");
                        })
                );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            OperatorSecurityProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        return new InMemoryUserDetailsManager(User.withUsername(properties.getOperatorUsername())
                .password(passwordEncoder.encode(properties.getOperatorPassword()))
                .roles("OPERATOR")
                .build());
    }
}
