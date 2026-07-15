package com.sfa.repository;

import com.sfa.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
    boolean existsByName(String name);
}
