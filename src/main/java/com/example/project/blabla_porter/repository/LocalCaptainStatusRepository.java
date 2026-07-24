package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.LocalCaptainStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocalCaptainStatusRepository extends JpaRepository<LocalCaptainStatus, Long> {
    Optional<LocalCaptainStatus> findByCaptainId(Long captainId);
    List<LocalCaptainStatus> findByAvailableTrue();
}
