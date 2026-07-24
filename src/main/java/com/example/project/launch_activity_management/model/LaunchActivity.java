package com.example.project.launch_activity_management.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "launch_activity")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaunchActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Launch name is required")
    private String launchName;

    @NotBlank(message = "Activity type is required")
    private String activityType;

    @NotBlank(message = "Run number is required")
    private String runNumber;

    @NotBlank(message = "Server is required")
    private String server;

    private String observation;

    @NotNull(message = "Activity date is required")
    private LocalDate activityDate;
}
