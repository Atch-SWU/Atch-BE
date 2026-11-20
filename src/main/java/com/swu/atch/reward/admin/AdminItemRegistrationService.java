package com.swu.atch.reward.admin;

import com.swu.atch.reward.asset.CharacterAssetProperties;
import com.swu.atch.reward.asset.CharacterAssetStorage;
import com.swu.atch.reward.domain.CharacterItem;
import com.swu.atch.reward.service.RewardResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminItemRegistrationService {
    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final long MAX_BATCH_BYTES = 20 * 1024 * 1024;
    private final AdminItemPersistenceService persistence;
    private final ObjectProvider<CharacterAssetStorage> storage;
    private final CharacterAssetProperties properties;
    private final RewardResponses responses;

    public AdminItemBatchResponse register(AdminPrincipal principal, AdminItemBatchRequest request, List<MultipartFile> files) {
        var prepared = prepare(request, files);
        var keys = new LinkedHashMap<String, String>();
        try {
            var existing = persistence.existing(prepared);
            for (var item : prepared) {
                if (existing.containsKey(item.input().itemCode())) continue;
                // 각 업로드에 새 키를 부여해 재시도·동시 요청의 파일을 덮어쓰지 않는다.
                String key = properties.keyPrefix() + item.input().category().name().toLowerCase(Locale.ROOT)
                        + "/" + item.input().itemCode() + "/" + UUID.randomUUID() + "." + item.extension();
                keys.put(item.input().itemCode(), key);
                store().put(key, item.image(), item.contentType());
            }
            List<CharacterItem> saved;
            try {
                saved = persistence.register(principal, prepared, keys);
            } catch (DataIntegrityViolationException ex) {
                // 다른 운영진이 같은 코드를 먼저 커밋했으면 새 트랜잭션에서 내용까지 재검증한다.
                saved = persistence.register(principal, prepared, keys);
            }
            cleanup(keys.values());
            return new AdminItemBatchResponse(saved.stream()
                    .map(item -> new AdminCatalogItemResponse(item.getItemCode(), responses.item(item))).toList());
        } catch (RuntimeException ex) {
            // DB 장애로 커밋 여부를 확정할 수 없으면 이미지 삭제를 보류한다.
            if (ex instanceof DataAccessException) log.warn("DB 장애 후 이미지 참조 확인 필요. 키={}", keys.values());
            else cleanup(keys.values());
            if (ex instanceof AdminItemException || ex instanceof com.swu.atch.common.exception.BusinessException) throw ex;
            log.warn("운영진 아이템 등록 실패. 요청 코드={}", prepared.stream().map(item -> item.input().itemCode()).toList());
            throw new AdminItemException(HttpStatus.SERVICE_UNAVAILABLE, "ITEM_REGISTRATION_UNAVAILABLE",
                    "아이템 등록을 완료하지 못했습니다. 같은 항목으로 다시 시도해주세요.");
        }
    }

    private List<PreparedCharacterItem> prepare(AdminItemBatchRequest request, List<MultipartFile> files) {
        if (request.items().size() != files.size()) throw invalid("아이템 정보와 이미지 파일 수가 다릅니다.");
        long total = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > MAX_BATCH_BYTES) throw invalid("한 번에 업로드할 이미지 합계는 20MB까지입니다.");
        var codes = new HashSet<String>();
        var indexes = new HashSet<Integer>();
        var prepared = new ArrayList<PreparedCharacterItem>();
        for (var input : request.items()) {
            if (!codes.add(input.itemCode()) || !indexes.add(input.fileIndex()) || input.fileIndex() >= files.size()) {
                throw invalid("등록 코드와 파일 위치는 각각 한 번씩 지정해주세요.");
            }
            var file = files.get(input.fileIndex());
            if (file.isEmpty() || file.getSize() > MAX_FILE_BYTES) throw invalid("이미지는 한 장당 2MB까지 등록할 수 있습니다.");
            try {
                byte[] image = file.getBytes();
                String format = validateImage(image);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
                prepared.add(new PreparedCharacterItem(input, image, hash, "image/" + format,
                        format.equals("jpeg") ? "jpg" : "png"));
            } catch (IOException ex) {
                throw invalid("이미지 파일을 읽을 수 없습니다. 파일을 다시 선택해주세요.");
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return prepared;
    }

    private String validateImage(byte[] image) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid("PNG 또는 JPEG 이미지 파일을 선택해주세요.");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if ((!format.equals("png") && !format.equals("jpeg"))
                        || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) {
                    throw invalid("이미지는 PNG·JPEG 형식이며 가로·세로 각각 4096px까지 지원합니다.");
                }
                reader.read(0);
                return format;
            } finally {
                reader.dispose();
            }
        }
    }

    private CharacterAssetStorage store() {
        var configured = storage.getIfAvailable();
        if (configured == null) throw new AdminItemException(HttpStatus.SERVICE_UNAVAILABLE,
                "ITEM_STORAGE_UNAVAILABLE", "이미지 저장소 설정을 확인해주세요.");
        return configured;
    }

    private void cleanup(Collection<String> keys) {
        if (keys.isEmpty()) return;
        try {
            // 커밋 응답을 잃었더라도 DB에서 사용 중인 이미지는 보존한다.
            var referenced = persistence.referencedKeys(keys);
            for (String key : keys) {
                if (!referenced.contains(key)) store().delete(key);
            }
        } catch (RuntimeException ex) {
            log.warn("아이템 이미지 정리 보류. DB 참조 확인과 S3 삭제를 재확인할 키={}", keys);
        }
    }

    private AdminItemException invalid(String message) {
        return new AdminItemException(HttpStatus.BAD_REQUEST, "INVALID_ITEM_UPLOAD", message);
    }
}
