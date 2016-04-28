package com.opsunv.proxyagent.model;

public class ProxyInfo {
	private String host;
	
	private int port;
	
	public ProxyInfo() {
	}
	
	public ProxyInfo(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
	@Override
	public String toString() {
		return host+":"+port;
	}
	
}
