package ydp.day01.Test;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ServerTest2 {
    // IOC 容器
    public static Map<String, Object> beanMap = new HashMap<>();
    // URL映射，RequestMapping
    public static Map<String, MethodInfo> methodMap = new HashMap<>();

    public static void main(String[] args) throws IOException {

        //调用扫描包的方法
        refreshBeanFactory("ydp.day01.Test");

        //建立服务器端socket
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(80));
        //是否为阻塞
        serverSocketChannel.configureBlocking(false);
        //创建存放socket的容器
        Selector selector = Selector.open();
        //将服务器端socket注册到selector中
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (true) {

            if (selector.select(3000) <= 0) {
                continue;
            }
            //对selector容器中每一个注册的socket进行迭代，迭代完之后并remove
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                handle(key);
                //迭代完之后移除
                keyIterator.remove();
            }
        }
    }

    private static void handle(SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            acceptHandle(key);
        } else if (key.isReadable()) {
            requestHandle(key);
        }
    }

    //  服务器端处理连接请求，将客户端socketChannel注册到selector中
    private static void acceptHandle(SelectionKey key) throws IOException {
        SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
        //设置为非阻塞
        socketChannel.configureBlocking(false);
        socketChannel.register(key.selector(), SelectionKey.OP_READ, ByteBuffer.allocate(1024));
    }

    private static void requestHandle(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        //byte字节Buffer流 -> 字节流
        ByteBuffer byteBuffer = (ByteBuffer) key.attachment();
        //clear：设置为写模式
        byteBuffer.clear();

        //从socket中读取内容到byteBuffer
        if (socketChannel.read(byteBuffer) == -1) {
            socketChannel.close();
            return;
        }

        byteBuffer.flip();
        String requestMsg = new String(byteBuffer.array());
        String url = requestMsg.split("\r\n")[0].split(" ")[1];

        List<String> urlPramas = new ArrayList<>();
        urlParamsParse(url, urlPramas);

        System.out.println(url);

        url = url.split("\\?")[0];
        String content = null;
        try {
            content = methodInvoke(url, urlPramas);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        if (content == null)
            content = "404";
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("HTTP/1.1 200 OK\r\n");
        stringBuilder.append("Content-Type:text/html;charset=utf-8\r\n\r\n");
        stringBuilder.append(content);
        socketChannel.write(ByteBuffer.wrap(stringBuilder.toString().getBytes()));
        socketChannel.close();
    }

    // 调用url所映射的方法
    private static String methodInvoke(String url, List<String> urlParams) throws InvocationTargetException, IllegalAccessException {
        MethodInfo methodInfo = methodMap.get(url);
        // 如果url中参数个数与方法中参数个数不一致，则返回404
        if (methodInfo == null) {
            return "404";
        }
        String className = methodInfo.getClassName();
        Method method = methodInfo.getMethod();
        Object beanObj = beanMap.get(className);
        Object[] params = new Object[urlParams.size()];
        Parameter[] parameters = method.getParameters();
        // 如果url中参数个数与方法中参数个数不一致，则返回404
        if (params.length != parameters.length) {
            return "参数个数不匹配";
        }
        // 按照方法中参数的属性，来进行参数转换和填充
        int i = 0;
        for (Parameter p : parameters) {
            String type = p.getType().getSimpleName();
            String pName = p.getName();
            boolean flag = false;
            for (String p2 : urlParams) {
                String pp[] = p2.split("=");
                if (pName.equals(pp[0])) {
                    // 根据类型进行参数转换
                    Object pValue = paramTranslate(type, pp[1]);
                    params[i++] = pValue;
                    flag = true;
                    continue;
                }
            }
            if (!flag)
                return "参数名称不匹配";
        }
        return (String) method.invoke(beanObj, params);
    }

    // 1. 初始化 beanMap
    // 2. 初始化 methodMap
    private static void refreshBeanFactory(String packages) throws UnsupportedEncodingException {
        //将路径中的"."转换为"/"
        String path = packages.replace(".", "/");
        //从 class：ServerTest中获取资源
        URL url = ServerTest2.class.getClassLoader().getResource(path);
        //获取报名对用的文件夹
        File rootPkgDir = new File(URLDecoder.decode(url.getPath(), "utf-8"));
        //对每个文件加进行递归遍历
        beanParse(rootPkgDir);

    }

    private static void beanParse(File dir) {
        //如果不是文件夹就直接结束方法
        if (!dir.isDirectory())
            return;
        //过滤并获取目录下的所有文件
        File[] files = dir.listFiles(pathname -> {
            //如果pathname是文件夹就递归遍历
            if (pathname.isDirectory()) {
                beanParse(pathname);
                return false;
            }
            //如果是个类就返回做处理
            return pathname.getName().endsWith(".class");
        });
        for (File f : files) {
            //获取绝对路径
            String path = f.getAbsolutePath();
            //获取包名和类名
            String className = path.split("classes\\\\")[1].replace("\\", ".").split("\\.class")[0];
            try {
                Class<?> cls = Class.forName(className);
                MyRestController myRestController = cls.getAnnotation(MyRestController.class);
                // 处理 MyRestController注解的类
                if (myRestController != null) {
                    controllerParse(cls);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //处理MyRestController注解的类
    private static void controllerParse(Class<?> cls) {
        try {
            // IOC的容器注入
            beanMap.put(cls.getSimpleName(), cls.newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 解析  MyRequestMapping注解的方法，初始化  methodMap
        Method[] methods = cls.getDeclaredMethods();
        for (Method method : methods) {
            MyRequestMapping myRequestMapping = method.getDeclaredAnnotation(MyRequestMapping.class);
            if (myRequestMapping == null)
                continue;
            String url = myRequestMapping.value();
            methodMap.put(url, new MethodInfo(method, cls.getSimpleName()));
        }


    }


    private static Object paramTranslate(String type, String s) {
        switch (type) {
            case "int":
                return Integer.valueOf(s);
            case "double":
                return Double.valueOf(s);
            case "float":
                return Float.valueOf(s);
            default:
                return s;
        }
    }

    // 解析url参数
    private static void urlParamsParse(String url, List<String> urlPramas) {
        if (!url.contains("?"))
            return;
        // hello?id=1&name=dd&ssss => id=1&name=dd&ssss => [id=1, name=dd, ssss]
        String[] ps = url.replaceFirst(".*?\\?", "").split("&");
        for (String p : ps) {
            //对数据进行过滤 将 [id=1, name=dd, ssss]中的ssss过滤掉
            if (!p.contains("="))
                continue;
            urlPramas.add(p);
        }
    }


}