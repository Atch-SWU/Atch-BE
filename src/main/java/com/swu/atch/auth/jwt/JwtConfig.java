
package com.swu.atch.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

// RS256 JWT 서명 및 검증 설정
@Configuration
public class JwtConfig {

    // PEM 파일에서 RSA 키 쌍 로딩
    @Bean
    public KeyPair jwtKeyPair(
            @Value("${app.jwt.private-key-location}") Resource privateKeyResource,
            @Value("${app.jwt.public-key-location}") Resource publicKeyResource
    ) throws IOException, GeneralSecurityException {

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        byte[] privateBytes = readPem(
                privateKeyResource,
                "PRIVATE KEY"
        );

        byte[] publicBytes = readPem(
                publicKeyResource,
                "PUBLIC KEY"
        );

        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(privateBytes)
        );

        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(publicBytes)
        );

        return new KeyPair(publicKey, privateKey);
    }

    // 개인키로 JWT 서명
    @Bean
    public JwtEncoder jwtEncoder(
            KeyPair jwtKeyPair,
            @Value("${app.jwt.key-id}") String keyId
    ) {
        return NimbusJwtEncoder.withKeyPair(
                        (RSAPublicKey) jwtKeyPair.getPublic(),
                        (RSAPrivateKey) jwtKeyPair.getPrivate()
                )
                .jwkPostProcessor(jwk -> jwk.keyID(keyId))
                .build();
    }

    // 공개키로 JWT 서명 및 클레임 검증
    @Bean
    public JwtDecoder jwtDecoder(
            KeyPair jwtKeyPair,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) jwtKeyPair.getPublic())
                .build();

        JwtClaimValidator<List<String>> audienceValidator =
                new JwtClaimValidator<>(
                        "aud",
                        audiences -> audiences != null
                                && audiences.contains(audience)
                );

        JwtClaimValidator<String> tokenTypeValidator =
                new JwtClaimValidator<>(
                        "token_use",
                        "access"::equals
                );

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<Jwt>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        audienceValidator,
                        tokenTypeValidator
                )
        );

        return decoder;
    }

    // PEM 헤더와 공백을 제거하고 Base64 디코딩
    private byte[] readPem(
            Resource resource,
            String keyType
    ) throws IOException {

        String pem;
        try (var input = resource.getInputStream()) {
            pem = new String(
                    input.readAllBytes(),
                    StandardCharsets.US_ASCII
            );
        }

        String base64 = pem
                .replace("-----BEGIN " + keyType + "-----", "")
                .replace("-----END " + keyType + "-----", "")
                .replaceAll("\\s+", "");

        return Base64.getDecoder().decode(base64);
    }
}
