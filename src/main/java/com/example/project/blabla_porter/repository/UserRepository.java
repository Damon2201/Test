package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobileNumber(String mobileNumber);
    List<User> findByRole(User.UserRole role);
    List<User> findByKycStatus(User.KycStatus kycStatus);
    boolean existsByMobileNumber(String mobileNumber);
}
