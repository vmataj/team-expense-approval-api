package com.vmataj.expense;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@Profile("private")
class PrivateSecurity {
    @Bean
    SecurityFilterChain privateAccess(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/style.css", "/demo.js", "/account.js", "/account", "/account/signup", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(login -> login.defaultSuccessUrl("/", true).permitAll())
            .logout(withDefaults())
            .build();
    }

    @Bean
    UserDetailsManager users(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }
}
