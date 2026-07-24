package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "trusted_contacts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @NotBlank(message = "Contact name is required")
    private String contactName;

    @NotBlank(message = "Contact phone number is required")
    private String contactPhoneNumber;

    private String relationship;
}
