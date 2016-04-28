package com.opsunv.proxyagent;

import com.opsunv.proxyagent.model.SocksProxyLink;
import com.opsunv.proxyagent.socks.SocksProxyServer;

public class SocksProxyMain2 {
	public static void main(String[] args) throws Exception{
		SocksProxyLink link = new SocksProxyLink();
		
		SocksProxyServer server = new SocksProxyServer(new int[]{1081},link);
		server.start();
		
		Thread.sleep(10000000000L);
		
	}
}
