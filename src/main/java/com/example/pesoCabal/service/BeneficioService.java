package com.example.pesoCabal.service;

import com.example.pesoCabal.model.DetalleCuenta;
import com.example.pesoCabal.repository.CuentaRepository;
import com.example.pesoCabal.repository.DetalleCuentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class BeneficioService {

    @Autowired
    private DetalleCuentaRepository detalleRepo;

    @Autowired
    private CuentaRepository cuentaRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Registra de forma segura y transaccional el peso de una parcialidad,
     * evalúa tolerancias y cierra la cuenta si es el último camión.
     */
    @Transactional // 🚀 Esto asegura que los cambios se guarden físicamente en PostgreSQL
    public String registrarPesajeOficial(Integer idDetalle, BigDecimal pesoObtenido, String observacionesForm) {

        // 1. Buscar la parcialidad/detalle
        DetalleCuenta detalle = detalleRepo.findById(idDetalle)
                .orElseThrow(() -> new RuntimeException("No existe la parcialidad especificada."));

        // 2. Control estricto de estados de la cuenta principal
        String sqlCheck = "SELECT c.estadopesaje FROM beneficio.cuentas c WHERE c.nocuenta = ? LIMIT 1";
        List<Map<String, Object>> resultados = jdbcTemplate.queryForList(sqlCheck, detalle.getNocuenta());

        if (resultados.isEmpty()) {
            throw new RuntimeException("No se encontró la cuenta principal asociada a este detalle.");
        }

        Integer idEstadoActual = (Integer) resultados.get(0).get("estadopesaje");

        if (idEstadoActual == null) {
            throw new RuntimeException("La cuenta no posee un estado válido asignado.");
        }
        if (idEstadoActual == 30) {
            throw new RuntimeException("Acción bloqueada: El pesaje global de esta cuenta ya ha sido finalizado.");
        }
        if (idEstadoActual != 29) {
            throw new RuntimeException("Solo se pueden pesar cuentas en estado 'Pesaje Iniciado'.");
        }

        // 3. Persistir datos físicos en la parcialidad (Estado interno 100: Pesado)
        detalle.setPesorecibido(pesoObtenido);
        detalle.setEstadopesaje(100);
        detalle.setTextorechazado("Pesaje Realizado");
        detalle.setObservaciones(observacionesForm);
        detalle.setFecharecepcion(LocalDateTime.now());

        detalleRepo.save(detalle); // Guardado seguro vía JPA

        // 4. Automatismo: Verificar si es la última parcialidad pendiente
        String noCuentaAsociada = detalle.getNocuenta();
        String sqlContarPendientes = "SELECT COUNT(*) FROM beneficio.detallecuenta " +
                "WHERE nocuenta = ? AND (estadopesaje IS NULL OR estadopesaje NOT IN (100, 68)) AND eliminado = false";

        Long pendientes = jdbcTemplate.queryForObject(sqlContarPendientes, Long.class, noCuentaAsociada);
        String mensajeExtra = "";

        if (pendientes != null && pendientes == 0) {
            // Obtener peso esperado de la cuenta madre
            String sqlGetEsperado = "SELECT pesototalesperado FROM beneficio.cuentas WHERE nocuenta = ? LIMIT 1";
            BigDecimal pesoTotalEsperado = jdbcTemplate.queryForObject(sqlGetEsperado, BigDecimal.class, noCuentaAsociada);
            if (pesoTotalEsperado == null) pesoTotalEsperado = BigDecimal.ZERO;

            // Sumar todos los camiones reales recibidos
            String sqlSumRecibido = "SELECT COALESCE(SUM(pesorecibido), 0) FROM beneficio.detallecuenta WHERE nocuenta = ? AND eliminado = false";
            BigDecimal pesoTotalRecibido = jdbcTemplate.queryForObject(sqlSumRecibido, BigDecimal.class, noCuentaAsociada);

            // Regla de negocio: Tolerancia del +/- 5%
            BigDecimal porcentajeTolerancia = new BigDecimal("0.05");
            BigDecimal toleranciaCalculada = pesoTotalEsperado.multiply(porcentajeTolerancia);
            BigDecimal diferenciaTotal = pesoTotalRecibido.subtract(pesoTotalEsperado);

            String resultadoToleranciaLabel = "Aceptado, en parametro";
            if (diferenciaTotal.compareTo(toleranciaCalculada.negate()) < 0) {
                resultadoToleranciaLabel = "Faltante";
            } else if (diferenciaTotal.compareTo(toleranciaCalculada) > 0) {
                resultadoToleranciaLabel = "Sobrante";
            }

            // Actualizar cuenta madre a estado Finalizado (30) con métricas
            String sqlUpdateCuentaCalculos = "UPDATE beneficio.cuentas SET " +
                    "estadopesaje = 30, pesototalrecibido = ?, diferenciatotal = ?, tolerancia = ?, resultadotolerancia = ? " +
                    "WHERE nocuenta = ?";

            jdbcTemplate.update(sqlUpdateCuentaCalculos,
                    pesoTotalRecibido, diferenciaTotal, toleranciaCalculada, resultadoToleranciaLabel, noCuentaAsociada
            );

            mensajeExtra = " Todas las parcialidades completadas. Cuenta en Beneficio actualizada a 'Pesaje Finalizado' [" + resultadoToleranciaLabel + "].";
        }

        return mensajeExtra;
    }
}