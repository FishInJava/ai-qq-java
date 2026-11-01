-- JustRAG V2：引用落库，历史消息可还原引用卡片。

create table query_citations (
    id          bigserial primary key,
    query_id    text    not null references queries (id) on delete cascade,
    idx         int     not null,
    chunk_id    bigint  not null,
    document_id bigint  not null,
    doc         text    not null,
    page_from   int     not null,
    page_to     int     not null,
    section     text    not null default '',
    snippet     text    not null default '',
    candidate   boolean not null default false
);

create index ix_query_citations_query on query_citations (query_id, idx);
