package com.hobozoo.aiqq.entity.rag;

/**
 * eval_cases 表的一行。
 */
public record EvalCaseRow(long id, String question, String kind, String expectedDoc, Integer pageFrom,
                          Integer pageTo, String[] keywords, String expectedSources) {
}
