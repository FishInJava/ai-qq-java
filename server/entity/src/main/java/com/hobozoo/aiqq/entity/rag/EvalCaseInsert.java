package com.hobozoo.aiqq.entity.rag;

/**
 * 写入 eval_cases 表的一组值。
 */
public record EvalCaseInsert(String question, String kind, String expectedDoc, Integer pageFrom, Integer pageTo,
                             String[] keywords, String expectedSourcesJson) {
}
