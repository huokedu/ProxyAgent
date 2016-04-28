package com.opsunv.proxyagent.socks;

import com.opsunv.proxyagent.model.SocksProxyLink;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * 实现了多层Socks代理
 * 
 */
public class SocksProxyServer {
	private ServerBootstrap server;
	
	private int[] ports;
	
	public SocksProxyServer(int[] ports,final SocksProxyLink link) {
		this.ports = ports;
		
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup childGroup = new NioEventLoopGroup();
		
		server = new ServerBootstrap();
		server.group(bossGroup, childGroup)
			.channel(NioServerSocketChannel.class)
			.handler(new LoggingHandler(LogLevel.INFO))
			.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline pipeline = ch.pipeline();
					pipeline.addLast(new SocksInitRequestDecoder());
			        pipeline.addLast(new SocksMessageEncoder());
			        pipeline.addLast(new BridgeChannelHandler(link));
				}
			});
	}
	
	public void start(){
		for(int port:ports){
			try {
				server.bind(port).sync().channel().closeFuture().sync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
