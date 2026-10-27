package com.optiplant.common.idempotency;

import com.optiplant.common.exception.ApiException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persisted {@code Idempotency-Key} handling (ARQ-007, data model §6.8 app_idempotency). It runs inside the caller's
 * transaction: a transaction-scoped advisory lock serializes the same key, the stored result is inserted together with
 * the effects and a rollback discards both. Authorization and scope must be checked by the caller before calling.
 */
@Service
public class IdempotencyService {
    public static final int MAX_KEY_LENGTH = 128;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** Rejects a missing, blank, oversized or control-character key before any effect. */
    public static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH || !key.chars().allMatch(c -> c > 32 && c < 127)) {
            throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED",
                "La cabecera Idempotency-Key es obligatoria: hasta 128 caracteres visibles, sin espacios.");
        }
        return key;
    }

    /**
     * Returns the stored result when the same actor repeats the key with the same request, rejects a different request
     * under that key, and otherwise runs {@code action} and stores its result.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(long actorId, String operation, String scope, String key, Object request, int status,
                         Class<T> type, Supplier<T> action) {
        requireKey(key);
        byte[] hash = sha256(json.writeValueAsBytes(request));
        byte[] lockKey = sha256((actorId + "\n" + operation + "\n" + scope + "\n" + key).getBytes(StandardCharsets.UTF_8));
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", ByteBuffer.wrap(lockKey).getLong());
        List<Object[]> stored = jdbc.query("SELECT request_hash,result_body::text FROM app_idempotency "
                + "WHERE actor_id=? AND operation_code=? AND scope_key=? AND idempotency_key=?",
            (row, index) -> new Object[] {row.getBytes(1), row.getString(2)}, actorId, operation, scope, key);
        if (!stored.isEmpty()) {
            if (!Arrays.equals((byte[]) stored.getFirst()[0], hash)) {
                throw new ApiException(409, "IDEMPOTENCY_CONFLICT",
                    "La clave de idempotencia ya se usó con una solicitud distinta; no se aplicaron efectos.");
            }
            return json.readValue((String) stored.getFirst()[1], type);
        }
        T result = action.get();
        jdbc.update("INSERT INTO app_idempotency(actor_id,operation_code,scope_key,idempotency_key,request_hash,"
                + "result_status,result_body,completed_at) VALUES (?,?,?,?,?,?,?::jsonb,?)",
            actorId, operation, scope, key, hash, status, json.writeValueAsString(result), Timestamp.from(clock.instant()));
        return result;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
