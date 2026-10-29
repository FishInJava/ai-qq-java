package com.hobozoo.aiqq.mq.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RetryAspect 的行为契约：
 * - 目标不实现 Retry 时，直接执行业务；
 * - 目标实现 Retry 且未超限时，执行业务；
 * - 超过重试次数时，返回 null 且不再执行业务。
 *
 * 注意：这里只测试切面方法的逻辑，不测试 pointcut 是否生效。
 */
class RetryAspectTest {

    private final RetryAspect aspect = new RetryAspect();
    private final ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

    @Test
    void nonRetryTarget_shouldProceed() throws Throwable {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.around(joinPoint)).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void retryTargetWithinLimit_shouldProceed() throws Throwable {
        Retry target = () -> Integer.MAX_VALUE;
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.around(joinPoint)).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void retryTargetOverLimit_shouldReturnNullWithoutProceeding() throws Throwable {
        Retry target = () -> -1;
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        assertThat(aspect.around(joinPoint)).isNull();
        verify(joinPoint, never()).proceed();
    }
}
