package com.indivaragroup.ageninlite.controller.downline;

import com.indivaragroup.ageninlite.common.dto.ApiResponse;
import com.indivaragroup.ageninlite.dto.downline.DownlineDetailResponseDto;
import com.indivaragroup.ageninlite.service.downline.DownlinerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/downliners")
@RequiredArgsConstructor
public class DownlinerController {

    private final DownlinerService downlinerService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DownlineDetailResponseDto>> getDownlinerDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
            ) {
        UUID requesterId = UUID.fromString(userId);
        Pageable pageable = PageRequest.of(page, size);

        DownlineDetailResponseDto response = downlinerService.getDownlineDetail(requesterId, id, pageable);

        return ResponseEntity.ok(new ApiResponse<>(true, "Downliner detail fetched successfully", response));
    }

}
