package com.sfa.repository;

import com.sfa.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, String> {
    List<Permission> findAllByOrderByCategoryAscLabelAsc();
}
