package com.coldguard.gateway.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The gateway signs the tokens it issues and validates the ones it receives with the same RSA key
 * pair. Key files are read at startup, so a missing or malformed key stops the application instead
 * of leaving the API unable to authenticate.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

  @Bean
  @ConditionalOnMissingBean
  Clock jwtClock() {
    return Clock.systemUTC();
  }

  @Bean
  public JwtEncoder jwtEncoder(JwtProperties props) {
    RSAPublicKey publicKey = PemKeys.readPublicKey(props.publicKeyPath());
    RSAPrivateKey privateKey = PemKeys.readPrivateKey(props.privateKeyPath());
    RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
  }

  @Bean
  public JwtDecoder jwtDecoder(JwtProperties props) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(PemKeys.readPublicKey(props.publicKeyPath())).build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(props.clockSkew()), new JwtIssuerValidator(props.issuer()));
    decoder.setJwtValidator(validator);
    return decoder;
  }
}
