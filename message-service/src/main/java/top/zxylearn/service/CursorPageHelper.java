package top.zxylearn.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class CursorPageHelper {

    private static final String CURSOR_SEPARATOR = "_";
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private CursorPageHelper() {
    }

    public static CursorParams parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new CursorParams(null, null);
        }
        String[] parts = cursor.split(CURSOR_SEPARATOR);
        if (parts.length != 2) {
            throw new IllegalArgumentException("游标格式错误");
        }
        try {
            return new CursorParams(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("游标格式错误");
        }
    }

    public static LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    public static String buildNextCursor(LocalDateTime time, Long id) {
        if (time == null || id == null) {
            return null;
        }
        long epochMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return epochMillis + CURSOR_SEPARATOR + id;
    }

    public static int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size <= 0) {
            throw new IllegalArgumentException("分页大小必须大于0");
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public record CursorParams(Long cursorTimeMillis, Long cursorId) {
    }
}
