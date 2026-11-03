package com.conciliador.conciliador.repository;

import com.conciliador.conciliador.model.SinonimoDinamico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SinonimoDinamicoRepository extends JpaRepository<SinonimoDinamico, Long> {
    Optional<SinonimoDinamico> findByTermoOrigem(String termoOrigem);
}