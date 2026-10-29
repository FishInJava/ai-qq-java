package com.hobozoo.aiqq.service.thread;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * sequence 的行为契约：
 * - 空输入返回已完成的空列表；
 * - 结果顺序与输入顺序一致；
 * - 任一 future 失败时，组合结果以 CompletionException 失败。
 */
class CompletableFutureDemoTest {

    @Test
    void sequence_empty_shouldReturnEmptyList() {
        List<CompletableFuture<String>> futures = List.of();

        List<String> result = CompletableFutureDemo.sequence(futures).join();

        assertThat(result).isEmpty();
    }

    @Test
    void sequence_shouldPreserveInputOrder() {
        List<String> result = CompletableFutureDemo.sequence(List.of(
                CompletableFuture.completedFuture("first"),
                CompletableFuture.completedFuture("second"),
                CompletableFuture.completedFuture("third")
        )).join();

        assertThat(result).containsExactly("first", "second", "third");
    }

    @Test
    void sequence_whenOneFutureFails_shouldFail() {
        CompletableFuture<String> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("upstream failed"));

        CompletableFuture<List<String>> combined = CompletableFutureDemo.sequence(List.of(
                CompletableFuture.completedFuture("ok"),
                failed
        ));

        assertThatThrownBy(combined::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("upstream failed");
    }
}
