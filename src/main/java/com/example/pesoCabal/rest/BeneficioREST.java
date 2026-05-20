package com.example.pesoCabal.rest;

import com.example.pesoCabal.dto.CatalogoDTO;
import com.example.pesoCabal.model.Cuenta;
import com.example.pesoCabal.model.DetalleCuenta;
import com.example.pesoCabal.dto.CuentaDTO;
import com.example.pesoCabal.repository.CatalogoRepository;
import com.example.pesoCabal.repository.CuentaRepository;
import com.example.pesoCabal.repository.DetalleCuentaRepository;
import jakarta.transaction.Transactional;
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
import java.time.LocalDate;

@RestController
@RequestMapping("/api/beneficio")
@CrossOrigin(origins = "*")
public class BeneficioREST {

    @Autowired
    private CuentaRepository cuentaRepo;

    @Autowired
    private DetalleCuentaRepository detalleRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogoRepository catalogoRepository;

    // =========================================================================
    // CATÁLOGOS
    // =========================================================================

    @GetMapping("/catalogos/estados-pesaje")
    public ResponseEntity<List<CatalogoDTO>> obtenerEstadosPesaje() {
        return ResponseEntity.ok(catalogoRepository.findEstadosPesajeBeneficio());
    }

    @GetMapping("/catalogos/{id}")
    public ResponseEntity<?> obtenerCatalogoPorId(@PathVariable Long id) {
        try {
            String sql = "SELECT id AS idopcioncatalogo, detallecatalogo AS nombreopcion " +
                    "FROM beneficio.catalogos " +
                    "WHERE idcatalogo = ? AND eliminado = false ORDER BY id ASC";
            List<Map<String, Object>> catalogo = jdbcTemplate.queryForList(sql, id);
            return ResponseEntity.ok(catalogo);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Error al leer el catálogo: " + e.getMessage() + "\"}");
        }
    }

    // =========================================================================
    // 1. BANDEJA PRINCIPAL
    // =========================================================================

    @GetMapping("/cuentas")
    public List<CuentaDTO> listarCuentas() {
        return cuentaRepo.findByEliminadoFalseWithAgricultor();
    }

    // =========================================================================
    // 2. DETALLE DE PARCIALIDADES
    // =========================================================================

    @GetMapping("/cuentas/{noCuenta}/detalles")
    public List<DetalleCuenta> listarDetalles(@PathVariable String noCuenta) {
        return detalleRepo.findDetallesValidosParaBeneficio(noCuenta);
    }

    @PostMapping("/detalles/{id}/pesar")
    @Transactional
    public ResponseEntity<?> pesarParcialidad(@PathVariable Integer id, @RequestBody Map<String, Object> payload) {
        try {
            if (payload.get("peso") == null) {
                return ResponseEntity.badRequest().body("{\"error\": \"El peso obtenido es obligatorio.\"}");
            }

            BigDecimal pesoObtenido = new BigDecimal(payload.get("peso").toString());
            String tipoMedida = payload.get("tipoMedida") != null ? payload.get("tipoMedida").toString() : "1";
            String observacionesForm = payload.get("observaciones") != null ? payload.get("observaciones").toString() : "";

            Optional<DetalleCuenta> detalleOpt = detalleRepo.findById(id);
            if (detalleOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"No existe la parcialidad especificada.\"}");
            }
            DetalleCuenta detalle = detalleOpt.get();

            // Verificar estado de la cuenta madre
            String sqlCheck = "SELECT c.estadopesaje, cat.detallecatalogo FROM beneficio.cuentas c " +
                    "LEFT JOIN beneficio.catalogos cat ON c.estadopesaje = cat.id WHERE c.nocuenta = ? LIMIT 1";
            List<Map<String, Object>> resultados = jdbcTemplate.queryForList(sqlCheck, detalle.getNocuenta());

            if (resultados.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"No se encontró la cuenta principal asociada a este detalle.\"}");
            }

            Map<String, Object> resultadoCuenta = resultados.get(0);
            Integer idEstadoActual = (Integer) resultadoCuenta.get("estadopesaje");
            String estadoNombreActual = (String) resultadoCuenta.get("detallecatalogo");

            if (idEstadoActual == null) {
                return ResponseEntity.badRequest().body("{\"error\": \"La cuenta no posee un estado válido asignado.\"}");
            }
            if (idEstadoActual == 30) {
                return ResponseEntity.badRequest().body("{\"error\": \"Acción bloqueada: El pesaje global de esta cuenta ya ha sido finalizado.\"}");
            }
            if (idEstadoActual != 29) {
                String nombreMostrar = (estadoNombreActual != null) ? estadoNombreActual : "Desconocido";
                return ResponseEntity.badRequest().body("{\"error\": \"La cuenta se encuentra en estado: " + nombreMostrar + ". Solo se pueden pesar cuentas en 'Pesaje Iniciado'.\"}");
            }

            // Guardar en detallecuenta
            detalle.setPesorecibido(pesoObtenido);
            detalle.setEstadopesaje(100);
            detalle.setTextorechazado("Pesaje Realizado");
            detalle.setObservaciones(observacionesForm);
            detalle.setFecharecepcion(LocalDateTime.now());

            // 🔥 CORRECCIÓN AQUÍ: Forzar la escritura en la BD para que el JdbcTemplate lo pueda leer correctamente
            detalleRepo.saveAndFlush(detalle);

            // =========================================================================
            // INSERT EN pesocabal.pesajecabal con los datos capturados del formulario
            // =========================================================================
            String parcialidadIdStr = String.valueOf(detalle.getIddetallecuenta());

            String sqlCheckDup = "SELECT COUNT(*) FROM pesocabal.pesajecabal WHERE nocuenta = ? AND parcialidad = ?";
            Long yaExiste = jdbcTemplate.queryForObject(sqlCheckDup, Long.class, detalle.getNocuenta(), parcialidadIdStr);

            if (yaExiste == null || yaExiste == 0) {
                // Resolver unidad de medida: usar la del formulario, si falla usar la de la cuenta madre
                Integer idUnidadFinal;
                try {
                    idUnidadFinal = Integer.parseInt(tipoMedida);
                } catch (NumberFormatException nfe) {
                    String sqlUnidad = "SELECT idunidadpeso FROM beneficio.cuentas WHERE nocuenta = ? LIMIT 1";
                    Long idUnidadMadre = jdbcTemplate.queryForObject(sqlUnidad, Long.class, detalle.getNocuenta());
                    idUnidadFinal = (idUnidadMadre != null) ? idUnidadMadre.intValue() : 1;
                }

                String sqlInsert = "INSERT INTO pesocabal.pesajecabal " +
                        "(nocuenta, parcialidad, pesoobtenido, idunidadmedida, fechapesaje, observaciones, creadopor) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

                jdbcTemplate.update(sqlInsert,
                        detalle.getNocuenta(),
                        parcialidadIdStr,
                        pesoObtenido,
                        idUnidadFinal,
                        LocalDate.now(),
                        observacionesForm.isEmpty() ? "Sin observaciones" : observacionesForm,
                        3
                );
            }
            // =========================================================================

            // =========================================================================
            // AUTOMATISMO: CIERRE DE CUENTA SI YA NO HAY PARCIALIDADES PENDIENTES
            // =========================================================================
            String noCuentaAsociada = detalle.getNocuenta();

            String sqlContarPendientes = "SELECT COUNT(*) FROM beneficio.detallecuenta " +
                    "WHERE nocuenta = ? " +
                    "AND (estadopesaje IS NULL OR estadopesaje NOT IN (100, 68)) " +
                    "AND eliminado = false";
            Long pendientes = jdbcTemplate.queryForObject(sqlContarPendientes, Long.class, noCuentaAsociada);

            String mensajeExtra = "";
            if (pendientes != null && pendientes == 0) {

                String sqlGetEsperado = "SELECT pesototalesperado FROM beneficio.cuentas WHERE nocuenta = ? LIMIT 1";
                BigDecimal pesoTotalEsperado = jdbcTemplate.queryForObject(sqlGetEsperado, BigDecimal.class, noCuentaAsociada);
                if (pesoTotalEsperado == null) pesoTotalEsperado = BigDecimal.ZERO;

                String sqlSumRecibido = "SELECT COALESCE(SUM(pesorecibido), 0) FROM beneficio.detallecuenta WHERE nocuenta = ? AND eliminado = false";
                BigDecimal pesoTotalRecibido = jdbcTemplate.queryForObject(sqlSumRecibido, BigDecimal.class, noCuentaAsociada);

                BigDecimal porcentajeTolerancia = new BigDecimal("0.05");
                BigDecimal toleranciaCalculada = pesoTotalEsperado.multiply(porcentajeTolerancia);
                BigDecimal diferenciaTotal = pesoTotalRecibido.subtract(pesoTotalEsperado);

                String resultadoToleranciaLabel = "Aceptado, en parametro";
                if (diferenciaTotal.compareTo(toleranciaCalculada.negate()) < 0) {
                    resultadoToleranciaLabel = "Faltante";
                } else if (diferenciaTotal.compareTo(toleranciaCalculada) > 0) {
                    resultadoToleranciaLabel = "Sobrante";
                }

                String sqlUpdateCuenta = "UPDATE beneficio.cuentas SET " +
                        "estadopesaje = 30, " +
                        "pesototalrecibido = ?, " +
                        "diferenciatotal = ?, " +
                        "tolerancia = ?, " +
                        "resultadotolerancia = ? " +
                        "WHERE nocuenta = ?";

                jdbcTemplate.update(sqlUpdateCuenta,
                        pesoTotalRecibido,
                        diferenciaTotal,
                        toleranciaCalculada,
                        resultadoToleranciaLabel,
                        noCuentaAsociada
                );

                mensajeExtra = " Todas las parcialidades completadas. Cuenta actualizada a 'Pesaje Finalizado' [" + resultadoToleranciaLabel + "].";

                // Notificar al módulo Agricultor
                try {
                    org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                    String urlAgricultor = "http://localhost:8081/api/agricultor/cuentas/actualizar-estado";

                    Map<String, Object> requestAgricultor = new HashMap<>();
                    requestAgricultor.put("nocuenta", noCuentaAsociada);
                    requestAgricultor.put("estado", 167);

                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    org.springframework.http.HttpEntity<Map<String, Object>> entity =
                            new org.springframework.http.HttpEntity<>(requestAgricultor, headers);

                    org.springframework.http.ResponseEntity<String> respuestaApi =
                            restTemplate.postForEntity(urlAgricultor, entity, String.class);

                    if (respuestaApi.getStatusCode().is2xxSuccessful()) {
                        mensajeExtra += " Sincronizado exitosamente con Agricultor (Estado 167).";
                    }
                } catch (Exception httpEx) {
                    mensajeExtra += " Advertencia: No se pudo conectar con el módulo de Agricultor (" + httpEx.getMessage() + ").";
                }
            }

            return ResponseEntity.ok("{\"mensaje\": \"Se actualizó con éxito el peso de la parcialidad." + mensajeExtra + "\"}");

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Error en el proceso de pesaje: " + e.getMessage() + "\"}");
        }
    }


    // =========================================================================
    // 4. BOLETA — Solo consulta y devuelve datos para impresión, NO inserta nada
    // =========================================================================

    @PostMapping("/detalles/{id}/boleta")
    public ResponseEntity<?> generarBoleta(@PathVariable Integer id, @RequestHeader("X-User-Logged") String usuarioLogueado) {
        try {
            Optional<DetalleCuenta> detalleOpt = detalleRepo.findById(id);
            if (detalleOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"No se encontró el registro para generar boleta.\"}");
            }

            DetalleCuenta dc = detalleOpt.get();

            // Validar que ya fue pesado
            if (dc.getEstadopesaje() == null || dc.getEstadopesaje() != 100) {
                return ResponseEntity.badRequest().body("{\"error\": \"La boleta no puede generarse si la unidad no ha completado su pesaje físico.\"}");
            }

            // Verificar que el registro ya existe en pesajecabal (se insertó al pesar)
            String parcialidadIdStr = String.valueOf(dc.getIddetallecuenta());
            String sqlCheck = "SELECT COUNT(*) FROM pesocabal.pesajecabal WHERE nocuenta = ? AND parcialidad = ?";
            Long existeRegistro = jdbcTemplate.queryForObject(sqlCheck, Long.class, dc.getNocuenta(), parcialidadIdStr);

            if (existeRegistro == null || existeRegistro == 0) {
                return ResponseEntity.badRequest().body("{\"error\": \"No existe registro de pesaje en pesajecabal para esta parcialidad.\"}");
            }

            // Leer unidad de medida de la cuenta madre para el reporte
            String sqlUnidad = "SELECT idunidadpeso FROM beneficio.cuentas WHERE nocuenta = ? LIMIT 1";
            Long idUnidadMedida = jdbcTemplate.queryForObject(sqlUnidad, Long.class, dc.getNocuenta());

            // Armar respuesta para impresión sin tocar nada en la BD
            Map<String, Object> boletaReporte = new HashMap<>();
            boletaReporte.put("fechaBoleta", LocalDate.now());
            boletaReporte.put("usuario", usuarioLogueado);
            boletaReporte.put("cuenta", dc.getNocuenta());
            boletaReporte.put("idUnidadMedida", idUnidadMedida);
            boletaReporte.put("idParcialidad", dc.getNoparcialidad());
            boletaReporte.put("placa", dc.getPlaca());
            boletaReporte.put("cui", dc.getCuitransportista());
            boletaReporte.put("pesoObtenido", dc.getPesorecibido());
            boletaReporte.put("observaciones", dc.getObservaciones());

            return ResponseEntity.ok(boletaReporte);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Error al generar la boleta: " + e.getMessage() + "\"}");
        }
    }


    @GetMapping("/pesajes-realizados")
    public ResponseEntity<?> listarPesajesRealizados() {
        try {
            // Hacemos un JOIN entre el esquema pesocabal y beneficio para traer el detalle de la unidad
            String sql = "SELECT p.idpesajecabal AS idpesajecabal, " +
                    "p.nocuenta AS nocuenta, " +
                    "p.parcialidad AS parcialidad, " +
                    "p.pesoobtenido AS pesoobtenido, " +
                    "p.idunidadmedida AS idunidadmedida, " +
                    "c.detallecatalogo AS unidadmedidanombre, " + // <-- Traemos el nombre real de la medida
                    "p.fechapesaje AS fechapesaje, " +
                    "p.observaciones AS observaciones, " +
                    "p.creadopor AS creadopor, " +
                    "p.modificadopor AS modificadopor " +
                    "FROM pesocabal.pesajecabal p " +
                    "LEFT JOIN beneficio.catalogos c ON p.idunidadmedida = c.id " + // <-- El cruce de tablas
                    "ORDER BY p.idpesajecabal DESC";

            List<Map<String, Object>> pesajes = jdbcTemplate.queryForList(sql);
            return ResponseEntity.ok(pesajes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Error al obtener el listado de pesajes: " + e.getMessage() + "\"}");
        }
    }
}