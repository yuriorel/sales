package com.example.sales.common.security;

import com.example.sales.common.logging.UserContextFilter;
import com.example.sales.user.domain.Role;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.net.http.HttpClient;
import java.util.Collection;
import java.util.stream.Stream;

/// One stateless filter chain for both login types: HTTP Basic against the `users`
/// table and bearer JWTs from the external OAuth2 broker.
///
/// Roles: a `LOCAL` user has the roles stored in its row. A JWT user is always
/// `CUSTOMER` and also `ADMIN` when the token's `scope` contains `ADMIN`; the broker
/// decides who may get that scope, and the database is not consulted.
///
/// Authentication and authorization failures are passed to the MVC exception
/// handlers, so they are problem responses like every other error.
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        AuthenticationEntryPoint unauthorized = (request, response, ex) -> resolver.resolveException(request, response, null, ex);
        AccessDeniedHandler forbidden = (request, response, ex) -> resolver.resolveException(request, response, null, ex);
        var jwtAuthentication = new JwtAuthenticationConverter();
        jwtAuthentication.setJwtGrantedAuthoritiesConverter(jwtRoles());

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/*/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/*/categories/**", "/api/*/products/**", "/api/*/search/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/api/*/admin/**", "/api/*/categories/**", "/api/*/products/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthentication)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .addFilterBefore(new UserContextFilter(), AuthorizationFilter.class)
                .build();
    }

    /// Decodes broker tokens: keys from the JWKS endpoint (fetched over TLS with the
    /// `oauth2-broker` SSL bundle), then issuer and expiry checks.
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            SslBundles sslBundles) {
        var httpClient = HttpClient.newBuilder()
                .sslContext(sslBundles.getBundle("oauth2-broker").createSslContext())
                .build();
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .restOperations(new RestTemplate(new JdkClientHttpRequestFactory(httpClient)))
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /// Maps a JWT to role authorities: always `CUSTOMER`, plus every scope that names a [Role].
    static Converter<Jwt, Collection<GrantedAuthority>> jwtRoles() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("");
        return jwt -> Stream.concat(
                        Stream.of(Role.CUSTOMER),
                        scopes.convert(jwt).stream().map(GrantedAuthority::getAuthority).flatMap(Role::named))
                .distinct()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();
    }
}
