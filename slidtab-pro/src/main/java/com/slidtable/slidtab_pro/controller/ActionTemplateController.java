package com.slidtable.slidtab_pro.controller;

import com.slidtable.slidtab_pro.common.ApiResponse;
import com.slidtable.slidtab_pro.common.BusinessException;
import com.slidtable.slidtab_pro.dto.protocol.ActionStep;
import com.slidtable.slidtab_pro.entity.ActionTemplate;
import com.slidtable.slidtab_pro.dto.request.ActionTemplateRequest;
import com.slidtable.slidtab_pro.repository.ActionTemplateRepository;
import com.slidtable.slidtab_pro.service.control.ActionExecutor;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 动作编排模板管理：管理员可视化编排设备动作链。
 * <p>
 * 模板不绑定图书，可独立调用 /run 派发到 ActionExecutor 执行。
 * sequenceJson 字段存 ActionStep 列表的 JSON 序列化字符串。
 * </p>
 */
@RestController
@RequestMapping("/api/action-template")
public class ActionTemplateController {

    private static final Logger log = LoggerFactory.getLogger(ActionTemplateController.class);

    private final ActionTemplateRepository repository;
    private final ActionExecutor actionExecutor;
    private final ObjectMapper objectMapper;

    public ActionTemplateController(ActionTemplateRepository repository,
                                   ActionExecutor actionExecutor,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.actionExecutor = actionExecutor;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<ActionTemplate>> list() {
        return ApiResponse.success(repository.findAll());
    }

    @PostMapping
    public ApiResponse<ActionTemplate> create(@Valid @RequestBody ActionTemplateRequest req) {
        if (repository.existsByName(req.name())) {
            throw new BusinessException(1002, "模板名已存在: " + req.name());
        }
        validateSequence(req.sequenceJson());
        ActionTemplate t = new ActionTemplate();
        t.setName(req.name());
        t.setDescription(req.description());
        t.setSequenceJson(req.sequenceJson());
        log.info("[模板创建] name={}, steps={}", req.name(), parse(req.sequenceJson()).size());
        return ApiResponse.success(repository.save(t));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActionTemplate> update(@PathVariable Long id, @Valid @RequestBody ActionTemplateRequest req) {
        ActionTemplate t = repository.findById(id)
                .orElseThrow(() -> new BusinessException(1004, "模板不存在: id=" + id));
        validateSequence(req.sequenceJson());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setSequenceJson(req.sequenceJson());
        log.info("[模板更新] id={}, name={}", id, req.name());
        return ApiResponse.success(repository.save(t));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(1004, "模板不存在: id=" + id);
        }
        repository.deleteById(id);
        log.info("[模板删除] id={}", id);
        return ApiResponse.success("ok");
    }

    /**
     * 立即试运行：解析模板动作序列并派发到 ActionExecutor。
     */
    @PostMapping("/{id}/run")
    public ApiResponse<Map<String, Object>> run(@PathVariable Long id) {
        ActionTemplate t = repository.findById(id)
                .orElseThrow(() -> new BusinessException(1004, "模板不存在: id=" + id));
        List<ActionStep> steps = parse(t.getSequenceJson());
        if (steps.isEmpty()) {
            return ApiResponse.error(1009, "模板未配置动作序列");
        }
        String jobId = "TPL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        actionExecutor.execute(jobId, 0L, steps);
        log.info("[模板试运行] 派发: templateId={}, name={}, jobId={}, steps={}",
                id, t.getName(), jobId, steps.size());
        return ApiResponse.success(Map.of(
                "jobId", jobId,
                "steps", steps.size(),
                "message", "模板动作序列已派发，请观察设备动作"
        ));
    }

    private void validateSequence(String json) {
        List<ActionStep> steps = parse(json);
        if (steps.isEmpty()) {
            throw new BusinessException(1001, "动作序列不能为空");
        }
    }

    private List<ActionStep> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ActionStep>>() {});
        } catch (Exception e) {
            throw new BusinessException(1001, "动作序列 JSON 解析失败: " + e.getMessage());
        }
    }
}
