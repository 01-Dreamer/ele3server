package top.zxylearn.enums;

public enum EmailCaptchaScene {

    REGISTER("register", "注册");

    private final String key;
    private final String description;

    EmailCaptchaScene(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }
}
