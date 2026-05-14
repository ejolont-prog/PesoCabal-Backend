package com.example.pesoCabal.service;

import com.example.pesoCabal.model.DetalleCuenta;
import com.example.pesoCabal.repository.CuentaRepository;
import com.example.pesoCabal.repository.DetalleCuentaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class BeneficioService {
    @Autowired
    private DetalleCuentaRepository detalleRepo;
    @Autowired
    private CuentaRepository cuentaRepo;

    @Transactional
    public DetalleCuenta registrarPesaje(Integer idDetalle, BigDecimal pesoReal) {
        DetalleCuenta detalle = detalleRepo.findById(idDetalle)
                .orElseThrow(() -> new RuntimeException("Parcialidad no encontrada"));

        detalle.setPesorecibido(pesoReal);
        detalle.setFecharecepcion(LocalDateTime.now());
        detalle.setEstadopesaje(2);

        return detalleRepo.save(detalle);
    }
}
