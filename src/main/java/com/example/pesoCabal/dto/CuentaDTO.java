package com.example.pesoCabal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CuentaDTO {
    Long getIdcuenta();
    String getNocuenta();            // 🚀 Cambiado a minúsculas para acoplarse a Postgres
    String getNitagricultor();
    BigDecimal getPesototalesperado(); // 🚀 Cambiado a minúsculas
    LocalDateTime getFechacreacion();   // 🚀 Cambiado a minúsculas
    Integer getIdestadopesaje();       // 🚀 Cambiado a minúsculas

    String getNombreagricultor();      // 🚀 Cambiado a minúsculas
    String getEstadonombre();          // 🚀 Cambiado a minúsculas
    Integer getCantparcialidades();    // 🚀 Cambiado a minúsculas
    String getUnidadpesonombre();      // 🚀 Cambiado a minúsculas
}