package com.swu.atch.account;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.domain.AuthProvider;
import com.swu.atch.auth.dto.request.LoginRequest;
import com.swu.atch.auth.dto.response.TokenResponse;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.refresh.RefreshTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.*;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.setting.domain.UserSetting;
import com.swu.atch.setting.repository.UserSettingRepository;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import com.swu.atch.user.service.UserAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class AccountApiIntegrationTest extends IsolatedAuthIntegrationTest {
    private static final String PASSWORD = "Atch123!";
    private static final String NEW_PASSWORD = "NewAtch123!";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired UserSettingRepository settings;
    @Autowired PasswordEncoder encoder;
    @Autowired LocalAuthService auth;
    @Autowired AccountCredentials credentials;
    @Autowired AuthSecrets secrets;
    @Autowired AuthSessionService sessionService;
    @Autowired LocalAuthPersistenceService persistence;
    @Autowired PasswordResetChallenges challenges;
    @Autowired EmailVerificationService signupVerification;
    @Autowired StringRedisTemplate redis;
    @Autowired UserAccountService userAccounts;
    @Autowired JwtTokenService jwtTokens;
    @Autowired @Qualifier("passwordResetMailExecutor") ThreadPoolTaskExecutor mailExecutor;
    @MockitoBean EmailSender mail;
    @MockitoSpyBean RefreshTokenService refreshTokens;
    @MockitoSpyBean JdbcTemplate jdbcSpy;
    private BlockingQueue<Delivery> deliveries;

    @BeforeEach
    void captureFakeMail() {
        deliveries = new LinkedBlockingQueue<>();
        doAnswer(call -> { deliveries.add(new Delivery(call.getArgument(0), call.getArgument(1))); return null; })
                .when(mail).sendPasswordResetCode(anyString(), anyString());
        doAnswer(call -> { deliveries.add(new Delivery(call.getArgument(0), call.getArgument(1))); return null; })
                .when(mail).sendVerificationCode(anyString(), anyString());
    }

    @AfterEach
    void waitsForMailTasks() throws Exception {
        await(() -> mailExecutor.getActiveCount() == 0 && mailExecutor.getThreadPoolExecutor().getQueue().isEmpty());
    }

    @Test
    void signupStillStoresHashedCodesAndProofsAndSupportsLoginRefreshLogout() throws Exception {
        String email = email();
        assertStatus(post("/api/v1/auth/email/code"), Map.of("email", email), 204);
        String code = delivery(email).code();
        String codeKey = "auth:email:code:" + AuthSecrets.sha256(email);
        var fields = redis.opsForHash().entries(codeKey);
        assertThat(fields.get("codeHash").toString()).matches("[a-f0-9]{64}").isNotEqualTo(code);
        assertTtl(codeKey, Duration.ofMinutes(5));
        assertTtl("auth:email:cooldown:" + AuthSecrets.sha256(email), Duration.ofSeconds(60));
        var verified = response(post("/api/v1/auth/email/verify"), Map.of("email", email, "code", code), 200);
        String proof = verified.path("verificationToken").asText();
        assertTtl("auth:email:proof:" + AuthSecrets.sha256(proof), Duration.ofMinutes(10));
        assertThat(redis.hasKey(codeKey)).isFalse();
        var signedUp = response(post("/api/v1/auth/signup"), Map.of("email",email,"password",PASSWORD,
                "nickname","계정검증","verificationToken",proof,"deviceId","signup-device"), 201);
        String access = signedUp.path("accessToken").asText();
        assertStatus(bearer(patch("/api/v1/users/me/nickname"), access), Map.of("nickname","새이름"), 200);
        var refreshed = response(post("/api/v1/auth/token/refresh"), Map.of("refreshToken",signedUp.path("refreshToken").asText()), 200);
        assertStatus(post("/api/v1/auth/logout"), Map.of("refreshToken",refreshed.path("refreshToken").asText()), 204);
        assertStatus(post("/api/v1/auth/token/refresh"), Map.of("refreshToken",refreshed.path("refreshToken").asText()), 401);
        assertThat(auth.login(new LoginRequest(email,PASSWORD,"again")).accessToken()).isNotBlank();
    }

    @Test
    void resetRevokesAllDevicesPreservesOtherUserAndRejectsProofMisuse() throws Exception {
        Fixture own = fixture(); Fixture other = fixture();
        var first = login(own, PASSWORD, "first"); var second = login(own, PASSWORD, "second");
        var otherLogin = login(other, PASSWORD, "other");
        String proof = resetProof(own.email());
        assertStatus(post("/api/v1/auth/password-reset"), Map.of("email",other.email(),"resetToken",proof,"newPassword",NEW_PASSWORD),400);
        assertStatus(post("/api/v1/auth/signup"), Map.of("email",email(),"password",PASSWORD,"nickname","가입검증",
                "verificationToken",proof,"deviceId","wrong-purpose"),400);
        assertStatus(post("/api/v1/auth/password-reset"), Map.of("email",own.email(),"resetToken",proof,"newPassword",NEW_PASSWORD),204);
        for (var token : List.of(first,second)) {
            assertStatus(bearer(patch("/api/v1/users/me/nickname"),token.accessToken()),Map.of("nickname","실패이름"),401);
            assertStatus(post("/api/v1/auth/token/refresh"),Map.of("refreshToken",token.refreshToken()),401);
            assertThat(redis.hasKey(refreshKey(token.refreshToken()))).isFalse();
        }
        assertThat(redis.hasKey("auth:refresh:user:"+own.id())).isFalse();
        assertStatus(bearer(patch("/api/v1/users/me/nickname"),otherLogin.accessToken()),Map.of("nickname","다른이름"),200);
        assertStatus(post("/api/v1/auth/login"),Map.of("email",own.email(),"password",PASSWORD,"deviceId","old"),401);
        assertStatus(post("/api/v1/auth/login"),Map.of("email",own.email(),"password",NEW_PASSWORD,"deviceId","new"),200);
        assertStatus(post("/api/v1/auth/password-reset"),Map.of("email",own.email(),"resetToken",proof,"newPassword",PASSWORD),400);
    }

    @Test
    void pendingResetProofCannotOverrideLaterPasswordChange() throws Exception {
        Fixture own=fixture(); var token=login(own,PASSWORD,"one");
        String proof=resetProof(own.email());
        assertStatus(bearer(patch("/api/v1/users/me/password"),token.accessToken()),
                Map.of("currentPassword",PASSWORD,"newPassword",NEW_PASSWORD),204);
        assertStatus(post("/api/v1/auth/password-reset"),Map.of("email",own.email(),"resetToken",proof,"newPassword",PASSWORD),400);
        assertThat(login(own,NEW_PASSWORD,"new").accessToken()).isNotBlank();
    }

    @Test
    void passwordChangeChecksCurrentPasswordAndLogsOutEveryDevice() throws Exception {
        Fixture own=fixture(); var one=login(own,PASSWORD,"one"); var two=login(own,PASSWORD,"two");
        String originalHash=account(own).getPasswordHash();
        assertStatus(bearer(patch("/api/v1/users/me/password"),one.accessToken()),
                Map.of("currentPassword","Wrong123!","newPassword",NEW_PASSWORD),403);
        assertThat(account(own).getPasswordHash()).isEqualTo(originalHash);
        assertStatus(bearer(patch("/api/v1/users/me/password"),one.accessToken()),
                Map.of("currentPassword",PASSWORD,"newPassword",NEW_PASSWORD),204);
        assertThat(encoder.matches(NEW_PASSWORD,account(own).getPasswordHash())).isTrue();
        for (var token:List.of(one,two)) {
            assertStatus(bearer(patch("/api/v1/users/me/nickname"),token.accessToken()),Map.of("nickname","이름검증"),401);
            assertStatus(post("/api/v1/auth/token/refresh"),Map.of("refreshToken",token.refreshToken()),401);
        }
        assertStatus(post("/api/v1/auth/login"),Map.of("email",own.email(),"password",PASSWORD,"deviceId","bad"),401);
        assertThat(login(own,NEW_PASSWORD,"good").accessToken()).isNotBlank();
    }

    @Test
    void nicknameUsesJwtOwnerAndKeepsLoginAndAllowsSameName() throws Exception {
        Fixture own=fixture(); Fixture other=fixture(); var token=login(own,PASSWORD,"one");
        var result=response(bearer(patch("/api/v1/users/me/nickname"),token.accessToken()),Map.of("nickname","새닉네임"),200);
        assertThat(result.path("nickname").asText()).isEqualTo("새닉네임");
        assertStatus(bearer(patch("/api/v1/users/me/nickname"),token.accessToken()),Map.of("nickname","새닉네임"),200);
        assertThat(users.findById(other.id()).orElseThrow().getNickname()).isEqualTo("계정검증");
        assertStatus(patch("/api/v1/users/me/nickname"),Map.of("nickname","새닉네임"),401);
        assertStatus(bearer(patch("/api/v1/users/me/nickname"),jwtTokens.issueAccessToken(own.id())),Map.of("nickname","이전토큰"),401);
    }

    @ParameterizedTest
    @ValueSource(strings={"a","abcdefghijkl","사용자1","이름!",""})
    void rejectsInvalidNicknames(String nickname) throws Exception {
        var own=fixture(); var token=login(own,PASSWORD,"input");
        assertStatus(bearer(patch("/api/v1/users/me/nickname"),token.accessToken()),Map.of("nickname",nickname),400);
    }

    @Test
    void publicResetValidatesEmailCodeAndPasswordAndSeparatesSignupProof() throws Exception {
        assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email","invalid"),400);
        assertStatus(post("/api/v1/auth/password-reset/verify"),Map.of("email",email(),"code","abc123"),400);
        Fixture own=fixture();
        signupVerification.sendCode(own.email()); String signupCode=delivery(own.email()).code();
        var signupProof=signupVerification.verifyCode(own.email(),signupCode);
        assertStatus(post("/api/v1/auth/password-reset"),Map.of("email",own.email(),"resetToken",signupProof.verificationToken(),"newPassword",NEW_PASSWORD),400);
        String proof=resetProof(own.email());
        assertStatus(post("/api/v1/auth/password-reset"),Map.of("email",own.email(),"resetToken",proof,"newPassword","weak"),400);
        assertStatus(post("/api/v1/auth/password-reset"),Map.of("email",own.email(),"resetToken",proof,"newPassword",NEW_PASSWORD),204);
    }

    @Test
    void resetCodesAreHashedExpireAndAllowFiveAttemptsForKnownAndUnknownEmails() throws Exception {
        Fixture own=fixture(); String unknown=email();
        assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",own.email()),204);
        String realCode=delivery(own.email()).code();
        String knownKey=resetCodeKey(own.email());
        assertThat(redis.opsForHash().get(knownKey,"codeHash")).isNotEqualTo(realCode);
        assertThat(redis.opsForHash().get(knownKey,"codeHash")).isEqualTo(
                secrets.hmac("password-reset-code",own.email()+":"+realCode));
        assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",unknown),204);
        await(() -> mailExecutor.getActiveCount()==0);
        for(String email:List.of(own.email(),unknown)) {
            assertTtl(resetCodeKey(email),Duration.ofMinutes(5));
            assertTtl("auth:reset:cooldown:"+AuthSecrets.sha256(email),Duration.ofSeconds(60));
            assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",email),429);
            String wrong=realCode.equals("000000")?"111111":"000000";
            for(int i=0;i<5;i++) assertStatus(post("/api/v1/auth/password-reset/verify"),
                    Map.of("email",email,"code",wrong),i==4?429:400);
            assertThat(redis.hasKey(resetCodeKey(email))).isFalse();
        }
    }

    @Test
    void expiredResetProofAndCodeAreRejectedWithoutChangingPassword() throws Exception {
        Fixture own=fixture(); String proof=resetProof(own.email());
        redis.expire("auth:reset:proof:"+AuthSecrets.sha256(proof),Duration.ofMillis(1));
        await(() -> !Boolean.TRUE.equals(redis.hasKey("auth:reset:proof:"+AuthSecrets.sha256(proof))));
        assertStatus(post("/api/v1/auth/password-reset"),Map.of("email",own.email(),"resetToken",proof,"newPassword",NEW_PASSWORD),400);
        redis.delete("auth:reset:cooldown:"+AuthSecrets.sha256(own.email()));
        assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",own.email()),204);
        String code=delivery(own.email()).code(); redis.expire(resetCodeKey(own.email()),Duration.ofMillis(1));
        await(() -> !Boolean.TRUE.equals(redis.hasKey(resetCodeKey(own.email()))));
        assertStatus(post("/api/v1/auth/password-reset/verify"),Map.of("email",own.email(),"code",code),400);
        assertThat(encoder.matches(PASSWORD,account(own).getPasswordHash())).isTrue();
    }

    @Test
    void smtpDelayAndFailureDoNotAffectPublicResponseOrRetryBehavior() throws Exception {
        Fixture own=fixture(); String unknown=email();
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); if (!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
            throw new MailSendException("synthetic failure"); }).when(mail).sendPasswordResetCode(eq(own.email()),anyString());
        var pool=Executors.newSingleThreadExecutor();
        try {
            var request=pool.submit(() -> { assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",own.email()),204); return true; });
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            assertThat(request.get(1,TimeUnit.SECONDS)).isTrue();
            assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",unknown),204);
        } finally { release.countDown(); pool.shutdownNow(); }
        await(() -> mailExecutor.getActiveCount()==0);
        for(String email:List.of(own.email(),unknown)) {
            assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",email),429);
            for(int i=0;i<5;i++) assertStatus(post("/api/v1/auth/password-reset/verify"),Map.of("email",email,"code","000000"),i==4?429:400);
        }
    }

    @Test
    void reissuedCodeClearsPriorBindingAndIgnoresAnOldMailFailure() {
        Fixture own=fixture();
        var old=challenges.issue(own.email());
        assertThat(challenges.bind(old,own.id(),credentials.stamp(account(own)))).isTrue();
        redis.delete("auth:reset:cooldown:"+AuthSecrets.sha256(own.email()));
        var newer=challenges.issue(own.email());
        assertThat(redis.opsForHash().hasKey(resetCodeKey(own.email()),"userId")).isFalse();
        assertThat(challenges.bind(old,own.id(),"old-stamp")).isFalse();
        assertThat(challenges.bind(newer,own.id(),credentials.stamp(account(own)))).isTrue();
        challenges.invalidate(old);
        assertThat(challenges.verify(own.email(),newer.code()).expiresIn()).isEqualTo(600);
    }

    @Test
    void boundedMailQueueRejectsBothKnownAndUnknownRequestsEqually() throws Exception {
        Fixture own=fixture(); var release=new CountDownLatch(1);
        Runnable blocked=() -> { try { release.await(5,TimeUnit.SECONDS); }
            catch(InterruptedException ex) { Thread.currentThread().interrupt(); } };
        try {
            mailExecutor.execute(blocked); mailExecutor.execute(blocked);
            await(() -> mailExecutor.getActiveCount()==2);
            for(int i=0;i<64;i++) mailExecutor.execute(blocked);
            assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",own.email()),503);
            assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",email()),503);
        } finally { release.countDown(); }
    }

    @Test
    void withdrawalDeletesAllOwnedTablesButKeepsOtherUserAndCatalog() throws Exception {
        Fixture own=fixture(); Fixture other=fixture(); seedGraph(own.id()); seedGraph(other.id());
        var token=login(own,PASSWORD,"delete-one"); var another=login(own,PASSWORD,"delete-two");
        String ownLegacy="auth:refresh:"+UUID.randomUUID();
        String otherLegacy="auth:refresh:"+UUID.randomUUID();
        redis.opsForHash().put(ownLegacy,"userId",own.id().toString()); redis.expire(ownLegacy,Duration.ofDays(14));
        redis.opsForHash().put(otherLegacy,"userId",other.id().toString()); redis.expire(otherLegacy,Duration.ofDays(14));
        long itemCount=jdbc.queryForObject("SELECT count(*) FROM character_items",Long.class);
        assertStatus(bearer(post("/api/v1/users/me/withdrawal"),token.accessToken()),Map.of("currentPassword","Wrong123!"),403);
        assertThat(users.existsById(own.id())).isTrue();
        assertStatus(bearer(post("/api/v1/users/me/withdrawal"),token.accessToken()),Map.of("currentPassword",PASSWORD),204);
        assertThat(users.existsById(own.id())).isFalse();
        assertThat(redis.hasKey(ownLegacy)).isFalse();
        assertThat(redis.hasKey(otherLegacy)).isTrue();
        for(String table:List.of("auth_accounts","user_settings","todos","todo_daily_snapshots","time_prediction_experiments",
                "time_estimation_profiles","repeat_patterns","recurrence_rules","reward_settlements","reward_grants","user_character_items","user_equipped_items")) {
            assertThat(count(table,own.id())).as(table).isZero();
            assertThat(count(table,other.id())).as(table).isPositive();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM character_items",Long.class)).isEqualTo(itemCount);
        for(String table:List.of("sessions","todo_embeddings")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table+" child JOIN todos t ON t.id=child.todo_id WHERE t.user_id=?",
                    Long.class,other.id())).as(table+" other user preserved").isEqualTo(1);
        }
        for(var value:List.of(token,another)) {
            assertStatus(bearer(patch("/api/v1/users/me/nickname"),value.accessToken()),Map.of("nickname","탈퇴이름"),401);
            assertStatus(post("/api/v1/auth/token/refresh"),Map.of("refreshToken",value.refreshToken()),401);
        }
        assertThat(login(other,PASSWORD,"preserved").accessToken()).isNotBlank();
    }

    @Test
    void redisFailureLeavesPasswordAndAccountDataUnchanged() throws Exception {
        Fixture own=fixture(); var token=login(own,PASSWORD,"fail"); String hash=account(own).getPasswordHash();
        doThrow(new RedisConnectionFailureException("synthetic")).when(refreshTokens).revokeAllForUser(own.id());
        assertStatus(bearer(patch("/api/v1/users/me/password"),token.accessToken()),Map.of("currentPassword",PASSWORD,"newPassword",NEW_PASSWORD),503);
        assertStatus(bearer(post("/api/v1/users/me/withdrawal"),token.accessToken()),Map.of("currentPassword",PASSWORD),503);
        assertThat(users.existsById(own.id())).isTrue(); assertThat(account(own).getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void databaseFailureRollsBackEveryDeletionAndDoesNotRestoreRevokedSessions() {
        Fixture own=fixture(); seedGraph(own.id()); var token=login(own,PASSWORD,"rollback");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("synthetic"))
                .when(jdbcSpy).update(eq("DELETE FROM users WHERE id=?"), eq(own.id()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> userAccounts.withdraw(own.id(),credentials.stamp(account(own)),PASSWORD));
        assertThat(users.existsById(own.id())).isTrue(); assertThat(count("todos",own.id())).isPositive();
        assertThat(count("reward_grants",own.id())).isPositive(); assertThat(count("auth_accounts",own.id())).isPositive();
        assertThat(redis.hasKey(refreshKey(token.refreshToken()))).isFalse();
    }

    @Test
    void crossOwnerSnapshotAbortsWithdrawalBeforeDeletingAnything() throws Exception {
        Fixture own=fixture(); Fixture other=fixture(); long todo=seedGraph(own.id());
        jdbc.update("UPDATE todo_daily_snapshots SET user_id=? WHERE todo_id=?",other.id(),todo);
        var token=login(own,PASSWORD,"cross");
        assertStatus(bearer(post("/api/v1/users/me/withdrawal"),token.accessToken()),Map.of("currentPassword",PASSWORD),409);
        assertThat(users.existsById(own.id())).isTrue(); assertThat(count("todos",own.id())).isPositive();
        assertThat(redis.hasKey(refreshKey(token.refreshToken()))).isTrue();
    }

    @Test
    void simultaneousProofConsumptionAllowsOnePasswordReset() throws Exception {
        Fixture own=fixture(); String token=resetProof(own.email());
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<Boolean> attempt=() -> { start.await(); try { var proof=challenges.consume(own.email(),token);
            userAccounts.resetPassword(proof.userId(),proof.credentialStamp(),NEW_PASSWORD); return true; }
            catch(BusinessException ex) { return false; } };
        try { var a=pool.submit(attempt);var b=pool.submit(attempt);start.countDown();
            assertThat((a.get(5,TimeUnit.SECONDS)?1:0)+(b.get(5,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
        } finally { start.countDown();pool.shutdownNow(); }
    }

    @Test
    void loginAuthenticatedBeforePasswordChangeCannotIssueOldStateAfterward() {
        Fixture own=fixture();
        var authenticated=persistence.authenticate(own.email(),PASSWORD);
        userAccounts.changePassword(own.id(),authenticated.credentialStamp(),PASSWORD,NEW_PASSWORD);
        assertThrows(BusinessException.class,
                () -> sessionService.issue(own.id(),"late-device",authenticated.credentialStamp()));
        assertThat(redis.hasKey("auth:refresh:user:"+own.id())).isFalse();
    }

    @Test
    void concurrentPasswordChangesOnlyCommitOneOldCredentialState() throws Exception {
        Fixture own=fixture(); String stamp=credentials.stamp(account(own));
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<Boolean> change=() -> { start.await(); try {
            userAccounts.changePassword(own.id(),stamp,PASSWORD,NEW_PASSWORD);return true;
        } catch(BusinessException ex) { return false; } };
        try { var a=pool.submit(change);var b=pool.submit(change);start.countDown();
            assertThat((a.get(5,TimeUnit.SECONDS)?1:0)+(b.get(5,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
        } finally { start.countDown();pool.shutdownNow(); }
    }

    @Test
    void openApiContainsRequestSchemasStatusesAndAuthentication() throws Exception {
        var result=mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(200);
        JsonNode api=json.readTree(result.getContentAsString());
        for(String path:List.of("/api/v1/auth/password-reset/code","/api/v1/auth/password-reset/verify","/api/v1/auth/password-reset",
                "/api/v1/users/me/password","/api/v1/users/me/withdrawal","/api/v1/users/me/nickname")) {
            String method=path.endsWith("nickname")||path.endsWith("/password")?"patch":"post";
            var body=api.path("paths").path(path).path(method).path("requestBody");
            assertThat(body.path("required").asBoolean()).isTrue();
            String ref=body.path("content").path("application/json").path("schema").path("$ref").asText();
            assertThat(ref).startsWith("#/components/schemas/");
            assertThat(api.path("components").path("schemas").path(ref.substring(ref.lastIndexOf('/')+1))
                    .path("properties").size()).isPositive();
        }
        assertThat(api.path("components").path("schemas").path("PasswordChangeRequest").path("properties").path("newPassword").path("writeOnly").asBoolean()).isTrue();
        assertThat(api.path("paths").path("/api/v1/users/me/withdrawal").path("post").path("security").toString()).contains("bearerAuth");
        assertThat(api.path("paths").path("/api/v1/auth/password-reset/code").path("post").path("security").toString()).doesNotContain("bearerAuth");
        var evidence=java.nio.file.Path.of("build/verification/account-openapi.json");
        java.nio.file.Files.createDirectories(evidence.getParent());
        java.nio.file.Files.writeString(evidence,result.getContentAsString());
    }

    private Fixture fixture() {
        User user=users.save(new User("계정검증")); String email=email();
        accounts.save(AuthAccount.local(user,email,encoder.encode(PASSWORD))); settings.save(new UserSetting(user));
        return new Fixture(user.getId(),email);
    }
    private AuthAccount account(Fixture f) { return accounts.findByUser_IdAndProvider(f.id(),AuthProvider.LOCAL).orElseThrow(); }
    private TokenResponse login(Fixture f,String password,String device) { return auth.login(new LoginRequest(f.email(),password,device)); }
    private String resetProof(String email) throws Exception {
        assertStatus(post("/api/v1/auth/password-reset/code"),Map.of("email",email),204);
        String code=delivery(email).code();
        var proof=response(post("/api/v1/auth/password-reset/verify"),Map.of("email",email,"code",code),200);
        String token=proof.path("resetToken").asText();
        assertTtl("auth:reset:proof:"+AuthSecrets.sha256(token),Duration.ofMinutes(10));
        return token;
    }
    private Delivery delivery(String email) throws Exception {
        Delivery value=deliveries.poll(3,TimeUnit.SECONDS);
        assertThat(value).isNotNull(); assertThat(value.email()).isEqualTo(email); return value;
    }
    private void assertStatus(MockHttpServletRequestBuilder request,Map<String,?> body,int expected) throws Exception { response(request,body,expected); }
    private JsonNode response(MockHttpServletRequestBuilder request,Map<String,?> body,int expected) throws Exception {
        var result=mvc.perform(request.contentType("application/json").content(json.writeValueAsString(body))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        String response=result.getResponse().getContentAsString();
        return response.isBlank()?json.nullNode():json.readTree(response);
    }
    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request,String token) { return request.header("Authorization","Bearer "+token); }
    private static String email() { return UUID.randomUUID()+"@example.com"; }
    private static String resetCodeKey(String email) { return "auth:reset:code:"+AuthSecrets.sha256(email); }
    private static String refreshKey(String token) { return "auth:refresh:"+token.split("\\.")[0]; }
    private void assertTtl(String key,Duration max) {
        assertThat(redis.getExpire(key,TimeUnit.MILLISECONDS)).isBetween(max.minusSeconds(10).toMillis(),max.toMillis());
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean()&&System.nanoTime()<until) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    private long count(String table,Long id) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE user_id=?",Long.class,id); }
    private long seedGraph(Long user) {
        Long pattern=jdbc.queryForObject("""
                INSERT INTO repeat_patterns(user_id,task_name,frequency,scheduled_time,occurrence_count,status,last_occurrence_date)
                VALUES (?,'fixture','DAILY','09:00',3,'DETECTING',CURRENT_DATE) RETURNING id
                """,Long.class,user);
        Long rule=jdbc.queryForObject("""
                INSERT INTO recurrence_rules(user_id,source_pattern_id,task_name,frequency,scheduled_time,
                default_deadline_offset_minutes,default_user_estimated_duration_minutes,enabled)
                VALUES (?,?,'fixture','DAILY','09:00',60,30,true) RETURNING id
                """,Long.class,user,pattern);
        Long todo=jdbc.queryForObject("""
                INSERT INTO todos(user_id,title,start_at,due_at,current_schedule_date,all_day,user_estimated_duration_minutes,
                user_estimate_source,selected_estimate_type,status,recurrence_rule_id,recurrence_occurrence_at,recurrence_period_start_date)
                VALUES (?,'fixture',now(),now()+interval '1 hour',CURRENT_DATE,false,30,'RECURRENCE_TEMPLATE','USER','PENDING',?,now(),CURRENT_DATE) RETURNING id
                """,Long.class,user,rule);
        jdbc.update("INSERT INTO sessions(todo_id,started_at) VALUES (?,now())",todo);
        jdbc.update("""
                INSERT INTO todo_daily_snapshots(todo_id,user_id,schedule_date,status_at_day_end,start_at_snapshot,due_at_snapshot,
                was_rolled_over,was_overdue,snapshot_at) VALUES (?,?,CURRENT_DATE,'PENDING',now(),now()+interval '1 hour',false,false,now())
                """,todo,user);
        jdbc.update("INSERT INTO time_estimation_profiles(user_id,duration_correction_factor,sample_count,underestimation_rate,profile_reliability_score) VALUES (?,1,0,0,0)",user);
        jdbc.update("INSERT INTO todo_embeddings(todo_id,embedding,embedding_model,source_text_hash) VALUES (?,?::vector,'fixture',?)",
                todo,"["+String.join(",",Collections.nCopies(1024,"0.1"))+"]","a".repeat(64));
        jdbc.update("INSERT INTO time_prediction_experiments(prediction_run_id,todo_id,user_id,strategy_type,predicted_minutes) VALUES (?,?,?,'USER_ESTIMATE',30)",UUID.randomUUID(),todo,user);
        Long item=jdbc.queryForObject("INSERT INTO character_items(name,grade,category,asset_key,active) VALUES ('fixture','B','COLOR','fixture',true) RETURNING id",Long.class);
        jdbc.update("INSERT INTO user_character_items(user_id,character_item_id) VALUES (?,?)",user,item);
        jdbc.update("INSERT INTO user_equipped_items(user_id,character_item_id,category,equipped_at) VALUES (?,?,'COLOR',now())",user,item);
        jdbc.update("INSERT INTO reward_settlements(user_id,reward_date,assigned_todo_count,completed_todo_count,completion_rate,settled_at) VALUES (?,CURRENT_DATE,1,1,100,now())",user);
        jdbc.update("INSERT INTO reward_grants(user_id,character_item_id,reward_date,reward_grade,completion_rate,granted_at) VALUES (?,?,CURRENT_DATE,'B',100,now())",user,item);
        return todo;
    }
    private record Fixture(Long id,String email) { }
    private record Delivery(String email,String code) {
        @Override public String toString() { return "Delivery[REDACTED]"; }
    }
}
