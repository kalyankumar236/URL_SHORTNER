package com.agentic.platform.orchestration.api;

import com.agentic.platform.common.PageResponse;
import com.agentic.platform.orchestration.domain.Workflow;
import com.agentic.platform.orchestration.dto.CreateWorkflowRequest;
import com.agentic.platform.orchestration.dto.GateResultResponse;
import com.agentic.platform.orchestration.dto.ReplanRequest;
import com.agentic.platform.orchestration.dto.TaskResponse;
import com.agentic.platform.orchestration.dto.WorkflowEventResponse;
import com.agentic.platform.orchestration.dto.WorkflowResponse;
import com.agentic.platform.orchestration.repo.GateResultRepository;
import com.agentic.platform.orchestration.repo.WorkflowEventRepository;
import com.agentic.platform.orchestration.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Orchestration workflow API. Reads are public; mutations (create/run/cancel/replan)
 * require ROLE_ADMIN, enforced by {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowEventRepository eventRepo;
    private final GateResultRepository gateRepo;

    public WorkflowController(WorkflowService workflowService, WorkflowEventRepository eventRepo,
                              GateResultRepository gateRepo) {
        this.workflowService = workflowService;
        this.eventRepo = eventRepo;
        this.gateRepo = gateRepo;
    }

    @PostMapping
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest req,
                                                   Authentication auth) {
        String actor = auth != null ? auth.getName() : "admin";
        Workflow wf = workflowService.create(req.requirement(), actor, req.taskInputs());
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + wf.getId()))
                .body(WorkflowResponse.from(wf));
    }

    @GetMapping
    public PageResponse<WorkflowResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        Page<Workflow> p = workflowService.list(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return PageResponse.of(p.map(WorkflowResponse::from));
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(@PathVariable String id) {
        return WorkflowResponse.from(workflowService.get(id));
    }

    @GetMapping("/{id}/tasks")
    public List<TaskResponse> tasks(@PathVariable String id) {
        return workflowService.tasks(id).stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/{id}/events")
    public List<WorkflowEventResponse> events(@PathVariable String id) {
        workflowService.get(id); // existence check
        return eventRepo.findByWorkflowIdOrderBySeqAsc(id).stream().map(WorkflowEventResponse::from).toList();
    }

    @GetMapping("/{id}/gates")
    public List<GateResultResponse> gates(@PathVariable String id) {
        workflowService.get(id);
        return gateRepo.findByWorkflowIdOrderByCreatedAtAsc(id).stream().map(GateResultResponse::from).toList();
    }

    @PostMapping("/{id}/run")
    public WorkflowResponse run(@PathVariable String id) {
        return WorkflowResponse.from(workflowService.run(id));
    }

    @PostMapping("/{id}/cancel")
    public WorkflowResponse cancel(@PathVariable String id, Authentication auth) {
        String actor = auth != null ? auth.getName() : "admin";
        return WorkflowResponse.from(workflowService.cancel(id, actor));
    }

    @PostMapping("/{id}/replan")
    public WorkflowResponse replan(@PathVariable String id,
                                   @RequestBody(required = false) ReplanRequest req,
                                   Authentication auth) {
        String actor = auth != null ? auth.getName() : "admin";
        if (req != null && req.fromTask() != null && !req.fromTask().isBlank()) {
            return WorkflowResponse.from(workflowService.replanFromTask(
                    id, req.fromTask(), req.reason(), actor, req.taskInputs()));
        }
        return WorkflowResponse.from(
                workflowService.replan(id, actor, req == null ? null : req.taskInputs()));
    }
}
