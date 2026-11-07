package com.vmataj.expense;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
class DemoSecurity {
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/demo.js", "/style.css", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/claims").hasRole("EMPLOYEE")
                .requestMatchers(HttpMethod.POST, "/api/claims/*/decision").hasRole("MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/claims/*/withdraw").hasRole("EMPLOYEE")
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .httpBasic(withDefaults())
            .build();
    }

    @Bean
    UserDetailsService users() {
        String password = "{bcrypt}" + new BCryptPasswordEncoder().encode("demo-pass");
        return new InMemoryUserDetailsManager(
            User.withUsername("employee").password(password).roles("EMPLOYEE").build(),
            User.withUsername("employee2").password(password).roles("EMPLOYEE").build(),
            User.withUsername("manager").password(password).roles("MANAGER").build()
        );
    }
}
