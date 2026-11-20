package com.swu.atch.reward.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.character.assets")
public record CharacterAssetProperties(
        @DefaultValue("https://assets.atch.cloud") String publicBaseUrl,
        @DefaultValue("") String bucket,
        @DefaultValue("ap-northeast-2") String region,
        @DefaultValue("item/") String keyPrefix
) { }
