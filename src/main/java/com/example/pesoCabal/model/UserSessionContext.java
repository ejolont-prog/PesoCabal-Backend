package com.example.pesoCabal.model;

public class UserSessionContext {
    private Long idUsuario;
    private Long idPerfil;

    public UserSessionContext(Long idUsuario, Long idPerfil) {
        this.idUsuario = idUsuario;
        this.idPerfil = idPerfil;
    }
    // Getters
    public Long getIdUsuario() { return idUsuario; }
    public Long getIdPerfil() { return idPerfil; }
}