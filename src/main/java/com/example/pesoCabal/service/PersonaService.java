package com.example.pesoCabal.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.example.pesoCabal.model.Persona;
import com.example.pesoCabal.repository.PersonaRepository;

import java.util.List;
import java.util.Optional;

@Service
public class PersonaService {


    @Autowired
    private PersonaRepository personaRepository;

    public List<Persona> findAll() {
        return personaRepository.findByEstadoP("E");
    }

    public List<Persona> findAll(Sort sort) {
        return personaRepository.findAll(sort);
    }


    public Page<Persona> findAll(Pageable pageable) {
        return personaRepository.findAll(pageable);
    }

    public <S extends Persona> S save(S entity) {
        return personaRepository.save(entity);
    }

    public Optional<Persona> findById(Long id) {
        return personaRepository.findById(id);
    }


    public Boolean deleteById(Long id) {
        Persona persona =  personaRepository.findById(id).orElse(null);
        if (persona != null){
            persona.setEstadoP("D");
            personaRepository.save(persona);
            return true;
        }
        return false;
    }

    public void delete(Persona entity) {
        personaRepository.delete(entity);
    }



}

