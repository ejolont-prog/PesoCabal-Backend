package com.example.pesoCabal.repository;

import com.example.pesoCabal.model.DetalleCuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetalleCuentaRepository extends JpaRepository<DetalleCuenta, Integer> {
    List<DetalleCuenta> findByNocuentaAndEliminadoFalse(String noCuenta);
}
