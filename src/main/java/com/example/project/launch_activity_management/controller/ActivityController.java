package com.example.project.launch_activity_management.controller;

import com.example.project.launch_activity_management.model.LaunchActivity;
import com.example.project.launch_activity_management.service.ActivityService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activities")
public class ActivityController {

    @Autowired
    private ActivityService service;

    // CREATE
    @PostMapping
    public LaunchActivity save(@Valid @RequestBody LaunchActivity activity) {
        return service.save(activity);
    }

    // UPDATE
    @PutMapping("/{id}")
    public LaunchActivity update(@PathVariable Long id,
                                @Valid @RequestBody LaunchActivity activity) {
        activity.setId(id);
        return service.update(activity);
    }

    // GET ALL
    @GetMapping
    public List<LaunchActivity> getAll() {
        return service.getAll();
    }

    // GET BY ID
    @GetMapping("/{id}")
    public LaunchActivity getById(@PathVariable Long id) {
        return service.getById(id);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "Deleted successfully";
    }
}
