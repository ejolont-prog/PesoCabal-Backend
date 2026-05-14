package com.example.pesoCabal.repository;


import com.example.pesoCabal.model.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
    List<Cuenta> findByEliminadoFalse();
    Optional<Cuenta> findByNoCuenta(String noCuenta);
}
