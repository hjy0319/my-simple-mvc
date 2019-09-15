package com.hujy.controller;

import com.hujy.annotation.MyAutowired;
import com.hujy.annotation.MyController;
import com.hujy.annotation.MyRequestMapping;
import com.hujy.annotation.MyRequestParam;
import com.hujy.service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/demo")
public class DemoController {

    @MyAutowired
    private DemoService demoService;

    @MyRequestMapping("/hello")
    public void hello(HttpServletRequest req, HttpServletResponse resp, @MyRequestParam("name") String name) {
        String result = demoService.hello(name);
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
