package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import com.slidtable.slidtab_pro.dto.PickupJob;
import com.slidtable.slidtab_pro.dto.request.PickupRequest;
import com.slidtable.slidtab_pro.dto.request.ReserveRequest;
import com.slidtable.slidtab_pro.dto.request.ReturnRequest;
import com.slidtable.slidtab_pro.dto.protocol.StatusReport;
import com.slidtable.slidtab_pro.dto.response.BorrowRecordView;
import com.slidtable.slidtab_pro.dto.response.ReserveResponse;
import com.slidtable.slidtab_pro.service.BorrowService;
import com.slidtable.slidtab_pro.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/borrow")
public class BorrowController {

    private final BorrowService borrowService;
    private final UserService userService;

    public BorrowController(BorrowService borrowService, UserService userService) {
        this.borrowService = borrowService;
        this.userService = userService;
    }

    @PostMapping("/reserve")
    public ApiResponse<ReserveResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        return ApiResponse.success(borrowService.reserve(request.userId(), request.itemId()));
    }

    @PostMapping("/pickup")
    public ApiResponse<PickupJob> pickup(@Valid @RequestBody PickupRequest request) {
        return ApiResponse.success(borrowService.pickup(request.recordId(), request.userId()));
    }

    @GetMapping("/pickup/{recordId}/status")
    public ApiResponse<PickupJob> pickupStatus(@PathVariable Long recordId) {
        return ApiResponse.success(borrowService.getPickupJob(recordId));
    }

    @PostMapping("/return")
    public ApiResponse<StatusReport> returnItem(@Valid @RequestBody ReturnRequest request) {
        return ApiResponse.success(borrowService.returnItem(request.userId(), request.itemId(), request.remark()));
    }

    @GetMapping("/history/{userId}")
    public ApiResponse<List<BorrowRecordView>> history(@PathVariable String userId) {
        userService.getByUserId(userId);
        return ApiResponse.success(borrowService.history(userId));
    }
}
