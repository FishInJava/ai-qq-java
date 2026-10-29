package com.hobozoo.aiqq.aspect;

import com.hobozoo.aiqq.entity.demo.Cat;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ControllerAspect 的行为契约：
 * - 正常返回时原样返回业务结果；
 * - 业务抛异常时原样向上抛，不吞异常。
 */
class ControllerAspectTest {

    private final ControllerAspect aspect = new ControllerAspect();
    private final ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    private final Signature signature = mock(Signature.class);

    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("findById");
        when(joinPoint.getArgs()).thenReturn(new Object[]{Map.of("name", "ping")});
    }

    @Test
    void shouldReturnProceedResult() throws Throwable {
        Cat expected = Cat.builder().name("pong").build();
        when(joinPoint.proceed()).thenReturn(expected);

        assertThat(aspect.around(joinPoint)).isSameAs(expected);
        verify(joinPoint).proceed();
    }

    @Test
    void shouldRethrowExceptionFromProceed() throws Throwable {
        IllegalStateException failure = new IllegalStateException("boom");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.around(joinPoint)).isSameAs(failure);
    }
}
