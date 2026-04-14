package com.example.pesoCabal.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Entity
@Data
@Table(name = "pais")
public class Pais implements Serializable {

    @Id
    @Column(name = "id_pais")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String nombre;
}

