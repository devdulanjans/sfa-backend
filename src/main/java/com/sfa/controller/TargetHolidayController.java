package com.sfa.controller;

import com.sfa.entity.TargetHoliday;
import com.sfa.repository.TargetHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/target-holidays")
@RequiredArgsConstructor
public class TargetHolidayController {

    private final TargetHolidayRepository holidayRepo;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public List<TargetHoliday> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate t = to != null ? to : f.plusYears(1);
        return holidayRepo.findByHolidayDateBetweenOrderByHolidayDate(f, t);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public TargetHoliday create(@RequestBody Map<String, Object> body) {
        LocalDate date = LocalDate.parse((String) body.get("holidayDate"));
        String description = (String) body.get("description");
        return holidayRepo.save(TargetHoliday.builder()
                .holidayDate(date)
                .description(description)
                .build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        holidayRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
