package com.example.pesoCabal.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "detallecuenta", schema = "beneficio")
public class DetalleCuenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer iddetallecuenta;

    private String nocuenta;
    private Integer noparcialidad; // ID parcialidad del agricultor
    private String placa;
    private String cuitransportista;

    private BigDecimal pesoestimado; // Lo que dice el agricultor
    private BigDecimal pesorecibido; // LO QUE DICE LA BÁSCULA (Se llena en beneficio)

    private Integer estado;
    private Integer estadopesaje;

    private LocalDateTime fecharecepcion;
    private Boolean eliminado = false;

    @Column(name = "fechacreacion", insertable = false, updatable = false)
    private LocalDateTime fechacreacion;
}