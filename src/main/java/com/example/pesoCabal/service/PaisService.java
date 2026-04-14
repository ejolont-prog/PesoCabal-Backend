package com.example.pesoCabal.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.pesoCabal.model.Pais;
import com.example.pesoCabal.repository.PaisRepository;

import java.util.List;

@Service
public class PaisService {


    @Autowired
    private PaisRepository paisRepository;


    public List<Pais> findAll() {
        return paisRepository.findAll();
    }


}

