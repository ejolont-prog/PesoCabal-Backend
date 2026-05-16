package com.example.pesoCabal.rest;

import com.example.pesoCabal.model.Cuenta;
import com.example.pesoCabal.model.DetalleCuenta;
import com.example.pesoCabal.dto.CuentaDTO;
import com.example.pesoCabal.repository.CuentaRepository;
import com.example.pesoCabal.repository.DetalleCuentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/beneficio") // Mantiene tu ruta base actual
@CrossOrigin(origins = "*")
public class BeneficioREST {

    @Autowired
    private CuentaRepository cuentaRepo;

    @Autowired
    private DetalleCuentaRepository detalleRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate; // Lo usaremos únicamente para registrar la boleta física y leer catálogos de seguridad

    // 1. BANDEJA PRINCIPAL (Filtro automático delegando en tu CuentaRepository optimizado)
    @GetMapping("/cuentas")
    public List<CuentaDTO> listarCuentas() {
        return cuentaRepo.findByEliminadoFalseWithAgricultor();
    }

    // 2. PANTALLA DE DETALLE (Vista de Parcialidades por No. Cuenta)
    @GetMapping("/cuentas/{noCuenta}/detalles")
    public List<DetalleCuenta> listarDetalles(@PathVariable String noCuenta) {
        return detalleRepo.findByNocuentaAndEliminadoFalse(noCuenta);
    }

    // 3. ACCIÓN PRINCIPAL: "Actualizar Peso" (Formulario de Báscula)
    @PostMapping("/detalles/{id}/pesar")
    public ResponseEntity<?> pesarParcialidad(@PathVariable Integer id, @RequestBody Map<String, Object> payload) {
        try {
            // Verificar que venga el peso requerido
            if (payload.get("peso") == null) {
                return ResponseEntity.badRequest().body("{\"error\": \"El peso obtenido es obligatorio.\"}");
            }
            BigDecimal pesoObtenido = new BigDecimal(payload.get("peso").toString());

            // Captura de nuevos campos opcionales del formulario
            String tipoMedida = payload.get("tipoMedida") != null ? payload.get("tipoMedida").toString() : "Quintales";

            // ✍️ Capturamos las observaciones enviadas por el usuario desde el Front
            String observacionesForm = payload.get("observaciones") != null ? payload.get("observaciones").toString() : "";

            // Buscar la parcialidad/detalle enviado para conocer su noCuenta asociado
            Optional<DetalleCuenta> detalleOpt = detalleRepo.findById(id);
            if (detalleOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"No existe la parcialidad especificada.\"}");
            }
            DetalleCuenta detalle = detalleOpt.get();

            // 🔍 CORRECCIÓN CRÍTICA DE COLUMNA: Se cambia c.idestadopesaje por c.estadopesaje
            String sqlCheck = "SELECT c.estadopesaje, cat.detallecatalogo FROM beneficio.cuentas c " +
                    "LEFT JOIN beneficio.catalogos cat ON c.estadopesaje = cat.id WHERE c.nocuenta = ? LIMIT 1";

            List<Map<String, Object>> resultados = jdbcTemplate.queryForList(sqlCheck, detalle.getNocuenta());

            if (resultados.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"No se encontró la cuenta principal asociada a este detalle.\"}");
            }

            Map<String, Object> resultadoCuenta = resultados.get(0);
            Integer idEstadoActual = (Integer) resultadoCuenta.get("estadopesaje");
            String estadoNombreActual = (String) resultadoCuenta.get("detallecatalogo");

            // Validar si el estado es nulo o si no corresponde a Pesaje Iniciado (29) o Finalizado (30)
            if (idEstadoActual == null || (idEstadoActual != 29 && idEstadoActual != 30)) {
                String nombreMostrar = (estadoNombreActual != null) ? estadoNombreActual : "Desconocido";
                // RN AN02: No. 8 - Mensaje Dinámico de Error Estricto
                return ResponseEntity.badRequest().body("{\"error\": \"La cuenta se encuentra en estado: " + nombreMostrar + " no es posible ingresar pesajes.\"}");
            }

