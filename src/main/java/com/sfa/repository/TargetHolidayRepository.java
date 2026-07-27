package com.sfa.repository;

import com.sfa.entity.TargetHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TargetHolidayRepository extends JpaRepository<TargetHoliday, UUID> {

    List<TargetHoliday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);
}
