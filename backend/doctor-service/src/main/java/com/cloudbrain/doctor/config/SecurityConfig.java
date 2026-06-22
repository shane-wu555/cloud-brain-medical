package com.cloudbrain.doctor.config;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.oauth2.jwt.*; import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter; import org.springframework.security.web.SecurityFilterChain;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean JwtDecoder decoder(@Value("${security.jwt.secret}") String secret,@Value("${security.jwt.issuer}") String issuer){var key=new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256");var d=NimbusJwtDecoder.withSecretKey(key).build();d.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));return d;}
 @Bean JwtAuthenticationConverter converter(){var c=new JwtAuthenticationConverter();c.setJwtGrantedAuthoritiesConverter(j->java.util.List.of(new SimpleGrantedAuthority("ROLE_"+j.getClaimAsString("role"))));return c;}
 @Bean SecurityFilterChain chain(HttpSecurity h,JwtAuthenticationConverter c)throws Exception{return h.csrf(x->x.disable()).sessionManagement(x->x.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(x->x.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated()).oauth2ResourceServer(x->x.jwt(j->j.jwtAuthenticationConverter(c))).build();}
}
