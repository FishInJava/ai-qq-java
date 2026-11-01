package com.hobozoo.aiqq.configuration;

import com.hobozoo.aiqq.entity.demo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ExceptionHandler {

    @ResponseBody
    @org.springframework.web.bind.annotation.ExceptionHandler
    public Object handlerAllException(HttpServletResponse httpServletResponse,Exception exception){
        log.error("ex:{}",exception.toString());
        return new Result<Object>();
    }

}
