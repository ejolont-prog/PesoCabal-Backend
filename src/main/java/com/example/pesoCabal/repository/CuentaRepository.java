package com.example.pesoCabal.repository;

import com.example.pesoCabal.model.Cuenta;
import com.example.pesoCabal.dto.CuentaDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    @Query(value = "SELECT " +
            "c.idcuenta AS idcuenta, " +
            "c.nocuenta AS noCuenta, " +
            "c.nitagricultor AS nitagricultor, " +
            "c.pesototalesperado AS pesoTotalEsperado, " +
            "c.fechacreacion AS fechaCreacion, " +
            "c.estadopesaje AS idEstadoPesaje, " +
            "c.idunidadpeso AS idunidadpeso, " +
            "a.razonsocial AS nombreAgricultor, " +
            "cat.detallecatalogo AS estadoNombre, " +
            "cat_peso.detallecatalogo AS unidadpesonombre, " +
            "(SELECT COUNT(*) FROM beneficio.detallecuenta dc WHERE dc.nocuenta = c.nocuenta AND dc.eliminado = false) AS cantParcialidades " +
            "FROM beneficio.cuentas c " +
            "LEFT JOIN beneficio.agricultores a ON TRIM(c.nitagricultor) = TRIM(a.nit) " +
            "LEFT JOIN beneficio.catalogos cat ON c.estadopesaje = cat.id " +
            "LEFT JOIN beneficio.catalogos cat_peso ON c.idunidadpeso = cat_peso.id " +
            "WHERE c.eliminado = false " +
            "AND c.estadopesaje IN (29, 30) " +
            "ORDER BY c.fechacreacion DESC", nativeQuery = true)
    List<CuentaDTO> findByEliminadoFalseWithAgricultor();




}