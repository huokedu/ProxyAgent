package com.opsunv.proxyagent;

import java.util.Arrays;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdResponseDecoder;
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
			.handler(new SocksMessageEncoder());
		
		client.connect("localhost", 8888).addListener(new ChannelFutureListener() {
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
							
							ctx.pipeline().addLast(new SocksCmdResponseDecoder());
							ctx.pipeline().addLast(new SimpleChannelInboundHandler<SocksCmdResponse>(){
	
									@Override
									protected void channelRead0(ChannelHandlerContext ctx, SocksCmdResponse msg)
											throws Exception {
										ChannelPipeline pipeline = ctx.pipeline();
										pipeline.remove(this);
										pipeline.remove(SocksMessageEncoder.class);
										pipeline.addLast(new HttpRequestEncoder());
										pipeline.addLast(new HttpResponseDecoder());
										pipeline.addLast(new SimpleChannelInboundHandler<HttpResponse>(){

											@Override
											protected void channelRead0(ChannelHandlerContext ctx, HttpResponse msg)
													throws Exception {
												System.out.println("http response:"+msg.getStatus());
											}
											
										});
										
										System.out.println("发送http请求");
										DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/robots.txt");
										request.headers().add("Host", "baidu.com");
										request.headers().add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
										request.headers().add("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/49.0.2623.112 Safari/537.36");
										ctx.channel().writeAndFlush(request);
									}
									
								});
							
							ctx.channel().writeAndFlush(new SocksCmdRequest(SocksCmdType.CONNECT, SocksAddressType.DOMAIN, "www.baidu.com", 80));
						}
					});
					
					ch.writeAndFlush(new SocksInitRequest(Arrays.asList(SocksAuthScheme.NO_AUTH)));
				}else{
					System.out.println("失败");
				}
			}
		});
		
	}
}
