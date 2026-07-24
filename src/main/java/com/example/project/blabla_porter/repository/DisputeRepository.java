package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findByReporterUserId(Long reporterUserId);
    List<Dispute> findByParcelRequestId(Long parcelRequestId);
    List<Dispute> findByRideRequestId(Long rideRequestId);
    List<Dispute> findByStatus(Dispute.DisputeStatus status);
}
