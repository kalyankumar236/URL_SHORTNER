package com.agentic.platform.orchestration.policy;

import java.util.List;

/**
 * Result of a mediated tool invocation.
 *
 * @param executed whether the tool ran
 * @param tool     the tool identifier
 * @param risk     risk classification applied
 * @param log      ordered, audit-friendly execution log (no secrets)
 */
public record ToolResult(boolean executed, String tool, String risk, List<String> log) {
}
