package com.parceltrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public pages: home, public tracking, static files, error pages.
                .requestMatchers("/", "/track/**", "/register", "/login", "/css/**", "/js/**", "/vendor/**", "/error/**", "/error")
                    .permitAll()
                .requestMatchers("/seller/**").hasRole("SELLER")
                .requestMatchers("/rider/**").hasRole("RIDER")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Everything else needs a logged-in user. Deny by default.
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .addLogoutHandler(new HeaderWriterLogoutHandler(
                    new ClearSiteDataHeaderWriter(ClearSiteDataHeaderWriter.Directive.CACHE,
                        ClearSiteDataHeaderWriter.Directive.COOKIES,
                        ClearSiteDataHeaderWriter.Directive.STORAGE))));
        // CSRF protection stays ON (Spring Security default). Do not disable it.
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
