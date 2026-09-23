package com.ishan.sciverse.summit.config;

import com.ishan.sciverse.summit.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserService userService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/api/forgot-password",
                        "/css/**", "/js/**", "/fonts/**", "/lib/**", "/images/**", "/webjars/**",
                        "/h2-console/**").permitAll()
                // One-time bootstrap admin (creates the first chair account, then is deleted)
                .requestMatchers("/setup", "/api/setup/**").hasRole("ADMIN")
                // Live-voting modal status must be readable by delegates AND the chair
                .requestMatchers("/api/voting/status").authenticated()
                // Live-segment status must be readable by delegates AND the chair
                .requestMatchers("/api/segment/status").authenticated()
                // Chair/admin-only management area
                .requestMatchers(
                        "/dashboard", "/", "/history", "/history/**",
                        "/session/**", "/api/session/**",
                        "/motions", "/resolution", "/voting",
                        "/speakers", "/unmod", "/stats", "/notes",
                        "/import", "/data-import", "/add", "/edit/**", "/save", "/delete/**",
                        "/api/stats/**", "/api/evaluation/**", "/api/delegates/**",
                        "/api/speakers/**",
                        "/api/segment/control/**",
                        "/settings/**", "/api/import/**", "/api/motions/**", "/api/voting/**",
                        "/api/resolutions/**",
                        "/admin/**")
                    .hasAnyRole("CHAIR", "ADMIN")
                // Anything else (delegate portal + APIs) requires a login
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(roleBasedSuccessHandler())
                .failureHandler(loginFailureHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .csrf(csrf -> csrf.disable()) // Disable CSRF for simplicity in this demo
            .headers(headers -> headers.frameOptions().sameOrigin()); // Allow H2 console frames

        return http.build();
    }

    /** Redirects based on role: ADMIN -> /setup, CHAIR -> /dashboard, DELEGATE -> /delegate. */
    @Bean
    public AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            String target;
            if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
                target = "/setup";
            } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CHAIR"))) {
                target = "/dashboard";
            } else {
                target = "/delegate";
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.ishan.sciverse.summit.service.AuditService auditService;

    /** Audits failed logins, then preserves the stock /login?error redirect. */
    @Bean
    public org.springframework.security.web.authentication.AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            try {
                String username = request.getParameter("username");
                auditService.log("LOGIN_FAILED", "USER", null,
                        "Failed login for \"" + (username != null ? username : "?") + "\".");
            } catch (Exception ignored) {
                // auditing must never break authentication
            }
            response.sendRedirect(request.getContextPath() + "/login?error");
        };
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
