package com.hobozoo.aiqq.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiqqException 的行为契约：
 * - errType 用于区分异常类别，必须原样保留；
 * - message / cause 必须传递给 RuntimeException。
 */
class AiqqExceptionTest {

    @Test
    void shouldExposeErrorTypeAndMessage() {
        AiqqException exception = new AiqqException(AiqqException.INPUT_ERROR, "name is required");

        assertThat(exception.getErrType()).isEqualTo("InputError");
        assertThat(exception).hasMessage("name is required");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void shouldPreserveCauseWhenProvided() {
        IllegalStateException cause = new IllegalStateException("database is down");

        AiqqException exception = new AiqqException(AiqqException.SYS_ERROR, "query failed", cause);

        assertThat(exception.getErrType()).isEqualTo("SysError");
        assertThat(exception).hasMessage("query failed").hasCause(cause);
    }

    @Test
    void shouldAllowNullMessage() {
        AiqqException exception = new AiqqException(AiqqException.RUNTIME_ERROR, null);

        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getErrType()).isEqualTo("RuntimeError");
    }

    @Test
    void shouldAllowNullErrorType() {
        AiqqException exception = new AiqqException(null, "unknown failure");

        assertThat(exception.getErrType()).isNull();
        assertThat(exception).hasMessage("unknown failure");
    }

    @Test
    void shouldBeUncheckedException() {
        AiqqException exception = new AiqqException(AiqqException.KEY_MISSING, "key");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
