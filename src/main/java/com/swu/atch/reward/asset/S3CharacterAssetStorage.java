package com.swu.atch.reward.asset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class S3CharacterAssetStorage implements CharacterAssetStorage {
    private final S3Client client;
    private final String bucket;

    public S3CharacterAssetStorage(S3Client client, CharacterAssetProperties properties) {
        this.client = client;
        bucket = properties.bucket();
    }

    @Override
    public void put(String key, byte[] image, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType(contentType).cacheControl("public, max-age=31536000, immutable").build(),
                RequestBody.fromBytes(image));
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
