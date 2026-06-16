package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import top.zxylearn.dto.user.UserCreateRequest;
import top.zxylearn.result.Result;

@FeignClient(name = "user-service")
public interface UserClient {

    @PostMapping("/internal/user/create-user")
    Result<?> createUser(@RequestBody UserCreateRequest request);
}
