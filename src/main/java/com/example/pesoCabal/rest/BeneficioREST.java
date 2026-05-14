package com.example.pesoCabal.rest;

import com.example.pesoCabal.model.Cuenta;
import com.example.pesoCabal.model.DetalleCuenta;
import com.example.pesoCabal.repository.CuentaRepository;
import com.example.pesoCabal.repository.DetalleCuentaRepository;
import com.example.pesoCabal.service.BeneficioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/beneficio")
@CrossOrigin(origins = "*")
public class BeneficioREST {

    @Autowired
    private CuentaRepository cuentaRepo;
    @Autowired
    private DetalleCuentaRepository detalleRepo;
    @Autowired
    private BeneficioService beneficioService;

    @GetMapping("/cuentas")
    public List<Cuenta> listarCuentas() {
        return cuentaRepo.findByEliminadoFalse();
    }

    @GetMapping("/cuentas/{noCuenta}/detalles")
    public List<DetalleCuenta> listarDetalles(@PathVariable String noCuenta) {
        return detalleRepo.findByNocuentaAndEliminadoFalse(noCuenta);
    }

    @PostMapping("/detalles/{id}/pesar")
    public ResponseEntity<?> pesarParcialidad(@PathVariable Integer id, @RequestBody Map<String, Object> payload) {
        BigDecimal peso = new BigDecimal(payload.get("peso").toString());
        return ResponseEntity.ok(beneficioService.registrarPesaje(id, peso));
    }
}