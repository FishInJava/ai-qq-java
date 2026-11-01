package com.hobozoo.aiqq.service.rag;

import com.hobozoo.aiqq.entity.rag.PipelineConfig;

import org.springframework.stereotype.Component;

import com.hobozoo.aiqq.dao.LibraryDao;

import tools.jackson.databind.ObjectMapper;

/**
 * 读取管线定义并按 JSON 映射参数。
 */
@Component
public class PipelineConfigService {

    private final LibraryDao repo;
    private final ObjectMapper mapper;

    public PipelineConfigService(LibraryDao repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public PipelineConfig load(String name, int version) {
        String definition = repo.pipelineDefinition(name, version);
        return mapper.readValue(definition, PipelineConfig.class);
    }

    public int currentVersion(String name) {
        Integer version = repo.currentPipelineVersion(name);
        return version == null ? 1 : version;
    }
}
