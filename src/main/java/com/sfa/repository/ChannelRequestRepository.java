package com.sfa.repository;

import com.sfa.entity.ChannelRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChannelRequestRepository extends JpaRepository<ChannelRequest, UUID> {

    Page<ChannelRequest> findByStatus(ChannelRequest.Status status, Pageable pageable);

    long countByStatus(ChannelRequest.Status status);

    @Query("""
        SELECT r FROM ChannelRequest r
        WHERE r.status = com.sfa.entity.ChannelRequest$Status.PENDING AND LOWER(r.email) = LOWER(:email)
        """)
    Optional<ChannelRequest> findPendingByEmail(@Param("email") String email);

    @Query(value = "SELECT nextval('channel_request_seq')", nativeQuery = true)
    long nextReferenceNumber();
}
