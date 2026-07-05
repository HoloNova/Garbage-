package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.Cabinet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CabinetRepository extends JpaRepository<Cabinet, Long> {

    Optional<Cabinet> findByCabinetId(String cabinetId);
}
