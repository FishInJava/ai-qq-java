package com.hobozoo.aiqq.entity.rag;

import java.util.List;
import java.util.Map;

/**
 * 管线页要的三块数据。
 */
public record PipelineSnapshot(DocumentRow document, List<PageRow> pages, List<RunRow> runs,
                               Map<Long, List<EventRow>> events, List<BlockRow> blocks) {
}
