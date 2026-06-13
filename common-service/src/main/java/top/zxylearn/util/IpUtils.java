package top.zxylearn.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public final class IpUtils {

    private IpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return normalizeIp(forwardedFor.split(",")[0].trim());
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return normalizeIp(realIp.trim());
        }
        return normalizeIp(request.getRemoteAddr());
    }

    public static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        String value = cleanIpValue(ip);
        String ipv4 = normalizeIpv4(value);
        if (ipv4 != null) {
            return ipv4;
        }
        if (!value.contains(":")) {
            return value.toLowerCase(Locale.ROOT);
        }
        try {
            String normalized = InetAddress.getByName(value).getHostAddress();
            int scopeIndex = normalized.indexOf('%');
            if (scopeIndex > 0) {
                normalized = normalized.substring(0, scopeIndex);
            }
            ipv4 = normalizeIpv4(normalized);
            if (ipv4 != null) {
                return ipv4;
            }
            if ("0:0:0:0:0:0:0:1".equals(normalized) || "::1".equals(normalized)) {
                return "127.0.0.1";
            }
            return normalized.toLowerCase(Locale.ROOT);
        } catch (UnknownHostException ex) {
            return value.toLowerCase(Locale.ROOT);
        }
    }

    private static String cleanIpValue(String ip) {
        String value = ip.trim();
        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']'));
        } else {
            int colonIndex = value.indexOf(':');
            if (colonIndex > 0 && colonIndex == value.lastIndexOf(':') && value.contains(".")) {
                value = value.substring(0, colonIndex);
            }
        }
        int scopeIndex = value.indexOf('%');
        if (scopeIndex > 0) {
            value = value.substring(0, scopeIndex);
        }
        return value;
    }

    private static String normalizeIpv4(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank() || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(value);
        }
        if ("127.0.0.1".contentEquals(builder)) {
            return "127.0.0.1";
        }
        return builder.toString();
    }
}
