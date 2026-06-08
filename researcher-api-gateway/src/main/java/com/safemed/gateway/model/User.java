package com.safemed.gateway.model;

import jakarta.persistence.*;
import lombok.Data;

// researcher account, "researchers" table since "user" is a reserved word in postgres
@Entity
@Table(name = "researchers")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    // store the hashed password, never plain text
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String institution;

    // e.g. RESEARCHER, ADMIN
    @Column(nullable = false)
    private String role;
}
