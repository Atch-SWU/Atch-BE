package com.swu.atch.reward.asset;

public interface CharacterAssetStorage {
    void put(String key, byte[] image, String contentType);
    void delete(String key);
}
