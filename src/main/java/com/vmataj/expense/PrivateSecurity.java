package com.vmataj.expense;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.http.HttpStatus;

@Configuration
@Profile("private")
class PrivateSecurity {
    @Bean
    SecurityFilterChain privateAccess(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/ledger.html", "/ledger.css", "/ledger.js", "/receipts.js", "/approvals",
                    "/login.html", "/signup.html", "/index.html", "/style.css", "/account.js", "/demo.js", "/account", "/account/signup", "/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                PathPatternRequestMatcher.withDefaults().matcher("/api/ledger/**"))
                .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login.html"), request -> true))
            .formLogin(login -> login.loginPage("/login.html").loginProcessingUrl("/login")
                .failureUrl("/login.html?error").defaultSuccessUrl("/", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .build();
    }

    @Bean
    UserDetailsManager users(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }
}