            // PROCESAMIENTO Y PERSISTENCIA (Campos físicos de la tabla)
            detalle.setPesorecibido(pesoObtenido);
            detalle.setEstadopesaje(100); // Estado que acepta el catálogo

            // 🔥 1. Guardamos el texto fijo automático exigido por la regla de negocio
            detalle.setTextorechazado("Pesaje Realizado");

            // 🔥 2. Guardamos las observaciones capturadas del formulario de la báscula
            detalle.setObservaciones(observacionesForm);

            // Se registra la fecha actual del pesaje tal como pide el flujo
            detalle.setFecharecepcion(LocalDateTime.now());

            detalleRepo.save(detalle); // Guarda los cambios en beneficio.detallecuenta

            // RN AN01: No. 6 o No. 7 - Éxito
            return ResponseEntity.ok("{\"mensaje\": \"Se actualizó con éxito el peso de la parcialidad.\"}");

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Error en el proceso de pesaje: " + e.getMessage() + "\"}");
        }
    }


    // 4. ACCIÓN SECUNDARIA: "Generar Boleta" (Persistencia en tablas de pesocabal)
    @PostMapping("/detalles/{id}/boleta")
    public ResponseEntity<?> generarBoleta(@PathVariable Integer id, @RequestHeader("X-User-Logged") String usuarioLogueado) {
        try {
            // Buscamos la parcialidad pesada
            Optional<DetalleCuenta> detalleOpt = detalleRepo.findById(id);
            if (detalleOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"No se encontró el registro para generar boleta.\"}");
            }



            DetalleCuenta dc = detalleOpt.get();

            // Restricción Crítica: Validar que cuente con la marca de pesaje realizado antes de proceder
            if (dc.getEstadopesaje() == null || dc.getEstadopesaje() != 99) {
                return ResponseEntity.badRequest().body("{\"error\": \"La boleta no puede generarse si la unidad no ha completado su pesaje físico.\"}");
            }

            // Obtener el ID del Pesaje Externo desde la cuenta madre para completar los campos obligatorios
            String sqlPesaje = "SELECT idpesaje FROM beneficio.cuentas WHERE nocuenta = ? LIMIT 1";
            Long idPesajeExterno = jdbcTemplate.queryForObject(sqlPesaje, Long.class, dc.getNocuenta());

            // Insertamos la Boleta en el esquema dedicado de Peso Cabal
            String sqlInsertBoleta = "INSERT INTO pesocabal.boletas (nocuenta, idparcialidad, idpesaje, placa, cuitransportista, pesobascula, tipomedida, usuarioorigen, fechaboleta) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'Quintales', ?, ?)";

            LocalDateTime fechaBoleta = LocalDateTime.now();
            jdbcTemplate.update(sqlInsertBoleta, dc.getNocuenta(), dc.getNoparcialidad(), idPesajeExterno, dc.getPlaca(), dc.getCuitransportista(), dc.getPesorecibido(), usuarioLogueado, fechaBoleta);

            // Mapeamos la respuesta exacta que exige el reporte de impresión en el Front
            Map<String, Object> boletaReporte = new HashMap<>();
            boletaReporte.put("fechaBoleta", fechaBoleta);
            boletaReporte.put("usuario", usuarioLogueado);
            boletaReporte.put("cuenta", dc.getNocuenta());
            boletaReporte.put("idPesaje", idPesajeExterno);
            boletaReporte.put("idParcialidad", dc.getNoparcialidad());
            boletaReporte.put("placa", dc.getPlaca());
            boletaReporte.put("cui", dc.getCuitransportista());
            boletaReporte.put("pesoObtenido", dc.getPesorecibido());

            return ResponseEntity.ok(boletaReporte);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Error al emitir el comprobante físico: " + e.getMessage() + "\"}");
        }
    }
}