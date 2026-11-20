package com.swu.atch.reward.asset;

import com.swu.atch.reward.admin.AdminProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CharacterAssetProperties.class, AdminProperties.class})
public class CharacterAssetConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
    S3Client characterAssetS3Client(CharacterAssetProperties properties) {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new IllegalArgumentException("운영진 등록 기능에는 S3 버킷 이름이 필요합니다.");
        }
        // EC2 역할 등 SDK 기본 자격 증명을 사용하며 접근 키를 애플리케이션에 저장하지 않는다.
        return S3Client.builder().region(Region.of(properties.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(20)).apiCallAttemptTimeout(Duration.ofSeconds(10))
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(2).build()).build())
                .build();
    }
}
