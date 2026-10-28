
package com.swu.atch.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Atch! Access Token 발급
@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final String keyId;
    private final Duration accessTokenTtl;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.key-id}") String keyId,
            @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
        this.keyId = keyId;
        this.accessTokenTtl = accessTokenTtl;
    }

    // 인증이 완료된 사용자에게 Access Token 발급
    public String issueAccessToken(Long userId) {
        return issueAccessToken(userId, null);
    }

    public String issueAccessToken(Long userId, String credentialStamp) {
        Instant now = Instant.now();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(accessTokenTtl))
                .id(UUID.randomUUID().toString())
                .claim("token_use", "access")
                .claims(values -> {
                    if (credentialStamp != null) values.put("credential_stamp", credentialStamp);
                })
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
