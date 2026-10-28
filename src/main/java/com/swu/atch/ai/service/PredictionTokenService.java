package com.swu.atch.ai.service;

import com.swu.atch.common.exception.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;

@Service
public class PredictionTokenService {
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String issuer;
    public static final String AUDIENCE = "atch-ai-prediction";

    public record Offer(UUID draftId, String inputHash, int minutes, String todoVersion) {}

    public record Issued(String token, Instant expiresAt) {}

    public PredictionTokenService(
            JwtEncoder encoder, KeyPair jwtKeyPair, @Value("${app.jwt.issuer}") String issuer) {
        this.encoder = encoder;
        this.issuer = issuer;
        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtKeyPair.getPublic()).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<Jwt>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtClaimValidator<List<String>>(
                                "aud", aud -> aud != null && aud.equals(List.of(AUDIENCE))),
                        new JwtClaimValidator<String>("token_use", "ai_prediction"::equals)));
    }

    public Issued issue(Long user, String stamp, UUID root, List<Offer> offers) {
        Instant now = Instant.now(), expires = now.plusSeconds(600);
        var values =
                offers.stream()
                        .map(
                                o ->
                                        Map.of(
                                                "d",
                                                o.draftId().toString(),
                                                "h",
                                                o.inputHash(),
                                                "m",
                                                o.minutes(),
                                                "v",
                                                o.todoVersion()))
                        .toList();
        var claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .subject(user.toString())
                        .audience(List.of(AUDIENCE))
                        .issuedAt(now)
                        .expiresAt(expires)
                        .claim("token_use", "ai_prediction")
                        .claim("stamp", stamp)
                        .claim("root", root.toString())
                        .claim("offers", values)
                        .build();
        String token =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                        .getTokenValue();
        if (token.length() > 8192) throw new IllegalStateException("제안 토큰 상한 초과");
        return new Issued(token, expires);
    }

    public Map<UUID, Offer> verify(String token, Long user, String stamp, UUID root) {
        try {
            if (token == null || token.length() > 8192) throw invalid();
            var jwt = decoder.decode(token);
            if (!jwt.getSubject().equals(user.toString())
                    || !Objects.equals(jwt.getClaimAsString("stamp"), stamp)
                    || !root.toString().equals(jwt.getClaimAsString("root"))
                    || jwt.getExpiresAt() == null
                    || !jwt.getExpiresAt().isAfter(Instant.now())) throw invalid();
            List<Map<String, Object>> items = jwt.getClaim("offers");
            if (items == null || items.isEmpty() || items.size() > 20) throw invalid();
            var result = new HashMap<UUID, Offer>();
            for (var value : items) {
                UUID id = UUID.fromString((String) value.get("d"));
                var offer =
                        new Offer(
                                id,
                                (String) value.get("h"),
                                ((Number) value.get("m")).intValue(),
                                (String) value.get("v"));
                if (offer.minutes() <= 0
                        || offer.inputHash() == null
                        || !offer.inputHash().matches("[0-9a-f]{64}")
                        || result.put(id, offer) != null) throw invalid();
            }
            return result;
        } catch (RuntimeException ex) {
            throw invalid();
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.AI_OFFER_INVALID);
    }
}
