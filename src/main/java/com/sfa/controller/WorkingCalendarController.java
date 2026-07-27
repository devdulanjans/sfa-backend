package com.sfa.controller;

import com.sfa.service.WorkingCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/working-calendar")
@RequiredArgsConstructor
public class WorkingCalendarController {

    private final WorkingCalendarService calendarService;

    @GetMapping("/weekdays")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Map<String, Object> getWeekdays() {
        return Map.of("days", calendarService.getWorkingWeekdayAbbreviations());
    }

    @PutMapping("/weekdays")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Map<String, Object> setWeekdays(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> days = (List<String>) body.get("days");
        calendarService.updateWorkingWeekdays(days);
        return Map.of("days", calendarService.getWorkingWeekdayAbbreviations());
    }
}
