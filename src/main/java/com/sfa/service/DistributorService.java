package com.sfa.service;

import com.sfa.dto.distributor.CreateDistributorRequest;
import com.sfa.dto.distributor.DistributorDto;
import com.sfa.entity.Distributor;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.DistributorRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DistributorService {

    private final DistributorRepository distributorRepository;
    private final UserRepository         userRepository;

    public Page<DistributorDto> list(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return distributorRepository.search(search.trim(), pageable).map(DistributorDto::from);
        }
        return distributorRepository.findAll(pageable).map(DistributorDto::from);
    }

    public DistributorDto getById(UUID id) {
        return DistributorDto.from(findOrThrow(id));
    }

    public List<DistributorDto> getByUser(UUID userId) {
        return distributorRepository.findByUserId(userId)
                .stream().map(DistributorDto::from).toList();
    }

    @Transactional
    public DistributorDto create(CreateDistributorRequest req) {
        if (distributorRepository.existsByCode(req.code())) {
            throw new BusinessException("Distributor code already exists: " + req.code());
        }
        Distributor d = Distributor.builder()
                .code(req.code().toUpperCase())
                .name(req.name())
                .address(req.address())
                .phone(req.phone())
                .email(req.email())
                .build();
        return DistributorDto.from(distributorRepository.save(d));
    }

    @Transactional
    public DistributorDto update(UUID id, CreateDistributorRequest req) {
        Distributor d = findOrThrow(id);
        if (!d.getCode().equals(req.code()) && distributorRepository.existsByCode(req.code())) {
            throw new BusinessException("Distributor code already exists: " + req.code());
        }
        d.setCode(req.code().toUpperCase());
        d.setName(req.name());
        d.setAddress(req.address());
        d.setPhone(req.phone());
        d.setEmail(req.email());
        return DistributorDto.from(distributorRepository.save(d));
    }

    @Transactional
    public DistributorDto toggleStatus(UUID id) {
        Distributor d = findOrThrow(id);
        d.setStatus(d.getStatus() == Distributor.DistributorStatus.ACTIVE
                ? Distributor.DistributorStatus.INACTIVE
                : Distributor.DistributorStatus.ACTIVE);
        return DistributorDto.from(distributorRepository.save(d));
    }

    @Transactional
    public void assignUser(UUID distributorId, UUID userId) {
        Distributor d = findOrThrow(distributorId);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (u.getDistributors().contains(d)) {
            throw new BusinessException("User already assigned to this distributor");
        }
        u.getDistributors().add(d);
        userRepository.save(u);
    }

    @Transactional
    public void unassignUser(UUID distributorId, UUID userId) {
        Distributor d = findOrThrow(distributorId);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        u.getDistributors().remove(d);
        userRepository.save(u);
    }

    private Distributor findOrThrow(UUID id) {
        return distributorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", id));
    }
}
