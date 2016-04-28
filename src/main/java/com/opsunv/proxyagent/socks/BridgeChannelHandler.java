package com.opsunv.proxyagent.socks;

import com.opsunv.proxyagent.model.SocksProxyLink;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socks.SocksAuthResponse;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksAuthStatus;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdRequestDecoder;
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksInitResponse;
import io.netty.handler.codec.socks.SocksRequest;

public class BridgeChannelHandler extends SimpleChannelInboundHandler<SocksRequest>{
	private SocksProxyLink link;
	
	public BridgeChannelHandler(SocksProxyLink link) {
		this.link = link;
	}

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
	                ctx.pipeline().addLast(new SocksServerConnectHandler(link));
	                ctx.pipeline().remove(this);
	                ctx.fireChannelRead(socksRequest);
	            } else {
	            	System.out.println("socks断开");
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
