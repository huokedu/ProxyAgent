package com.opsunv.proxyagent;

import com.opsunv.proxyagent.socks.SocksServerConnectHandler;
import com.opsunv.proxyagent.socks.SocksServerUtils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socks.SocksAuthResponse;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksAuthStatus;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdRequestDecoder;
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.codec.socks.SocksRequest;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class SocksProxyMain {
	public static void main(String[] args) throws Exception{
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup childGroup = new NioEventLoopGroup();
		
		ServerBootstrap server = new ServerBootstrap();
		server.group(bossGroup, childGroup)
			.channel(NioServerSocketChannel.class)
			.handler(new LoggingHandler(LogLevel.INFO))
			.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline pipeline = ch.pipeline();
					pipeline.addLast(new SocksInitRequestDecoder());
			        pipeline.addLast(new SocksMessageEncoder());
			        pipeline.addLast("socks",new SocksServerHandler());
				}
			});
		
		server.bind(1080).sync().channel().closeFuture().sync();
	}
}

class SocksServerHandler extends SimpleChannelInboundHandler<SocksRequest>{

	@Override
	public void channelRead0(ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        switch (socksRequest.requestType()) {
			case INIT:
	            ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
	            ctx.write(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
	            System.out.println("收到init");
	            break;
			case AUTH:
	            ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
	            ctx.write(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
	            System.out.println("收到auth");
	            break;
			case CMD:
	            SocksCmdRequest req = (SocksCmdRequest) socksRequest;
	            if (req.cmdType() == SocksCmdType.CONNECT) {
	                ctx.pipeline().addLast(new SocksServerConnectHandler());
	                ctx.pipeline().remove(this);
	                ctx.fireChannelRead(socksRequest);
	            } else {
	                ctx.channel().close();
	            }
	            System.out.println("收到cmd");
	            break;
			case UNKNOWN:
				ctx.close();
	            break;
		}
	}
	
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        throwable.printStackTrace();
        SocksServerUtils.closeOnFlush(ctx.channel());
    }

}