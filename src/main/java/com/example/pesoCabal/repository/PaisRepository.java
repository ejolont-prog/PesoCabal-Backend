package com.example.pesoCabal.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;
import com.example.pesoCabal.model.Pais;

@Repository
public interface PaisRepository extends JpaRepository<Pais, Long>{

}
