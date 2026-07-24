package com.example.project.launch_activity_management.service;

import com.example.project.launch_activity_management.model.LaunchActivity;
import com.example.project.launch_activity_management.repository.ActivityRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActivityService {

    @Autowired
    private ActivityRepository repository;

    public LaunchActivity save(LaunchActivity a) {

    if (repository.existsByLaunchNameAndRunNumberAndServer(
            a.getLaunchName(),
            a.getRunNumber(),
            a.getServer())) {

        throw new RuntimeException("Duplicate entry!");
    }

    validate(a);
    return repository.save(a);
   }

    public LaunchActivity update(LaunchActivity a) {
        if (a.getId() == null || a.getId() <= 0) {
            throw new IllegalArgumentException("Invalid ID");
        }
        validate(a);
        return repository.save(a); // same method handles update
    }

    public List<LaunchActivity> getAll() {
        return repository.findAll();
    }

    public LaunchActivity getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found"));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public void validate(LaunchActivity a) {

        if (a.getLaunchName() == null || a.getLaunchName().isBlank())
            throw new IllegalArgumentException("Launch name is required");

        if (a.getActivityType() == null || a.getActivityType().isBlank())
            throw new IllegalArgumentException("Activity Type is required");

        if (a.getServer() == null || a.getServer().isBlank())
            throw new IllegalArgumentException("Server is required");

        if (a.getRunNumber() == null || a.getRunNumber().isBlank())
            throw new IllegalArgumentException("Run Number is required");

        if (a.getActivityDate() == null)
            throw new IllegalArgumentException("Activity Date is required");
    }
}
