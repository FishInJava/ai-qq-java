-- JustRAG V1：19 张表。表结构说明见 .agents/notes 里的架构笔记。

create extension if not exists vector;

-- ---------------------------------------------------------------- 文档与解析

create table documents (
    id               bigserial primary key,
    path             text        not null,
    sha256           char(64)    not null,
    filename         text        not null,
    title            text,
    category         text        not null default 'general',
    pages            int         not null default 0,
    bytes            bigint      not null default 0,
    language         text,
    encrypted        boolean     not null default false,
    structure_source text        not null default 'unknown',
    pipeline_version int,
    status           text        not null default 'pending',
    error            text,
    deleted_at       timestamptz,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create unique index ux_documents_sha256 on documents (sha256) where deleted_at is null;
create index ix_documents_status on documents (status) where deleted_at is null;

comment on column documents.structure_source is 'font_guess / toc / flat / unknown';

create table pages (
    id          bigserial primary key,
    document_id bigint       not null references documents (id) on delete cascade,
    page_no     int          not null,
    width       numeric(10, 2) not null default 0,
    height      numeric(10, 2) not null default 0,
    rotation    int          not null default 0,
    char_count  int          not null default 0,
    is_empty    boolean      not null default false,
    unique (document_id, page_no)
);

create table blocks (
    id           bigserial primary key,
    document_id  bigint           not null references documents (id) on delete cascade,
    page_no      int              not null,
    block_type   text             not null check (block_type in ('text', 'table', 'heading')),
    bbox_x0      double precision not null,
    bbox_y0      double precision not null,
    bbox_x1      double precision not null,
    bbox_y1      double precision not null,
    text         text             not null,
    order_no     int              not null,
    section_path text[]           not null default '{}',
    font_size    double precision,
    font_name    text
);

create index ix_blocks_doc_page on blocks (document_id, page_no, order_no);

create table chunks (
    id           bigserial primary key,
    document_id  bigint      not null references documents (id) on delete cascade,
    block_id     bigint      references blocks (id) on delete cascade,
    parent_id    bigint      references chunks (id) on delete cascade,
    level        text        not null check (level in ('parent', 'child')),
    text         text        not null,
    text_md      text,
    char_count   int         not null,
    page_from    int         not null,
    page_to      int         not null,
    section_path text[]      not null default '{}',
    sha256       char(64)    not null,
    ord          int         not null default 0,
    created_at   timestamptz not null default now()
);

create index ix_chunks_doc_level on chunks (document_id, level, ord);
create index ix_chunks_parent on chunks (parent_id);
create index ix_chunks_sha on chunks (document_id, sha256);

create table chunk_blocks (
    chunk_id bigint not null references chunks (id) on delete cascade,
    block_id bigint not null references blocks (id) on delete cascade,
    ord      int    not null default 0,
    primary key (chunk_id, block_id)
);

create table embeddings (
    id         bigserial primary key,
    chunk_id   bigint           not null references chunks (id) on delete cascade,
    model      text             not null,
    dim        int              not null,
    vector     vector(512)      not null,
    created_at timestamptz      not null default now(),
    unique (chunk_id, model)
);

create index ix_embeddings_hnsw on embeddings
    using hnsw (vector vector_cosine_ops) with (m = 16, ef_construction = 64);

-- ---------------------------------------------------------------- 管线

create table pipeline_def (
    id         bigserial primary key,
    name       text        not null,
    version    int         not null,
    definition jsonb       not null,
    created_at timestamptz not null default now(),
    unique (name, version)
);

create table pipeline_runs (
    id               bigserial primary key,
    document_id      bigint      not null references documents (id) on delete cascade,
    pipeline_def_id  bigint      not null references pipeline_def (id),
    attempt          int         not null default 1,
    status           text        not null default 'running',
    started_at       timestamptz not null default now(),
    finished_at      timestamptz,
    stats            jsonb       not null default '{}'::jsonb
);

create index ix_pipeline_runs_doc on pipeline_runs (document_id, attempt);

create table pipeline_events (
    id          bigserial primary key,
    run_id      bigint      not null references pipeline_runs (id) on delete cascade,
    node        text        not null,
    status      text        not null,
    started_at  timestamptz not null default now(),
    duration_ms int,
    detail      jsonb       not null default '{}'::jsonb,
    message     text
);

create index ix_pipeline_events_run on pipeline_events (run_id, id);

create table jobs (
    id          bigserial primary key,
    type        text        not null check (type in ('scan', 'upload', 'ingest')),
    source_path text,
    document_id bigint      references documents (id) on delete set null,
    status      text        not null default 'queued',
    stage       text,
    progress    int         not null default 0,
    total       int         not null default 0,
    done        int         not null default 0,
    error       text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index ix_jobs_status on jobs (status, id);

-- ---------------------------------------------------------------- 问答

create sequence sessions_seq;
create sequence queries_seq;

create table sessions (
    id          text primary key default 's_' || nextval('sessions_seq'),
    title       text,
    document_id bigint      references documents (id) on delete set null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table queries (
    id             text primary key default 'q_' || nextval('queries_seq'),
    session_id     text        not null references sessions (id) on delete cascade,
    request_id     text,
    question       text        not null,
    answer         text,
    model          text,
    tokens_in      int,
    tokens_out     int,
    latency_ms     int,
    first_token_ms int,
    status         text        not null default 'running',
    error          text,
    created_at     timestamptz not null default now()
);

create unique index ux_queries_request_id on queries (request_id) where request_id is not null;
create index ix_queries_session on queries (session_id, created_at);

create table query_stages (
    query_id    text   not null references queries (id) on delete cascade,
    attempt     int    not null default 1,
    stage       text   not null,
    duration_ms int,
    payload     jsonb  not null default '{}'::jsonb,
    primary key (query_id, attempt, stage)
);

create table prompt_snapshots (
    id            bigserial primary key,
    query_id      text        not null references queries (id) on delete cascade,
    attempt       int         not null default 1,
    system_prompt text        not null,
    user_prompt   text        not null,
    blocks        jsonb       not null default '[]'::jsonb,
    created_at    timestamptz not null default now()
);

create table feedback (
    id         bigserial primary key,
    query_id   text        not null references queries (id) on delete cascade,
    verdict    text        not null check (verdict in ('wrong', 'incomplete', 'ok')),
    note       text,
    created_at timestamptz not null default now()
);

create table eval_cases (
    id                  bigserial primary key,
    question            text        not null,
    kind                text        not null check (kind in ('pos', 'neg', 'cross')),
    expected_doc        text,
    expected_page_from  int,
    expected_page_to    int,
    expected_keywords   text[]      not null default '{}',
    expected_sources    jsonb       not null default '[]'::jsonb,
    created_at          timestamptz not null default now()
);

-- ---------------------------------------------------------------- 运维

create table index_retry (
    id         bigserial primary key,
    chunk_id   bigint      not null references chunks (id) on delete cascade,
    operation  text        not null,
    attempts   int         not null default 0,
    last_error text,
    next_at    timestamptz not null default now()
);

create index ix_index_retry_next on index_retry (next_at);

create table artifacts (
    id          bigserial primary key,
    document_id bigint      not null references documents (id) on delete cascade,
    kind        text        not null,
    payload     jsonb       not null default '{}'::jsonb,
    created_at  timestamptz not null default now()
);

create table app_config (
    key        text primary key,
    value      jsonb       not null,
    updated_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- 种子数据

insert into app_config (key, value)
values ('retrieval', '{
  "bm25": {"k1": 1.2, "b": 0.75, "topK": 40},
  "vector": {"topK": 40, "efSearch": 40, "minCosine": 0.40},
  "fusion": {"rrfK": 60, "topK": 20},
  "rerank": {"enabled": false, "topK": 8},
  "context": {"tokenBudget": 8000},
  "refusal": {"minFusionScore": 0.012},
  "queryInstruction": "为这个句子生成表示以用于检索相关文章："
}'::jsonb);

insert into pipeline_def (name, version, definition)
values ('rag', 1, '{
  "nodes": ["parse", "tables", "blocks", "chunk", "embed", "index_vector", "index_lexical", "finalize"],
  "chunk": {
    "parentTarget": 1200, "parentMin": 600, "parentMax": 1600,
    "childSize": 300, "childOverlap": 60, "maxTokens": 512
  },
  "heading": {
    "sizeRatio": 1.15, "boldSizeRatio": 1.05, "lineGapRatio": 1.3, "maxLevel": 3
  },
  "header": {"zoneRatio": 0.08, "yTolerance": 2.0, "minRepeatPages": 3, "maxLength": 50},
  "templates": [
    {"name": "research", "category": "research", "pathKeywords": ["研报", "证券", "策略"]},
    {"name": "magazine", "category": "magazine", "pathKeywords": ["TASC"], "minPages": 40},
    {"name": "general", "category": "general", "pathKeywords": []}
  ]
}'::jsonb);
