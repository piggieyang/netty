package com.nettyDemo.Client;

import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import com.nettyDemo.Client.handler.LoginResponseHandler;
import com.nettyDemo.Client.handler.MessageResponseHandler;
import com.nettyDemo.codec.PacketDecoder;
import com.nettyDemo.codec.PacketEncoder;
import com.nettyDemo.codec.Spliter;
import com.nettyDemo.protocal.request.LoginRequestPacket;
import com.nettyDemo.protocal.request.MessageRequestPacket;
import com.nettyDemo.util.SessionUtil;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @author 王皓
 */
public class NettyClient {
	private static final int MAX_RETRY = 5;
	
    public static void main(String[] args) throws InterruptedException {       
        
        //在客户端程序中，group对应了我们IOClient.java中main函数起的线程,死循环发消息。
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        //客户端启动的引导类是 Bootstrap
        Bootstrap bootstrap = new Bootstrap();
        bootstrap
        		// 1.指定线程模型
        		.group(workerGroup)
        		// 2.指定 IO 类型为 NIO
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)//连接的超时时间
                .option(ChannelOption.SO_KEEPALIVE, true)//表示是否开启 TCP 底层心跳机制
                .option(ChannelOption.TCP_NODELAY, true)//表示是否开始 Nagle 算法
                // 3.IO 处理逻辑
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                    	
                    	ch.pipeline().addLast(new Spliter());
                    	ch.pipeline().addLast(new PacketDecoder());//添加一个解码逻辑处理器
                    	ch.pipeline().addLast(new LoginResponseHandler());//添加一个逻辑处理器
                    	ch.pipeline().addLast(new MessageResponseHandler());//添加一个逻辑处理器
                    	ch.pipeline().addLast(new PacketEncoder());//添加一个编码逻辑处理器
                    	
                    }
                });

        // 4.建立连接
        connect(bootstrap, "127.0.0.1", 1000, MAX_RETRY);
    }
    
    private static void connect(Bootstrap bootstrap, String host, int port,int retry) {
        bootstrap.connect(host, port).addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("连接成功!");
                Channel channel = ((ChannelFuture) future).channel();
                // 连接成功之后，启动控制台线程
                startConsoleThread(channel);                
            } else if (retry == 0) {
                System.err.println("重试次数已用完，放弃连接！");
            } else {
                // 第几次重连
                int order = (MAX_RETRY - retry) + 1;
                // 本次重连的间隔
                int delay = 1 << order;
                System.err.println(new Date() + ": 连接失败，第" + order + "次重连……");
                bootstrap.config().group().schedule(() -> connect(bootstrap, host, port, retry - 1), delay, TimeUnit
                        .SECONDS);
                //bootstrap.config() 这个方法返回的是 BootstrapConfig，
                //他是对 Bootstrap 配置参数的抽象，然后 bootstrap.config().group() 
                //返回的就是我们在一开始的时候配置的线程模型 workerGroup，
                //调 workerGroup 的 schedule 方法即可实现定时任务逻辑
            }
        });
    }
    
    @SuppressWarnings("resource")
	private static void startConsoleThread(Channel channel) {
        Scanner sc = new Scanner(System.in);
        LoginRequestPacket loginRequestPacket = new LoginRequestPacket();
    	
        new Thread(() -> {
            while (!Thread.interrupted()) {
                if (!SessionUtil.hasLogin(channel)) {
                    System.out.print("输入用户名登录: ");
                    String username = sc.nextLine();
                    loginRequestPacket.setUserName(username);
                    
                    // 密码使用默认的
                    loginRequestPacket.setPassword("pwd");   
                    
                    // 发送登录数据包
                    channel.writeAndFlush(loginRequestPacket);
                    waitForLoginResponse();
                }else {
                    String toUserId = sc.next();
                    String message = sc.next();
                    channel.writeAndFlush(new MessageRequestPacket(toUserId, message));

                }
            }
        }).start();
    }
    
        
    private static void waitForLoginResponse() {
          try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
    }   
    
}