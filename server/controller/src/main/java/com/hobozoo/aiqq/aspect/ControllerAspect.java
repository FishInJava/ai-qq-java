package com.hobozoo.aiqq.aspect;

import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Aspect
public class ControllerAspect {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /**
     * controller目录下，所有类，所有方法
     */
    @Pointcut("execution(* com.hobozoo.aiqq.controller.*.*(..) )")
    public void controllerLogPointCut(){

    }

    @Around("controllerLogPointCut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable{
        String methodName = pjp.getSignature().getName();
        log.info("{}--request：{} ",methodName, JSON_MAPPER.writeValueAsString(pjp.getArgs()));
        try {
            Object proceed = pjp.proceed();
            log.info("{}--response：{} ",methodName, JSON_MAPPER.writeValueAsString(proceed));
            return proceed;
        } catch (Throwable throwable) {
            log.error("proceed exception，{}",throwable.toString());
            throw throwable;
        }
    }

}
