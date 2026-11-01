package com.hobozoo.aiqq.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/counter")
@Tag(name = "Counter")
public class CounterController {

    private final AtomicLong count = new AtomicLong();

    /**
     * 读取当前计数
     */
    @Operation(summary = "读取当前计数")
    @RequestMapping(method = RequestMethod.GET)
    public long get() {
        return count.get();
    }

    /**
     * 计数加一并返回
     */
    @Operation(summary = "计数加一")
    @RequestMapping(value = "/increment", method = RequestMethod.POST)
    public long increment() {
        return count.incrementAndGet();
    }
}
