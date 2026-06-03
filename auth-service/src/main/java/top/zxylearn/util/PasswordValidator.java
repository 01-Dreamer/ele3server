package top.zxylearn.util;

import java.util.regex.Pattern;

public final class PasswordValidator {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,20}$");

    private PasswordValidator() {
    }

    public static void checkPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("密码只能包含字母和数字，长度为 6-20 位");
        }
    }
}
