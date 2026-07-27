package com.sfa.service;

import com.sfa.dto.CurrentMileageDto;
import com.sfa.dto.MileageLogDto;
import com.sfa.entity.MileageLog;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.MileageLogRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MileageLogService {

    private final MileageLogRepository mileageLogRepo;
    private final UserRepository       userRepo;

    /** Whether this user has an open session (needs end mileage before logout), or —
     *  if not — their last closed session's end mileage, shown as a reference on the
     *  start-mileage screen. */
    @Transactional(readOnly = true)
    public CurrentMileageDto getCurrentStatus(UUID userId) {
        var open = mileageLogRepo.findByUserIdAndEndMileageIsNull(userId);
        if (open.isPresent()) {
            return new CurrentMileageDto(open.get().getStartMileage(), null);
        }
        BigDecimal lastEndMileage = mileageLogRepo.findFirstByUserIdAndEndMileageIsNotNullOrderByEndedAtDesc(userId)
                .map(MileageLog::getEndMileage)
                .orElse(null);
        return new CurrentMileageDto(null, lastEndMileage);
    }

    public MileageLogDto recordStart(UUID userId, BigDecimal startMileage) {
        if (mileageLogRepo.findByUserIdAndEndMileageIsNull(userId).isPresent()) {
            throw new BusinessException("You already have an open mileage session — record end mileage before starting a new one");
        }
        if (startMileage == null || startMileage.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Start mileage must be zero or greater");
        }
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        MileageLog log = MileageLog.builder()
                .user(user)
                .logDate(LocalDate.now())
                .startMileage(startMileage)
                .startedAt(Instant.now())
                .build();
        return MileageLogDto.from(mileageLogRepo.save(log));
    }

    public MileageLogDto recordEnd(UUID userId, BigDecimal endMileage) {
        MileageLog log = mileageLogRepo.findByUserIdAndEndMileageIsNull(userId)
                .orElseThrow(() -> new BusinessException("Record start mileage before end mileage"));
        if (endMileage == null || endMileage.compareTo(log.getStartMileage()) < 0) {
            throw new BusinessException("End mileage cannot be less than start mileage");
        }

        log.setEndMileage(endMileage);
        log.setEndedAt(Instant.now());
        return MileageLogDto.from(mileageLogRepo.save(log));
    }

    @Transactional(readOnly = true)
    public Page<MileageLogDto> getReport(UUID userId, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        return mileageLogRepo.findLogs(userId, dateFrom, dateTo, pageable).map(MileageLogDto::from);
    }
}
