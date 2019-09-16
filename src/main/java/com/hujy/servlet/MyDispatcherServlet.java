package com.hujy.servlet;

import com.hujy.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatcherServlet extends HttpServlet {

    //保存application.properties 配置文件中的内容
    private Properties contextConfig = new Properties();

    //保存扫描的所有的类名
    private List<String> classNames = new ArrayList<>();

    // ioc容器
    private Map<String, Object> ioc = new HashMap<>();

    //保存所有的Url和方法的映射关系
    private List<Handler> handlerMapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) {
        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3、初始化扫描到的类，并且将它们放入到IOC 容器之中
        doInstance();
        //4、完成依赖注入
        doAutowired();
        //5、初始化HandlerMapping
        initHandlerMapping();
        System.out.println("**************** My Spring MVC has been initialized successfully *********************");
    }

    /**
     * 加载配置文件
     *
     * @param contextConfigLocation
     * @return void
     * @author hujy
     * @date 2019-09-16 11:31
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream fis = null;
        try {
            fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
            //1、读取配置文件
            contextConfig.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != fis) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 扫描配置文件中指定的路径，将文件名以[包名].[文件名]的形式保存到集合中
     *
     * @param packageName
     * @return void
     * @author hujy
     * @date 2019-09-16 11:33
     */
    private void doScanner(String packageName) {
        //将所有的包路径转换为文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            //如果是文件夹，继续递归
            if (file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else {
                classNames.add(packageName + "." + file.getName().replace(".class", "").trim());
            }
        }
    }

    /**
     * 对象初始化，保存到IOC容器
     *
     * @param
     * @return void
     * @author hujy
     * @date 2019-09-16 11:46
     */
    private void doInstance() {
        if (classNames.size() == 0) {
            return;
        }

        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(MyController.class)) {
                    //默认将首字母小写作为beanName
                    String beanName = loweredFirstChar(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(MyService.class)) {

                    MyService service = clazz.getAnnotation(MyService.class);
                    String beanName = service.value();
                    //优先使用用户自定义的名字
                    if (!"".equals(beanName.trim())) {
                        ioc.put(beanName, clazz.newInstance());
                        continue;
                    }

                    //如果没有自定义名字，就将接口类型作为bean的名字
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 首字母转成小写
     *
     * @param str
     * @return java.lang.String
     * @author hujy
     * @date 2019-09-16 14:08
     */
    private String loweredFirstChar(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 依赖注入
     *
     * @param
     * @return void
     * @author hujy
     * @date 2019-09-16 14:13
     */
    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //拿到实例对象中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {

                if (!field.isAnnotationPresent(MyAutowired.class)) {
                    continue;
                }

                // 拿到autowired的属性名
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                // 设置私有属性的访问权限
                field.setAccessible(true);
                try {
                    // 为属性注入实例，这里没有考虑循环依赖的复杂场景
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    /**
     * 初始化url映射关系
     *
     * @param
     * @return void
     * @author hujy
     * @date 2019-09-16 14:41
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            // 这里是对持有MyController的bean做处理
            if (!clazz.isAnnotationPresent(MyController.class)) {
                continue;
            }

            String url = "";
            // 获取Controller的url配置
            if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                url = requestMapping.value();
            }

            // 获取Method的url配置
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {

                // 没有加RequestMapping注解的直接忽略
                if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                    continue;
                }

                // 映射URL
                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String regex = ("/" + url + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                System.out.println("mapping " + regex + "," + method);
            }
        }
    }

    /***
     * 请求处理
     * @author hujy
     * @date 2019-09-16 15:11
     * @param req
     * @param resp
     * @return void
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        try {
            Handler handler = checkUrl(req);

            if (handler == null) {
                // 如果没有匹配上，返回404错误
                resp.getWriter().write("404 Not Found");
                return;
            }

            // 获取方法的参数列表
            Class<?>[] paramTypes = handler.method.getParameterTypes();

            // 保存所有需要自动赋值的参数值
            Object[] paramValues = new Object[paramTypes.length];

            Map<String, String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                //如果找到匹配的对象，则开始填充参数值
                if (!handler.paramIndexMapping.containsKey(param.getKey())) {
                    continue;
                }
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index], value);
            }

            // 设置方法中的request和response对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;

            handler.method.invoke(handler.controller, paramValues);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * URL校验
     * @author hujy
     * @date 2019-09-16 16:00
     * @param req
     * @return com.hujy.servlet.MyDispatcherServlet.Handler
     */
    private Handler checkUrl(HttpServletRequest req) throws Exception {
        if (handlerMapping.isEmpty()) {
            return null;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            try {
                Matcher matcher = handler.pattern.matcher(url);
                // 如果没有匹配上继续下一个匹配
                if (!matcher.matches()) {
                    continue;
                }
                return handler;
            } catch (Exception e) {
                throw e;
            }
        }
        return null;
    }

    /**
     * 类型转换
     * url传过来的参数都是String类型的，HTTP是基于字符串协议，只需要把String转换为任意类型就好
     * @author hujy
     * @date 2019-09-16 16:24
     * @param type
     * @param value
     * @return java.lang.Object
     */
    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        // 如果还有double等其他类型，可以利用策略模式继续扩展
        return value;
    }

    /**
     * 请求处理内部类
     * 记录Controller中的RequestMapping和Method的对应关系
     * @author hujy
     * @date 2019-09-16 14:50
     */
    private class Handler {

        // 保存方法对应的实例
        private Object controller;
        // 保存映射的方法
        private Method method;
        // url匹配正则
        private Pattern pattern;
        // 保存参数顺序
        private Map<String, Integer> paramIndexMapping;

        private Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<>();
            // url传过来的参数都是String类型的，HTTP是基于字符串协议，只需要把String转换为任意类型就好
            putParamIndexMapping(method);
        }

        /**
         * 保存参数在方法中的位置映射关系
         * @author hujy
         * @date 2019-09-16 16:02
         * @param method
         * @return void
         */
        private void putParamIndexMapping(Method method) {
            //提取方法中加了注解的参数
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]) {
                    if (a instanceof MyRequestParam) {
                        String paramName = ((MyRequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            //提取方法中的request和response参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }
        }
    }
}
