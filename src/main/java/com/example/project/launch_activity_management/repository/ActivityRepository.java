package com.example.project.launch_activity_management.repository;

import com.example.project.launch_activity_management.model.LaunchActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityRepository extends JpaRepository<LaunchActivity, Long> {
	boolean existsByLaunchNameAndRunNumberAndServer(
            String launchName,
            String runNumber,
            String server
    );
}
