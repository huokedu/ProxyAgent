package com.opsunv.proxyagent.model;

import java.util.ArrayList;
import java.util.List;

public class SocksProxyLink {
	
	private List<ProxyInfo> proxies = new ArrayList<>();
	
	public ProxyInfo getFirst(){
		return proxies.size()>0?proxies.get(0):null;
	}
	
	public void add(ProxyInfo proxy){
		proxies.add(proxy);
	}
	
	public List<ProxyInfo> getRemainProxy(){
		List<ProxyInfo> list = new ArrayList<>();
		
		for(int i=1;i<proxies.size();i++){
			list.add(proxies.get(i));
		}
		
		return list;
	}
}
