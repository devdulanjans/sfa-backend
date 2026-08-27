package com.sfa.repository;

import com.sfa.entity.Reason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReasonRepository extends JpaRepository<Reason, UUID> {
    List<Reason> findByTypeOrderBySortOrderAscLabelAsc(Reason.ReasonType type);

    boolean existsByTypeAndLabelIgnoreCase(Reason.ReasonType type, String label);
}
