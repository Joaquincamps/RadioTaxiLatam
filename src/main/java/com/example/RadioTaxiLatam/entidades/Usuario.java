package com.example.RadioTaxiLatam.entidades;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.usertype.UserType;

@Entity
@Data
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;

    @Column(nullable = false, unique = true)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType tipo;
}
