package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserId(String userId);

    Optional<User> findByPhoneAndStudentId(String phone, String studentId);
}
