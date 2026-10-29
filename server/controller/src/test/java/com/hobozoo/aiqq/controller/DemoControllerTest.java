package com.hobozoo.aiqq.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /demo/ping 的 HTTP 契约：
 * - 只接受 POST；
 * - name=ping 时返回 name=pong；
 * - 其他输入返回空 Cat，而不是报错。
 */
class DemoControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DemoController()).build();
    }

    @Test
    void ping_shouldReturnPong() throws Exception {
        mvc.perform(post("/demo/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ping\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("pong"));
    }

    @Test
    void nonPing_shouldReturnEmptyCat() throws Exception {
        mvc.perform(post("/demo/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nullValue()))
                .andExpect(jsonPath("$.id").value(nullValue()));
    }

    @Test
    void missingName_shouldReturnEmptyCat() throws Exception {
        mvc.perform(post("/demo/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"cat-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nullValue()))
                .andExpect(jsonPath("$.id").value(nullValue()));
    }

    @Test
    void nullName_shouldReturnEmptyCat() throws Exception {
        mvc.perform(post("/demo/ping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nullValue()));
    }

    @Test
    void ping_shouldRejectGet() throws Exception {
        mvc.perform(get("/demo/ping"))
                .andExpect(status().isMethodNotAllowed());
    }
}
