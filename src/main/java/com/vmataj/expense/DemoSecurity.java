package com.vmataj.expense;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@Profile("!private")
class DemoSecurity {
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/ledger.html", "/ledger.css", "/ledger.js", "/receipts.js", "/approvals",
                    "/index.html", "/demo.js", "/account.js", "/style.css", "/account", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/claims").hasRole("EMPLOYEE")
                .requestMatchers(HttpMethod.POST, "/api/claims/*/decision").hasRole("MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/claims/*/withdraw").hasRole("EMPLOYEE")
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .httpBasic(withDefaults())
            .build();
    }

    @Bean
    UserDetailsManager users() {
        String password = "{bcrypt}" + new BCryptPasswordEncoder().encode("demo-pass");
        return new InMemoryUserDetailsManager(
            User.withUsername("employee").password(password).roles("EMPLOYEE").build(),
            User.withUsername("employee2").password(password).roles("EMPLOYEE").build(),
            User.withUsername("manager").password(password).roles("MANAGER").build()
        );
    }
}
