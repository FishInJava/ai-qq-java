package com.hobozoo.aiqq.entity.demo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class Result<T> {

    private String returnCode;

    private String returnMessage;

    private T data;

}
