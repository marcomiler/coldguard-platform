package com.coldguard.incident.identity.infrastructure;

import com.coldguard.incident.identity.application.AuditEntry;
import com.coldguard.incident.identity.application.AuditRecorder;
import com.coldguard.incident.identity.application.LockoutPolicy;
import com.coldguard.incident.identity.application.PasswordPolicy;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

  private static final Logger log = LoggerFactory.getLogger(IdentityConfiguration.class);

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

  /**
   * Stand-in until the Audit Log module provides the real recorder: it keeps nothing. Identity
   * changes are therefore NOT audited yet; the warning makes that visible at startup.
   */
  @Bean
  @ConditionalOnMissingBean(AuditRecorder.class)
  AuditRecorder unauditedRecorder() {
    log.warn("No audit log is available: identity changes are not being audited");
    return (AuditEntry entry) -> {};
  }
}
