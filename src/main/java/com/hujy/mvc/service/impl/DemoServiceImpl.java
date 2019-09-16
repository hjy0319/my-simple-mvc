package com.hujy.mvc.service.impl;

import com.hujy.annotation.MyService;
import com.hujy.mvc.service.DemoService;

@MyService
public class DemoServiceImpl implements DemoService {
    @Override
    public String hello(String name) {
        return "hello " + name;
    }
}
