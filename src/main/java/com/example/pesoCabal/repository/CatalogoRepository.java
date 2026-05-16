package com.example.pesoCabal.repository;

import com.example.pesoCabal.model.Cuenta; // JPA requiere asociar una entidad base
import com.example.pesoCabal.dto.CatalogoDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogoRepository extends JpaRepository<Cuenta, Long> {

    @Query(value = "SELECT id AS id, catalogo AS catalogo, detallecatalogo AS detallecatalogo " +
            "FROM beneficio.catalogos " +
            "WHERE id IN (29, 30) AND eliminado = false " +
            "ORDER BY id ASC", nativeQuery = true)
    List<CatalogoDTO> findEstadosPesajeBeneficio();
}