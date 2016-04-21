package com.opsunv.proxyagent;

import java.util.Arrays;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.handler.codec.socks.SocksAuthResponse;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksInitRequest;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksInitResponseDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;

public class SocksClient {
	public static void main(String[] args) {
		Bootstrap client = new Bootstrap();
		client.group(new NioEventLoopGroup())
			.channel(NioSocketChannel.class)
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
			.handler(new ChannelInitializer<SocketChannel>() {
				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(new SocksMessageEncoder());
				}
			});
		
		client.connect("localhost", 1080).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if(future.isSuccess()){
					Channel ch = future.channel();
					
					ch.pipeline().addLast(new SocksInitResponseDecoder());
					ch.pipeline().addLast(new SimpleChannelInboundHandler<SocksInitResponse>() {
						@Override
						protected void channelRead0(ChannelHandlerContext ctx, SocksInitResponse msg) throws Exception {
							ctx.pipeline().remove(this);
							System.out.println("客户端收到:"+msg.toString());
							ctx.writeAndFlush(new SocksCmdRequest(SocksCmdType.CONNECT, SocksAddressType.DOMAIN, "www.baidu.com", 80)).sync();
						}
					});
					
					ch.write(new SocksInitRequest(Arrays.asList(SocksAuthScheme.NO_AUTH)));
					ch.flush();
					System.out.println("aaa");
				}else{
					System.out.println("失败");
				}
			}
		});
	}
}
