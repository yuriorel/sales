package com.example.sales.support;

import com.example.sales.common.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

/// Stands in for the OAuth2 broker in tests: signs tokens with a local RSA key and
/// replaces the JWKS-based decoder with one that trusts that key. The claim and type
/// checks are the production ones ([SecurityConfig#jwtValidator]).
@TestConfiguration
public class TestJwt {

    public static final String ISSUER = "https://localhost:8443";
    public static final KeyPair BROKER_KEYS = rsaKeyPair();

    @Bean
    @Primary
    JwtDecoder testJwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) BROKER_KEYS.getPublic()).validateType(false).build();
        decoder.setJwtValidator(SecurityConfig.jwtValidator(issuer));
        return decoder;
    }

    /// A valid broker token for `subject`; `customizer` can override any claim.
    public static String token(String subject, Consumer<JwtClaimsSet.Builder> customizer) {
        return token(BROKER_KEYS, subject, customizer);
    }

    public static String token(KeyPair keys, String subject, Consumer<JwtClaimsSet.Builder> customizer) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .claim("email", subject + "@example.com")
                .claim("scope", "openid profile email");
        customizer.accept(claims);
        var encoder = NimbusJwtEncoder.withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate()).build();
        // like the broker: an RFC 9068 access token
        var header = JwsHeader.with(SignatureAlgorithm.RS256).type("at+jwt").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public static KeyPair rsaKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
