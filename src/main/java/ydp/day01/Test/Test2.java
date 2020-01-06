package ydp.day01.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;

public class Test2 {

    public static void main(String[] args) throws IOException {

        //建立通信，三次握手
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(80));
        //是否为阻塞,这里设置为非堵塞
        serverSocketChannel.configureBlocking(false);
        //创建存放socket的容器
        Selector selector = Selector.open();
        //注册身份，这里是selector自身，所以是SelectionKey.OP_ACCEPT
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
//        System.out.println("Http Server Started.");
        while (true) {
            if (selector.select(3000) == 0) {
                continue;
            }
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                httpHandle(key);
                keyIterator.remove();
            }
        }
    }

    private static void httpHandle(SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            acceptHandle(key);
        } else if (key.isReadable()) {
            requestHandle(key);
        }
    }

    private static void acceptHandle(SelectionKey key) throws IOException {
        SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(key.selector(), SelectionKey.OP_READ, ByteBuffer.allocate(1024));
    }

    private static void requestHandle(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = (ByteBuffer) key.attachment();
        byteBuffer.clear();
        if (socketChannel.read(byteBuffer) == -1) {
            socketChannel.close();
            return;
        }
        byteBuffer.flip();
        String requestMsg = new String(byteBuffer.array());
        String url = requestMsg.split("\r\n")[0].split(" ")[1];
        System.out.println(requestMsg);
        System.out.println("Request: " + url);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("HTTP/1.1 200 OK\r\n");
        stringBuilder.append("Content-Type:text/html;charset=utf-8\r\n");
        stringBuilder.append("\r\n");
        stringBuilder.append("<html><head><title>HttpTest</title></head><body>");

        String res = myres(url);
        stringBuilder.append(res);

//        String content = HttpServer.contentMap.get(url);
//        stringBuilder.append(content != null ? content : "404");
        stringBuilder.append("</body></html>");

        socketChannel.write(ByteBuffer.wrap(stringBuilder.toString().getBytes()));
        socketChannel.close();
    }

    private static String myres(String url) {
        if (url.contains(".ico"))
            return "";
        String[] split = url.split("\\?");
        String s = "404";
        try {
            Class<?> aClass = Class.forName("ydp.day01.Test.UserController");
            Method[] methods = aClass.getDeclaredMethods();
            UserController userController = (UserController) aClass.getDeclaredConstructor().newInstance();

            for (Method method : methods) {
                //判断是否是注解
                if (method.isAnnotationPresent(MyRequestMapping.class)) {
                    //判断url是否指向该方法
                    if (split[0].replace("/", "").equals(method.getName())) {
                        if (split.length > 1) {
                            String[] paras = split[1].split("&");
                            HashMap<String, String> map = new HashMap<>();
                            for (String para : paras) {
                                String[] split1 = para.split("=");
                                map.put(split1[0], split1[1]);
                            }
                            //判断类型
                            Parameter[] parameters = method.getParameters();
                            Object[] parameters2 = new Object[parameters.length];
//                            System.out.println("1111111111111111111111??"+parameters2.length);
//                            System.out.println("1111111111111111111111??"+parameters.length);
                            for (int i = 0; i < parameters.length; i++) {
                                String type = parameters[i].getType().getSimpleName();
                                if (type.equals("int")) {
                                    //这里如果不设置idea的-parameter时会空指针异常；
                                    parameters2[i] = Integer.parseInt(map.get(parameters[i].getName()));
                                }
                            }
                            s = (String) method.invoke(userController,parameters2);
                        } else {
                            s = (String) method.invoke(userController);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

}