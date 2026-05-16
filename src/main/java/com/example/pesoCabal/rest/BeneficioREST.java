package com.example.pesoCabal.rest;

import com.example.pesoCabal.dto.CatalogoDTO;
import com.example.pesoCabal.model.Cuenta;
import com.example.pesoCabal.model.DetalleCuenta;
import com.example.pesoCabal.dto.CuentaDTO;
import com.example.pesoCabal.repository.CatalogoRepository;
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
import java.time.LocalDate;
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
    @Autowired
    private CatalogoRepository catalogoRepository;

    @GetMapping("/catalogos/estados-pesaje")
    public ResponseEntity<List<CatalogoDTO>> obtenerEstadosPesaje() {
        return ResponseEntity.ok(catalogoRepository.findEstadosPesajeBeneficio());
    }
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
    // 3. ACCIÓN PRINCIPAL: "Actualizar Peso" (Formulario de Báscula) con Automatismo de Estado 30 y Cálculos de Tolerancia


    // 3. ACCIÓN PRINCIPAL: "Actualizar Peso" (Formulario de Báscula) con Automatismo de Estado 30, Cálculos y Sincronización con Agricultor
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

            // Capturamos las observaciones enviadas por el usuario desde el Front
            String observacionesForm = payload.get("observaciones") != null ? payload.get("observaciones").toString() : "";

            // Buscar la parcialidad/detalle enviado para conocer su noCuenta asociado
            Optional<DetalleCuenta> detalleOpt = detalleRepo.findById(id);
            if (detalleOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"No existe la parcialidad especificada.\"}");
            }
            DetalleCuenta detalle = detalleOpt.get();

            // CORRECCIÓN CRÍTICA DE COLUMNA: Se cambia c.idestadopesaje por c.estadopesaje
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
                return ResponseEntity.badRequest().body("{\"error\": \"La cuenta se encuentra en estado: " + nombreMostrar + " no es posible ingresar pesajes.\"}");
            }

            // PROCESAMIENTO Y PERSISTENCIA (Campos físicos de la tabla)
            detalle.setPesorecibido(pesoObtenido);
            detalle.setEstadopesaje(100); // Estado interno de la parcialidad: Pesaje Realizado

            // Guardamos el texto fijo automático exigido por la regla de negocio
            detalle.setTextorechazado("Pesaje Realizado");

            // Guardamos las observaciones capturadas del formulario de la báscula
            detalle.setObservaciones(observacionesForm);

            // Se registra la fecha actual del pesaje tal como pide el flujo
            detalle.setFecharecepcion(LocalDateTime.now());

            // 1. Guardamos la parcialidad actual
            detalleRepo.save(detalle);

            // =========================================================================
            // AUTOMATISMO EN BACKEND: VERIFICACIÓN, CAMBIO A ESTADO 30 Y CÁLCULOS
            // =========================================================================
            String noCuentaAsociada = detalle.getNocuenta();

            // Consultamos si quedan parcialidades de esta misma cuenta sin pesar
            String sqlContarPendientes = "SELECT COUNT(*) FROM beneficio.detallecuenta " +
                    "WHERE nocuenta = ? AND (estadopesaje IS NULL OR estadopesaje != 100) AND eliminado = false";

            Long pendientes = jdbcTemplate.queryForObject(sqlContarPendientes, Long.class, noCuentaAsociada);

            String mensajeExtra = "";
            // Si ya no quedan camiones/parcialidades pendientes (conteo es 0)
            if (pendientes != null && pendientes == 0) {

                // A) Obtener el peso total esperado de la cuenta madre
                String sqlGetEsperado = "SELECT pesototalesperado FROM beneficio.cuentas WHERE nocuenta = ? LIMIT 1";
                BigDecimal pesoTotalEsperado = jdbcTemplate.queryForObject(sqlGetEsperado, BigDecimal.class, noCuentaAsociada);

                if (pesoTotalEsperado == null) {
                    pesoTotalEsperado = BigDecimal.ZERO;
                }

                // B) Sumar todos los pesos reales guardados en los detalles de esta cuenta
                String sqlSumRecibido = "SELECT COALESCE(SUM(pesorecibido), 0) FROM beneficio.detallecuenta WHERE nocuenta = ? AND eliminado = false";
                BigDecimal pesoTotalRecibido = jdbcTemplate.queryForObject(sqlSumRecibido, BigDecimal.class, noCuentaAsociada);

                // C) Operaciones matemáticas precisas con BigDecimal (Tolerancia del 5%)
                BigDecimal porcentajeTolerancia = new BigDecimal("0.05");
                BigDecimal toleranciaCalculada = pesoTotalEsperado.multiply(porcentajeTolerancia); // +/- 5%
                BigDecimal diferenciaTotal = pesoTotalRecibido.subtract(pesoTotalEsperado);         // Recibido - Esperado

                // D) Definir etiqueta según el rango de tolerancia
                String resultadoToleranciaLabel = "Aceptado, en parametro";

                // Si la diferencia es menor que el negativo de la tolerancia -> Faltante crítico
                if (diferenciaTotal.compareTo(toleranciaCalculada.negate()) < 0) {
                    resultadoToleranciaLabel = "Faltante";
                }
                // Si la diferencia es mayor que la tolerancia positiva -> Sobrante crítico
                else if (diferenciaTotal.compareTo(toleranciaCalculada) > 0) {
                    resultadoToleranciaLabel = "Sobrante";
                }

                // E) UPDATE masivo de la cuenta en Beneficio con los cálculos y el Estado (30)
                String sqlUpdateCuentaCalculos = "UPDATE beneficio.cuentas SET " +
                        "estadopesaje = 30, " +
                        "pesototalrecibido = ?, " +
                        "diferenciatotal = ?, " +
                        "tolerancia = ?, " +
                        "resultadotolerancia = ? " +
                        "WHERE nocuenta = ?";

                jdbcTemplate.update(sqlUpdateCuentaCalculos,
                        pesoTotalRecibido,
                        diferenciaTotal,
                        toleranciaCalculada,
                        resultadoToleranciaLabel,
                        noCuentaAsociada
                );

                mensajeExtra = " Todas las parcialidades completadas. Cuenta actualizada en Beneficio a 'Pesaje Finalizado' [" + resultadoToleranciaLabel + "].";

                // =========================================================================
                // NOTIFICAR AL BACKEND DEL AGRICULTOR VIA HTTP (CAMBIO A ESTADO 167)
                // =========================================================================
                try {
                    org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

                    // Cambia esta URL por el puerto/ruta real del backend del Agricultor
                    String urlAgricultor = "http://localhost:8081/api/agricultor/cuentas/actualizar-estado";

                    // Armamos el JSON payload dinámico con el nocuenta obtenido y el ID de estado solicitado
                    Map<String, Object> requestAgricultor = new HashMap<>();
                    requestAgricultor.put("nocuenta", noCuentaAsociada);
                    requestAgricultor.put("estado", 167);

                    // Configurar encabezados HTTP estándar
                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

                    org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(requestAgricultor, headers);

                    // Se envía la petición via POST
                    org.springframework.http.ResponseEntity<String> respuestaApi = restTemplate.postForEntity(urlAgricultor, entity, String.class);

                    if (respuestaApi.getStatusCode().is2xxSuccessful()) {
                        mensajeExtra += " Sincronizado exitosamente con Agricultor (Estado 167).";
                    }
                } catch (Exception httpEx) {
                    // Al estar en bloques try-catch separados, si el API de agricultor está apagada,
                    // el pesaje local del beneficio de igual forma se guardará con éxito.
                    mensajeExtra += " Advertencia: No se pudo conectar con el módulo de Agricultor para actualizar al estado 167 (" + httpEx.getMessage() + ").";
                }
                // =========================================================================
            }
            // =========================================================================

            // Retornamos el éxito incluyendo la bandera informativa del automatismo
            return ResponseEntity.ok("{\"mensaje\": \"Se actualizó con éxito el peso de la parcialidad." + mensajeExtra + "\"}");

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
            if (dc.getEstadopesaje() == null || dc.getEstadopesaje() != 100) {
                return ResponseEntity.badRequest().body("{\"error\": \"La boleta no puede generarse si la unidad no ha completado su pesaje físico.\"}");
            }

            // Leer idunidadpeso desde la cuenta madre
            String sqlPesaje = "SELECT idunidadpeso FROM beneficio.cuentas WHERE nocuenta = ? LIMIT 1";
            Long idUnidadMedida = jdbcTemplate.queryForObject(sqlPesaje, Long.class, dc.getNocuenta());

            // INSERT apuntando a pesocabal.pesajecabal con sus columnas reales
            String sqlInsertBoleta = "INSERT INTO pesocabal.pesajecabal (nocuenta, parcialidad, pesoobtenido, idunidadmedida, fechapesaje, observaciones, creadopor) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            // Cambia LocalDateTime a LocalDate para fechapesaje (tipo date en BD)
            java.time.LocalDate fechaBoleta = java.time.LocalDate.now();

            // 🚀 CORRECCIÓN DE TIPOS DE DATOS:
            // 1. Convertir la parcialidad a String de forma explícita para varchar(50)
            String parcialidadStr = String.valueOf(dc.getNoparcialidad());

            // 2. Controlar 'creadopor' que es int4 en la Base de Datos
            Integer idUsuarioCreador = 3; // ID por defecto por si viene un texto (ej: "Usuario_PesoCabal_UMG")
            try {
                // Si el header llegara a enviar el ID numérico en texto (ej: "45"), lo parsea automáticamente
                idUsuarioCreador = Integer.parseInt(usuarioLogueado);
            } catch (NumberFormatException e) {
                // Si no es un número, se mantiene el ID por defecto (1) para evitar el fallo de SQL grammar
            }

            // Ejecutar la inserción con los tipos de datos exactos que exige PostgreSQL
            jdbcTemplate.update(sqlInsertBoleta,
                    dc.getNocuenta(),
                    parcialidadStr,       // ✅ String para varchar(50)
                    dc.getPesorecibido(),
                    idUnidadMedida,
                    fechaBoleta,          // ✅ LocalDate para date
                    dc.getObservaciones(),
                    idUsuarioCreador      // ✅ Integer para int4
            );

            // Mapeamos la respuesta para el reporte de impresión en el Front
            Map<String, Object> boletaReporte = new HashMap<>();
            boletaReporte.put("fechaBoleta", fechaBoleta);
            boletaReporte.put("usuario", usuarioLogueado); // Se devuelve el string original para despliegue visual en Angular
            boletaReporte.put("cuenta", dc.getNocuenta());
            boletaReporte.put("idUnidadMedida", idUnidadMedida);
            boletaReporte.put("idParcialidad", dc.getNoparcialidad());
            boletaReporte.put("placa", dc.getPlaca());
            boletaReporte.put("cui", dc.getCuitransportista());
            boletaReporte.put("pesoObtenido", dc.getPesorecibido());
            boletaReporte.put("observaciones", dc.getObservaciones());

            return ResponseEntity.ok(boletaReporte);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Error al emitir el comprobante físico: " + e.getMessage() + "\"}");
        }
    }
    // === NUEVO ENDPOINT AGREGADO PARA SOLUCIONAR EL ERROR 403 ===
    // Permite leer cualquier catálogo dinámico por su ID (ej. el catálogo 3 de unidades de medida)
    @GetMapping("/catalogos/{id}")
    public ResponseEntity<?> obtenerCatalogoPorId(@PathVariable Long id) {
        try {

            String sql = "SELECT id AS idopcioncatalogo, detallecatalogo AS nombreopcion " +
                    "FROM beneficio.catalogos " +
                    "WHERE idcatalogo = ? AND eliminado = false ORDER BY id ASC";

            // Si tu tabla de catálogos no usa "idcatalogo" sino que "id" es el identificador,
            // puedes ajustarlo según la lógica de tu base de datos.
            List<Map<String, Object>> catalogo = jdbcTemplate.queryForList(sql, id);

            return ResponseEntity.ok(catalogo);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\": \"Error al leer el catálogo: " + e.getMessage() + "\"}");
        }
    }
}