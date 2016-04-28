package com.opsunv.proxyagent.socks;


import java.util.Arrays;
import java.util.Iterator;

import com.opsunv.proxyagent.model.ProxyInfo;
import com.opsunv.proxyagent.model.SocksProxyLink;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdResponseDecoder;
import io.netty.handler.codec.socks.SocksCmdStatus;
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksInitRequest;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksInitResponseDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

public class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksCmdRequest> {
	private final Bootstrap b = new Bootstrap();
	
	private Promise<Channel> promise;
	
	private SocksProxyLink link;
	
	private volatile boolean isLast = false;
	
	public SocksServerConnectHandler(SocksProxyLink link) {
		this.link = link;
	}

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksCmdRequest request) throws Exception {
        promise = ctx.executor().newPromise();
        promise.addListener(new GenericFutureListener<Future<Channel>>() {
            public void operationComplete(final Future<Channel> future) throws Exception {
                final Channel outboundChannel = future.getNow();
                if (future.isSuccess()) {
                	System.out.println("桥接channel");
                    ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, request.addressType()))
                            .addListener(new ChannelFutureListener() {
                                public void operationComplete(ChannelFuture channelFuture) {
                                    ctx.pipeline().remove(SocksServerConnectHandler.this);
                                    outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                    ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                                    
                                }
                            });
                } else {
                    ctx.writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FAILURE, request.addressType()));
                    SocksServerUtils.closeOnFlush(ctx.channel());
                }
            }
        });

        ProxyInfo proxy = link.getFirst();
        if(proxy==null){
        	direct(ctx, request);
        }else{
        	socks(proxy, ctx, request);
        }
        
    }
    
    private void socks(final ProxyInfo firstProxy,final ChannelHandlerContext ctx,final SocksCmdRequest request){
    	 final Channel inboundChannel = ctx.channel();
         b.group(inboundChannel.eventLoop())
                 .channel(NioSocketChannel.class)
                 .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                 .option(ChannelOption.SO_KEEPALIVE, true)
                 .handler(new SocksMessageEncoder());
         
         //连接第一层
         b.connect(firstProxy.getHost(), firstProxy.getPort()).addListener(new ChannelFutureListener() {
             @Override
             public void operationComplete(ChannelFuture future) throws Exception {
                 if (future.isSuccess()) {
                	System.out.println("服务端连接socks代理["+firstProxy+"]成功");
                	Iterator<ProxyInfo> remains = link.getRemainProxy().iterator();
                	connectToSocks(future.channel(),request,remains);
                 } else {
                	 System.out.println("服务端连接socks代理["+firstProxy+"]失败");
                     ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FAILURE, request.addressType()));
                     SocksServerUtils.closeOnFlush(ctx.channel());
                 }
             }
         });
    }
    
    private void connectToSocks(Channel ch,final SocksCmdRequest request,final Iterator<ProxyInfo> remains){
    	ch.pipeline().addLast(new SocksInitResponseDecoder());
		ch.pipeline().addLast(new SimpleChannelInboundHandler<SocksInitResponse>() {
			@Override
			protected void channelRead0(ChannelHandlerContext ctx, SocksInitResponse msg) throws Exception {
				ChannelPipeline pipeline = ctx.pipeline();
				pipeline.remove(this);
				
			  	pipeline.addLast(new SocksCmdResponseDecoder());
		    	pipeline.addLast(new SimpleChannelInboundHandler<SocksCmdResponse>(){
					@Override
					protected void channelRead0(ChannelHandlerContext ctx, SocksCmdResponse msg)
							throws Exception {
							ctx.pipeline().remove(this);
							System.out.println("Channel连接成功");
							if(isLast){
								promise.setSuccess(ctx.channel());
							}else{
								connectToSocks(ctx.channel(), request, remains);
							}
						}
				});
				
				//连接下一层代理
				if(remains.hasNext()){
					ProxyInfo proxy = remains.next();
					System.out.println("服务器连接下层Socoks代理["+proxy+"]");
					ctx.writeAndFlush(new SocksCmdRequest(SocksCmdType.CONNECT, SocksAddressType.IPv4,proxy.getHost() , proxy.getPort()));
					isLast = false;
				}else{
					isLast = true;
					System.out.println("服务器连接目标主机["+request.host()+":"+request.port()+"]");
					ctx.writeAndFlush(new SocksCmdRequest(SocksCmdType.CONNECT, request.addressType(), request.host(), request.port()));
				}
			}
		});
		
		ch.writeAndFlush(new SocksInitRequest(Arrays.asList(SocksAuthScheme.NO_AUTH)));
    }
    
    private void direct(final ChannelHandlerContext ctx,final SocksCmdRequest request){
    	final Channel inboundChannel = ctx.channel();
    	b.group(inboundChannel.eventLoop())
    	.channel(NioSocketChannel.class)
    	.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
    	.option(ChannelOption.SO_KEEPALIVE, true)
    	.handler(new DirectClientHandler(promise));
    	System.out.println("服务端连接到："+request.host()+":"+request.port());
    	b.connect(request.host(), request.port()).addListener(new ChannelFutureListener() {
    		@Override
    		public void operationComplete(ChannelFuture future) throws Exception {
    			if (future.isSuccess()) {
    				// Connection established use handler provided results
    			} else {
    				// Close the connection if the connection attempt has failed.
    				ctx.channel().writeAndFlush(
    						new SocksCmdResponse(SocksCmdStatus.FAILURE, request.addressType()));
    				SocksServerUtils.closeOnFlush(ctx.channel());
    			}
    		}
    	});
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}
