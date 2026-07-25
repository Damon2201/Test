package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {
    List<UserDeviceToken> findByUserId(Long userId);
    Optional<UserDeviceToken> findByFcmToken(String fcmToken);
    void deleteByFcmToken(String fcmToken);
    void deleteByUserId(Long userId);
    void deleteByLastActiveBefore(LocalDateTime cutoff);
}
