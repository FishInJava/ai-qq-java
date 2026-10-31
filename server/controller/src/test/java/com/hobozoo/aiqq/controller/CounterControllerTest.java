package com.hobozoo.aiqq.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/counter 的 HTTP 契约：
 * GET 返回当前值；
 * POST increment 返回加一后的值。
 */
class CounterControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CounterController()).build();
    }

    /**
     * 初始值为零
     */
    @Test
    void initial_shouldBeZero() throws Exception {
        mvc.perform(get("/api/counter"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$").value(0));
    }

    /**
     * 每次调用加一
     */
    @Test
    void increment_shouldAddOne() throws Exception {
        mvc.perform(post("/api/counter/increment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));

        mvc.perform(post("/api/counter/increment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));

        mvc.perform(get("/api/counter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));
    }

    /**
     * 加一接口只收 POST
     */
    @Test
    void increment_shouldRejectGet() throws Exception {
        mvc.perform(get("/api/counter/increment"))
                .andExpect(status().isMethodNotAllowed());
    }
}
