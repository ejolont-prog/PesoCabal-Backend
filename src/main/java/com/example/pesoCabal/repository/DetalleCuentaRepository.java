package com.example.pesoCabal.repository;

import com.example.pesoCabal.model.DetalleCuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DetalleCuentaRepository extends JpaRepository<DetalleCuenta, Integer> {

    @Query("SELECT d FROM DetalleCuenta d WHERE d.nocuenta = :noCuenta " +
            "AND d.eliminado = false " +
            "AND d.estadopesaje != 67")
    List<DetalleCuenta> findDetallesValidosParaBeneficio(@Param("noCuenta") String noCuenta);
}