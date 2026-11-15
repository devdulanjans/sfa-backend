package com.sfa.controller;

import com.sfa.dto.channelrequest.ChannelRequestDtos.ApprovalResult;
import com.sfa.dto.channelrequest.ChannelRequestDtos.ApproveRequest;
import com.sfa.dto.channelrequest.ChannelRequestDtos.ChannelRequestDto;
import com.sfa.dto.channelrequest.ChannelRequestDtos.Counts;
import com.sfa.dto.channelrequest.ChannelRequestDtos.RejectRequest;
import com.sfa.entity.ChannelRequest;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.ChannelRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Super admin review of "Create your SFA" requests submitted on the public website. */
@RestController
@RequestMapping("/api/channel-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Channel requests")
public class ChannelRequestController {

    private final ChannelRequestService service;

    @GetMapping
    public Page<ChannelRequestDto> list(
            @RequestParam(required = false) ChannelRequest.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/counts")
    public Counts counts() {
        return service.counts();
    }

    @GetMapping("/{id}")
    public ChannelRequestDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve: creates the channel, its Company Profile and the owner login")
    public ApprovalResult approve(@PathVariable UUID id,
                                  @Valid @RequestBody(required = false) ApproveRequest body,
                                  @AuthenticationPrincipal UserDetailsImpl user) {
        return service.approve(id, body, user.getId());
    }

    @PostMapping("/{id}/reject")
    public ChannelRequestDto reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest body,
                                    @AuthenticationPrincipal UserDetailsImpl user) {
        return service.reject(id, body.note(), user.getId());
    }
}
