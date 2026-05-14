package com.example.pesoCabal.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cuentas", schema = "beneficio")
public class Cuenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idcuenta;

    @Column(name = "nocuenta")
    private String noCuenta;

    private String nitagricultor;
    private Integer estado; // 1: Creada, 2: Pesaje Iniciado, 3: Finalizado
    private String tipocuenta;
    private String banco;

    @Column(name = "pesototalesperado")
    private BigDecimal pesoTotalEsperado;

    @Column(name = "id") // ID del pesaje que viene del agricultor
    private Long idPesajeExterno;

    @Column(name = "idestadopesaje")
    private Integer idEstadoPesaje;

    @Column(name = "fechacreacion")
    private LocalDateTime fechaCreacion;

    private Boolean eliminado = false;
}