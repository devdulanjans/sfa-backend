package com.sfa.repository;

import com.sfa.entity.CustomerCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerCategoryRepository extends JpaRepository<CustomerCategory, UUID> {}
