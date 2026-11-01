package com.hobozoo.aiqq.aspect;

import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

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
        log.info("{}--request：{} ",methodName, json(pjp.getArgs()));
        try {
            Object proceed = pjp.proceed();
            log.info("{}--response：{} ",methodName, json(proceed));
            return proceed;
        } catch (Throwable throwable) {
            log.error("proceed exception，{}",throwable.toString(),throwable);
            throw throwable;
        }
    }

    /**
     * 日志序列化，跳过流
     */
    private static String json(Object value) {
        if (value instanceof Object[] args) {
            List<String> items = new ArrayList<>();
            for (Object arg : args) {
                items.add(describe(arg));
            }
            return "[" + String.join(", ", items) + "]";
        }
        return describe(value);
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ResponseEntity<?> response) {
            return response.getStatusCode() + " " + describe(response.getBody());
        }
        if (value instanceof MultipartFile file) {
            return "MultipartFile(" + file.getOriginalFilename() + ", " + file.getSize() + "B)";
        }
        if (value instanceof Resource || value instanceof SseEmitter) {
            return value.getClass().getSimpleName();
        }
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (RuntimeException e) {
            return String.valueOf(value);
        }
    }

}
