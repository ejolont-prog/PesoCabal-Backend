package com.example.pesoCabal.model;
import jakarta.persistence.*;
import lombok.Data;


import java.io.Serializable;

@Entity
@Data
@Table(name = "persona")
public class Persona implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String apellido;
    private Integer edad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_pais", referencedColumnName = "id_pais")
    private Pais pais;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_estado", referencedColumnName = "id")
    private Estado estado;

    @Column(name = "estado_p", insertable = false)
    private String estadoP;
}