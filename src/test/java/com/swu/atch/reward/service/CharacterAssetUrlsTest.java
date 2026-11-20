package com.swu.atch.reward.service;

import com.swu.atch.reward.asset.CharacterAssetProperties;
import com.swu.atch.reward.asset.CharacterAssetUrls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class CharacterAssetUrlsTest {
    private final CharacterAssetUrls urls = new CharacterAssetUrls(
            new CharacterAssetProperties("https://assets.atch.cloud/", "", "ap-northeast-2", "item/"));

    @Test
    void generatesHttpsUrlAndEncodesEachObjectKeySegment() {
        assertThat(urls.resolve("item/accessory/요리사 모자/v1.png"))
                .isEqualTo("https://assets.atch.cloud/item/accessory/%EC%9A%94%EB%A6%AC%EC%82%AC%20%EB%AA%A8%EC%9E%90/v1.png");
    }

    @ParameterizedTest
    @ValueSource(strings = {"character/chef-hat", "item/../private.png", "item//test.png", "item/./test.png", "item/test/", "item/test\\name.png"})
    void preservesLegacyFallbackAndRejectsPathsOutsideTheManagedPrefix(String key) {
        assertThat(urls.resolve(key)).isNull();
    }

    @Test
    void supportsConfiguredBucketPrefixWithoutChangingTheStoredKey() {
        var configured = new CharacterAssetUrls(new CharacterAssetProperties(
                "https://cdn.atch.test", "", "ap-northeast-2", "items/"));
        assertThat(configured.resolve("items/eyes/v2.png")).isEqualTo("https://cdn.atch.test/items/eyes/v2.png");
        assertThat(configured.resolve("item/eyes/v2.png")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://assets.atch.cloud", "https://user@assets.atch.cloud", "https://assets.atch.cloud?x=1"})
    void rejectsInvalidPublicBaseUrls(String base) {
        assertThatThrownBy(() -> new CharacterAssetUrls(new CharacterAssetProperties(base, "", "ap-northeast-2", "item/")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
