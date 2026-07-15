package com.sfa.repository;

import com.sfa.entity.SystemModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemModuleRepository extends JpaRepository<SystemModule, String> {
    List<SystemModule> findAllByOrderBySortOrderAsc();
}
