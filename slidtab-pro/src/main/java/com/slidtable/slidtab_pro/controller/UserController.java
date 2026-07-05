package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import com.slidtable.slidtab_pro.dto.request.LoginRequest;
import com.slidtable.slidtab_pro.dto.response.LoginResponse;
import com.slidtable.slidtab_pro.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }
}
