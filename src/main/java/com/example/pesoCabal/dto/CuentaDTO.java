package com.example.pesoCabal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CuentaDTO {
    Long getIdcuenta();
    String getNoCuenta();
    String getNitagricultor();
    BigDecimal getPesoTotalEsperado();
    LocalDateTime getFechaCreacion();
    Integer getIdEstadoPesaje();

    // Aquí mapeamos la razón social de la tabla de agricultores
    String getNombreAgricultor();


    String getEstadoNombre();     // Detalle del catálogo
    Integer getCantParcialidades();
}