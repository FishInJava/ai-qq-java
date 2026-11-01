package com.hobozoo.aiqq.service.rag;

import org.springframework.stereotype.Component;

import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.PipelineConfig;

import tools.jackson.databind.ObjectMapper;

/**
 * 读取管线定义并按 JSON 映射参数。
 */
@Component
public class PipelineConfigService {

    private final LibraryDao library;
    private final ObjectMapper mapper;

    public PipelineConfigService(LibraryDao library, ObjectMapper mapper) {
        this.library = library;
        this.mapper = mapper;
    }

    public PipelineConfig load(String name, int version) {
        String definition = library.pipelineDefinition(name, version);
        return mapper.readValue(definition, PipelineConfig.class);
    }

    public int currentVersion(String name) {
        Integer version = library.currentPipelineVersion(name);
        return version == null ? 1 : version;
    }
}
