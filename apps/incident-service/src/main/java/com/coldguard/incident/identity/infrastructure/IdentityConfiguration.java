package com.coldguard.incident.identity.infrastructure;

import com.coldguard.incident.identity.application.LockoutPolicy;
import com.coldguard.incident.identity.application.PasswordPolicy;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

  /** The id prefix lets the algorithm change later without invalidating stored hashes. */
  @Bean
  PasswordEncoder passwordEncoder(IdentityProperties props) {
    return new DelegatingPasswordEncoder(
        "bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(props.password().bcryptStrength())));
  }

  @Bean
  LockoutPolicy lockoutPolicy(IdentityProperties props) {
    return new LockoutPolicy(props.lockout().maxAttempts(), props.lockout().duration());
  }

  @Bean
  PasswordPolicy passwordPolicy(IdentityProperties props) {
    return new PasswordPolicy(props.password().minLength());
  }
}
