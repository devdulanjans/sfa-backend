package com.sfa.repository;

import com.sfa.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    List<SubscriptionPlan> findByActiveTrueOrderBySortOrderAscNameAsc();

    Optional<SubscriptionPlan> findByCode(String code);
}
