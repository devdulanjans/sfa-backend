package com.sfa.service;

import com.sfa.entity.SystemSetting;
import com.sfa.entity.TargetHoliday;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.SystemSettingRepository;
import com.sfa.repository.TargetHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The global working-day calendar used to spread a monthly sales target into a daily one.
 * The weekday pattern is stored as a single system_settings row ("working_days", e.g.
 * "MON,TUE,WED,THU,FRI,SAT") rather than a dedicated table — it's a scalar, so it reuses the
 * existing settings infrastructure. Date-specific holiday exceptions get their own small table
 * (target_holidays) since that's a growing list, not a scalar.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkingCalendarService {

    private static final String WORKING_DAYS_KEY = "working_days";
    private static final String DEFAULT_WORKING_DAYS = "MON,TUE,WED,THU,FRI,SAT";

    private final SystemSettingRepository settingRepo;
    private final TargetHolidayRepository holidayRepo;

    @Transactional(readOnly = true)
    public Set<DayOfWeek> getWorkingWeekdays() {
        String value = settingRepo.findById(WORKING_DAYS_KEY)
                .map(SystemSetting::getValue)
                .orElse(DEFAULT_WORKING_DAYS);
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(WorkingCalendarService::parseDay)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    /** Sorted "MON".."SUN" abbreviations, for API responses. */
    @Transactional(readOnly = true)
    public List<String> getWorkingWeekdayAbbreviations() {
        return getWorkingWeekdays().stream()
                .sorted()
                .map(WorkingCalendarService::formatDay)
                .toList();
    }

    /** Accepts raw "MON".."SUN" abbreviations from the API request — parsing/validation lives
     *  here, not duplicated in the controller. */
    public void updateWorkingWeekdays(List<String> dayAbbreviations) {
        Set<DayOfWeek> days = dayAbbreviations.stream()
                .map(WorkingCalendarService::parseDay)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        String value = days.stream()
                .sorted()
                .map(WorkingCalendarService::formatDay)
                .collect(Collectors.joining(","));
        SystemSetting setting = settingRepo.findById(WORKING_DAYS_KEY)
                .orElseThrow(() -> new ResourceNotFoundException("System setting 'working_days' not found"));
        setting.setValue(value);
        setting.setUpdatedAt(Instant.now());
        settingRepo.save(setting);
    }

    @Transactional(readOnly = true)
    public boolean isWorkingDay(LocalDate date) {
        if (!getWorkingWeekdays().contains(date.getDayOfWeek())) return false;
        return holidayRepo.findByHolidayDateBetweenOrderByHolidayDate(date, date).isEmpty();
    }

    /** Inclusive on both ends. Deliberately a plain day-by-day loop — a month is at most 31
     *  iterations, no need for a closed-form calculation. */
    @Transactional(readOnly = true)
    public int countWorkingDays(LocalDate fromInclusive, LocalDate toInclusive) {
        Set<DayOfWeek> workingDays = getWorkingWeekdays();
        Set<LocalDate> holidays = holidayRepo.findByHolidayDateBetweenOrderByHolidayDate(fromInclusive, toInclusive)
                .stream().map(TargetHoliday::getHolidayDate).collect(Collectors.toSet());

        int count = 0;
        for (LocalDate d = fromInclusive; !d.isAfter(toInclusive); d = d.plusDays(1)) {
            if (workingDays.contains(d.getDayOfWeek()) && !holidays.contains(d)) count++;
        }
        return count;
    }

    private static DayOfWeek parseDay(String abbrev) {
        return switch (abbrev.toUpperCase(Locale.ENGLISH)) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown weekday: " + abbrev);
        };
    }

    private static String formatDay(DayOfWeek day) {
        return day.name().substring(0, 3);
    }
}
