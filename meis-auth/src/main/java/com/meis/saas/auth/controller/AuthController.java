package com.meis.saas.auth.controller;

import com.meis.saas.api.dto.LoginRequest;
import com.meis.saas.api.dto.LoginResponse;
import com.meis.saas.auth.service.AuthService;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/platform/login")
    public Result<LoginResponse> platformLogin(@RequestBody LoginRequest request) {
        return Result.ok(authService.platformLogin(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return Result.ok();
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("ok");
    }
}
