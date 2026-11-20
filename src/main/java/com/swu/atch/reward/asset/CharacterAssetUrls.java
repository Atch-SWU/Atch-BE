package com.swu.atch.reward.asset;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;

@Component
public class CharacterAssetUrls {
    private final String baseUrl;
    private final String keyPrefix;

    public CharacterAssetUrls(CharacterAssetProperties properties) {
        URI base = URI.create(properties.publicBaseUrl());
        if (!"https".equals(base.getScheme()) || base.getHost() == null
                || base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalArgumentException("아이템 이미지 기본 주소는 HTTPS 주소여야 합니다.");
        }
        keyPrefix = properties.keyPrefix();
        if (keyPrefix == null || !keyPrefix.matches("[a-z0-9-]+/")) {
            throw new IllegalArgumentException("아이템 이미지 접두사는 item/처럼 한 단계로 설정합니다.");
        }
        baseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
    }

    // 앱에 포함된 이전 자산 키는 null을 반환해 기존 이미지 선택을 지원한다.
    public String resolve(String key) {
        if (key == null || !key.startsWith(keyPrefix) || key.endsWith("/") || key.contains("\\")) return null;
        String[] segments = key.split("/", -1);
        if (Arrays.stream(segments).anyMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."))) {
            return null;
        }
        return UriComponentsBuilder.fromUriString(baseUrl).pathSegment(segments).build().encode().toUriString();
    }
}
