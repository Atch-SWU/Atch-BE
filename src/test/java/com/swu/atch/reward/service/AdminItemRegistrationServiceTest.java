package com.swu.atch.reward.service;

import com.swu.atch.reward.admin.*;
import com.swu.atch.reward.asset.*;
import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.domain.ItemGrade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminItemRegistrationServiceTest {
    @Test
    void preservesUploadedImagesWhenTheDatabaseCommitResultIsUnknown() throws Exception {
        var persistence = mock(AdminItemPersistenceService.class);
        var storage = mock(CharacterAssetStorage.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<CharacterAssetStorage> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storage);
        when(persistence.existing(any())).thenReturn(Map.of());
        when(persistence.register(any(), any(), any())).thenThrow(new DataAccessResourceFailureException("commit response lost"));
        var properties = new CharacterAssetProperties("https://assets.atch.cloud", "test-bucket", "ap-northeast-2", "item/");
        var service = new AdminItemRegistrationService(persistence, provider, properties,
                new RewardResponses(new CharacterAssetUrls(properties)));
        var request = new AdminItemBatchRequest(List.of(new AdminItemBatchRequest.Item(
                "hat", "모자", ItemGrade.A, ItemCategory.ACCESSORY, 0)));
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", image);
        var file = new MockMultipartFile("files", "hat.png", "image/png", image.toByteArray());

        assertThatThrownBy(() -> service.register(new AdminPrincipal(1L, "test-stamp"), request, List.of(file)))
                .isInstanceOfSatisfying(AdminItemException.class, error -> assertThat(error.status().value()).isEqualTo(503));
        verify(storage).put(startsWith("item/accessory/hat/"), any(), eq("image/png"));
        verify(storage, never()).delete(anyString());
        verify(persistence, never()).referencedKeys(any());
    }
}
