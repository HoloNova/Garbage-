package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.ActionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionTemplateRepository extends JpaRepository<ActionTemplate, Long> {

    boolean existsByName(String name);
}
