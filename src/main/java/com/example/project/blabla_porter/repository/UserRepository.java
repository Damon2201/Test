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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.travelMode = 'DRIVING' WHERE u.travelMode IS NULL")
    void updateNullTravelModesToDriving();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.riderEnabled = true WHERE u.riderEnabled IS NULL")
    void updateNullRiderEnabledToTrue();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.passengerApproved = true, u.role = com.example.project.blabla_porter.model.User.UserRole.SENDER WHERE u.travelMode = 'PASSENGER' AND u.kycStatus = com.example.project.blabla_porter.model.User.KycStatus.APPROVED")
    void migrateExistingPassengerCouriers();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.passengerApproved = false WHERE u.passengerApproved IS NULL OR u.role = com.example.project.blabla_porter.model.User.UserRole.TRAVELER")
    void updateNullPassengerApprovedToFalse();
}
