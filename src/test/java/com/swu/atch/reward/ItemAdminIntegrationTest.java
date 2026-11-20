package com.swu.atch.reward;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.reward.admin.*;
import com.swu.atch.reward.asset.CharacterAssetStorage;
import com.swu.atch.reward.domain.*;
import com.swu.atch.reward.repository.*;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
@Transactional
class ItemAdminIntegrationTest extends IsolatedAuthIntegrationTest {
    @MockitoBean AdminProperties admins;
    @MockitoBean CharacterAssetStorage storage;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired CharacterItemRepository items;
    @Autowired UserCharacterItemRepository owned;
    @Autowired RewardSettlementRepository settlements;
    @Autowired RewardGrantRepository grants;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountCredentials credentials;
    @Autowired JwtTokenService jwt;
    @Autowired AdminItemRegistrationService registration;
    private final Set<Long> allowed = new HashSet<>();

    private record Actor(User user, AuthAccount account, String email) { }

    @BeforeEach
    void configureOperators() {
        allowed.clear();
        when(admins.enabled()).thenReturn(true);
        when(admins.allows(any())).thenAnswer(invocation -> allowed.contains(invocation.getArgument(0)));
    }

    @Test
    void requiresOperatorLoginForThePageAndApi() throws Exception {
        assertThat(mvc.perform(get("/admin/items")).andReturn().getResponse().getRedirectedUrl()).endsWith("/admin/login");
        assertThat(mvc.perform(get("/api/v1/admin/character-items")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/admin/login")).andReturn().getResponse().getContentAsString()).contains("운영진 로그인");
        var actor = actor(false);
        var response = mvc.perform(post("/admin/login").with(csrf()).param("email", actor.email()).param("password", "Operator123!"))
                .andReturn().getResponse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/login?error=1");
    }

    @Test
    void servesTheProtectedPageAndRequiresCsrfForRegistration() throws Exception {
        var session = login(actor(true));
        assertThat(mvc.perform(get("/admin/items").session(session)).andReturn().getResponse().getContentAsString())
                .contains("새 아이템 등록", "upload-row-template");
        var token = json.readTree(mvc.perform(get("/admin/csrf").session(session)).andReturn().getResponse().getContentAsString());
        assertThat(token.path("token").asText()).isNotBlank();
        assertThat(mvc.perform(multipart("/api/v1/admin/character-items/batch")
                .file(metadata("csrf", "요리사 모자", "A", "ACCESSORY"))
                .file(image()).session(session)).andReturn().getResponse().getStatus()).isEqualTo(403);
        verifyNoInteractions(storage);
    }

    @Test
    void registersGradeAndRemoteImageAndReplaysWithoutUploadingAgain() throws Exception {
        var session = login(actor(true));
        String code = "hat-" + UUID.randomUUID();
        var response = upload(session, metadata(code, "요리사 모자", "A", "ACCESSORY"), image());
        assertThat(response.getStatus()).isEqualTo(200);
        var entry = json.readTree(response.getContentAsString()).path("items").get(0);
        assertThat(entry.path("itemCode").asText()).isEqualTo(code);
        assertThat(entry.path("item").path("grade").asText()).isEqualTo("A");
        assertThat(entry.path("item").path("assetUrl").asText()).startsWith("https://assets.atch.cloud/item/accessory/");
        var repeated = upload(session, metadata(code, "요리사 모자", "A", "ACCESSORY"), image());
        assertThat(repeated.getStatus()).isEqualTo(200);
        assertThat(json.readTree(repeated.getContentAsString())).isEqualTo(json.readTree(response.getContentAsString()));
        verify(storage, times(1)).put(anyString(), any(), eq("image/png"));
        verify(storage, never()).delete(anyString());
    }

    @Test
    void rejectsDifferentMetadataUnderTheSameRegistrationCode() throws Exception {
        var session = login(actor(true));
        String code = "same-" + UUID.randomUUID();
        assertThat(upload(session, metadata(code, "모자", "A", "ACCESSORY"), image()).getStatus()).isEqualTo(200);
        var conflict = upload(session, metadata(code, "모자", "S", "ACCESSORY"), image());
        assertThat(conflict.getStatus()).isEqualTo(409);
        assertThat(json.readTree(conflict.getContentAsString()).path("code").asText()).isEqualTo("ITEM_CODE_CONFLICT");
        verify(storage, times(1)).put(anyString(), any(), anyString());
    }

    @Test
    void rejectsFilesThatOnlyPretendToBePng() throws Exception {
        var session = login(actor(true));
        var fake = new MockMultipartFile("files", "fake.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertThat(upload(session, metadata("invalid-image", "모자", "A", "ACCESSORY"), fake).getStatus()).isEqualTo(400);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsOversizedImagesBeforeContactingS3() throws Exception {
        var session = login(actor(true));
        var large = new MockMultipartFile("files", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
        assertThat(upload(session, metadata("large-image", "모자", "A", "ACCESSORY"), large).getStatus()).isEqualTo(400);
        verifyNoInteractions(storage);
    }

    @Test
    void cleansAttemptedUploadsAndSavesNoItemsWhenAnUploadFails() throws Exception {
        var session = login(actor(true));
        String first = "first-" + UUID.randomUUID();
        String second = "second-" + UUID.randomUUID();
        var metadata = metadata(new AdminItemBatchRequest(List.of(input(first, "첫 모자", 0), input(second, "두번째 모자", 1))));
        doNothing().doThrow(new IllegalStateException("storage unavailable")).when(storage).put(anyString(), any(), anyString());
        assertThat(upload(session, metadata, image(), image()).getStatus()).isEqualTo(503);
        assertThat(items.findAllByItemCodeIn(List.of(first, second))).isEmpty();
        verify(storage, times(2)).delete(anyString());
    }

    @Test
    void passwordChangeInvalidatesTheOperatorSessionAndLogoutRequiresCsrf() throws Exception {
        var actor = actor(true);
        var session = login(actor);
        assertThat(mvc.perform(post("/admin/logout").session(session)).andReturn().getResponse().getStatus()).isEqualTo(403);
        actor.account().changePasswordHash(encoder.encode("Changed123!"));
        accounts.flush();
        assertThat(mvc.perform(get("/api/v1/admin/character-items").session(session)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void operatorSessionCannotAuthenticateTheJwtApi() throws Exception {
        var session = login(actor(true));
        assertThat(mvc.perform(get("/api/v1/character").session(session)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void itemDetailReturnsGradeAndImageOnlyToTheOwner() throws Exception {
        var owner = actor(false);
        var other = actor(false);
        var item = items.saveAndFlush(CharacterItem.builder().name("요리사 모자").grade(ItemGrade.A)
                .category(ItemCategory.ACCESSORY).assetKey("item/accessory/chef-hat-v1.png").build());
        owned.saveAndFlush(UserCharacterItem.acquire(owner.user(), item));
        var response = mvc.perform(get("/api/v1/character/items/" + item.getId()).header("Authorization", bearer(owner)))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var body = json.readTree(response.getContentAsString());
        assertThat(body.path("grade").asText()).isEqualTo("A");
        assertThat(body.path("assetUrl").asText()).isEqualTo("https://assets.atch.cloud/item/accessory/chef-hat-v1.png");
        assertThat(mvc.perform(get("/api/v1/character/items/" + item.getId()).header("Authorization", bearer(other)))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/api/v1/character/items/" + item.getId())).andReturn().getResponse().getStatus()).isEqualTo(401);
        var list = json.readTree(mvc.perform(get("/api/v1/character/items").header("Authorization", bearer(owner)))
                .andReturn().getResponse().getContentAsString());
        assertThat(list.path("items").get(0).path("item").path("assetUrl")).isEqualTo(body.path("assetUrl"));
    }

    @Test
    void logoutEndsTheOperatorSession() throws Exception {
        var session = login(actor(true));
        assertThat(mvc.perform(post("/admin/logout").session(session).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl()).isEqualTo("/admin/login?logout=1");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void equipmentAndRewardResultsShareTheRemoteImageContract() throws Exception {
        var owner = actor(false);
        var item = items.saveAndFlush(CharacterItem.builder().name("원격 모자").grade(ItemGrade.A)
                .category(ItemCategory.ACCESSORY).assetKey("item/accessory/remote-hat.png").build());
        owned.saveAndFlush(UserCharacterItem.acquire(owner.user(), item));
        String imageUrl = "https://assets.atch.cloud/item/accessory/remote-hat.png";
        var equipped = json.readTree(mvc.perform(put("/api/v1/character/equipment/ACCESSORY")
                .header("Authorization", bearer(owner)).contentType("application/json")
                .content("{\"characterItemId\":" + item.getId() + "}"))
                .andReturn().getResponse().getContentAsString());
        assertThat(equipped.path("item").path("assetUrl").asText()).isEqualTo(imageUrl);
        var current = json.readTree(mvc.perform(get("/api/v1/character").header("Authorization", bearer(owner)))
                .andReturn().getResponse().getContentAsString());
        assertThat(current.path("slots").get(3).path("item").path("assetUrl").asText()).isEqualTo(imageUrl);
        var day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1);
        var settlement = settlements.saveAndFlush(RewardSettlement.settle(owner.user(), day, 1, 1, java.time.Instant.now()));
        grants.saveAndFlush(RewardGrant.fromSettlement(settlement, item, java.time.Instant.now()));
        var reward = json.readTree(mvc.perform(get("/api/v1/rewards/settlements/" + day)
                .header("Authorization", bearer(owner))).andReturn().getResponse().getContentAsString());
        assertThat(reward.path("grants").get(0).path("item").path("assetUrl").asText()).isEqualTo(imageUrl);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousOperatorsCreateOneItemAndRemoveOnlyTheirUnusedImages() throws Exception {
        var first = actor(true);
        var second = actor(true);
        String code = "concurrent-" + UUID.randomUUID();
        var request = new AdminItemBatchRequest(List.of(input(code, "동시 등록 모자", 0)));
        var file = image();
        var ready = new CountDownLatch(2);
        doAnswer(invocation -> {
            ready.countDown();
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("동시 업로드 대기 초과");
            return null;
        }).when(storage).put(anyString(), any(), anyString());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> registration.register(
                    new AdminPrincipal(first.user().getId(), credentials.stamp(first.account())), request, List.of(file)));
            var two = pool.submit(() -> registration.register(
                    new AdminPrincipal(second.user().getId(), credentials.stamp(second.account())), request, List.of(file)));
            assertThat(one.get(15, TimeUnit.SECONDS).items().getFirst().item().id())
                    .isEqualTo(two.get(15, TimeUnit.SECONDS).items().getFirst().item().id());
            var saved = items.findAllByItemCodeIn(List.of(code));
            assertThat(saved).hasSize(1);
            verify(storage, times(1)).delete(anyString());
            verify(storage, never()).delete(saved.getFirst().getAssetKey());
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            items.deleteAll(items.findAllByItemCodeIn(List.of(code)));
            accounts.deleteAllById(List.of(first.account().getId(), second.account().getId()));
            users.deleteAllById(List.of(first.user().getId(), second.user().getId()));
        }
    }

    @Test
    void openApiDocumentsThePublicDetailAndOperatorMultipartContract() throws Exception {
        var api = json.readTree(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
        assertThat(api.at("/paths/~1api~1v1~1character~1items~1{itemId}/get/security").toString()).contains("bearerAuth");
        assertThat(api.at("/paths/~1api~1v1~1admin~1character-items~1batch/post/security").toString()).contains("adminSession");
        assertThat(api.at("/paths/~1api~1v1~1admin~1character-items~1batch/post/requestBody/content").has("multipart/form-data")).isTrue();
        assertThat(api.at("/components/schemas/CharacterItemResponse/properties").has("assetUrl")).isTrue();
        assertThat(api.path("paths").has("/admin/items")).isFalse();
        var path = java.nio.file.Path.of("build/verification/item-admin-20261119/openapi.json");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, api.toPrettyString());
    }

    private Actor actor(boolean operator) {
        var user = users.saveAndFlush(new User("아이템검증"));
        String email = UUID.randomUUID() + "@atch.invalid";
        var account = accounts.saveAndFlush(AuthAccount.local(user, email, encoder.encode("Operator123!")));
        if (operator) allowed.add(user.getId());
        return new Actor(user, account, email);
    }

    private MockHttpSession login(Actor actor) throws Exception {
        var result = mvc.perform(post("/admin/login").with(csrf()).param("email", actor.email()).param("password", "Operator123!"))
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/admin/items");
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String bearer(Actor actor) {
        return "Bearer " + jwt.issueAccessToken(actor.user().getId(), credentials.stamp(actor.account()));
    }

    private org.springframework.mock.web.MockHttpServletResponse upload(MockHttpSession session, MockMultipartFile metadata,
            MockMultipartFile... files) throws Exception {
        var request = multipart("/api/v1/admin/character-items/batch").file(metadata).session(session).with(csrf());
        for (var file : files) request.file(file);
        return mvc.perform(request).andReturn().getResponse();
    }

    private AdminItemBatchRequest.Item input(String code, String name, int index) {
        return new AdminItemBatchRequest.Item(code, name, ItemGrade.A, ItemCategory.ACCESSORY, index);
    }

    private MockMultipartFile metadata(String code, String name, String grade, String category) throws Exception {
        return metadata(new AdminItemBatchRequest(List.of(new AdminItemBatchRequest.Item(code, name,
                ItemGrade.valueOf(grade), ItemCategory.valueOf(category), 0))));
    }

    private MockMultipartFile metadata(AdminItemBatchRequest input) throws Exception {
        return new MockMultipartFile("metadata", "metadata.json", "application/json", json.writeValueAsBytes(input));
    }

    private MockMultipartFile image() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", output);
        return new MockMultipartFile("files", "hat.png", "image/png", output.toByteArray());
    }
}
