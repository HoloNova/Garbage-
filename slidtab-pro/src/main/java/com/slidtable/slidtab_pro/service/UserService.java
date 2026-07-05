package com.slidtable.slidtab_pro.service;

import com.slidtable.slidtab_pro.common.BusinessException;
import com.slidtable.slidtab_pro.dto.request.LoginRequest;
import com.slidtable.slidtab_pro.dto.response.LoginResponse;
import com.slidtable.slidtab_pro.entity.User;
import com.slidtable.slidtab_pro.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        log.info("[用户登录] 开始: phone={}, studentId={}", request.phone(), request.studentId());
        User user = userRepository.findByPhoneAndStudentId(request.phone(), request.studentId())
                .orElse(null);
        if (user == null) {
            log.warn("[用户登录] 失败: 凭证不匹配 phone={}", request.phone());
            throw new BusinessException(1002, "用户不存在或凭证错误");
        }
        String token = "tk-" + user.getUserId() + "-" + System.currentTimeMillis();
        log.info("[用户登录] 成功: userId={}, name={}, identity={}",
                user.getUserId(), user.getName(), user.getIdentity());
        return new LoginResponse(user.getUserId(), user.getName(), user.getIdentity(), token);
    }

    public User getByUserId(String userId) {
        log.debug("[查询用户] userId={}", userId);
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("[查询用户] 不存在: userId={}", userId);
                    return new BusinessException(1002, "用户不存在: " + userId);
                });
    }
}
