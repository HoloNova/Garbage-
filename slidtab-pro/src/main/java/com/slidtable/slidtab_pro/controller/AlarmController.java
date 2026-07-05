package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import com.slidtable.slidtab_pro.entity.AlarmRecord;
import com.slidtable.slidtab_pro.service.AlarmService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<AlarmRecord> handle(@PathVariable Long id,
                                           @RequestParam String handler,
                                           @RequestParam(required = false) String description) {
        return ApiResponse.success(alarmService.handle(id, handler, description));
    }
}
