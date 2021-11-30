/*
 *  Copyright (C) 2021 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package we.dedicatedline.proxy.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import we.dedicatedline.DedicatedLineUtils;
import we.dedicatedline.proxy.ProxyConfig;
import we.dedicatedline.proxy.codec.*;
import we.dedicatedline.proxy.server.ProxyServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Francis Dong
 *
 */
public class ProxyClient {

	private static final Logger log = LoggerFactory.getLogger(ProxyClient.class);

	private EventLoopGroup group;
	private ChannelFuture channelFuture;
	private Channel channel;
	private Bootstrap bootstrap;
	private ChannelHandlerContext proxyServerChannelCtx;
	private String protocol;
	private String host;
	private Integer port;
	private InetSocketAddress senderAddress;

	private ProxyConfig proxyConfig;

	private boolean closed = false;

	public ProxyClient(InetSocketAddress senderAddress, String protocol, String host, Integer port, ChannelHandlerContext proxyServerChannelCtx, ProxyConfig proxyConfig) {
		this.senderAddress = senderAddress;
		this.protocol = protocol;
		this.host = host;
		this.port = port;
		this.proxyServerChannelCtx = proxyServerChannelCtx;
		this.proxyConfig = proxyConfig;
		group = new NioEventLoopGroup();

		bootstrap = new Bootstrap();
		switch (protocol) {
		case ProxyServer.PROTOCOL_TCP:
			bootstrap.group(group).channel(NioSocketChannel.class).remoteAddress(this.host, this.port)
					.option(ChannelOption.SO_KEEPALIVE, true).option(ChannelOption.TCP_NODELAY, true)
					.handler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) {
							ChannelPipeline pipeline = ch.pipeline();

							/*if (proxyConfig.getRole().equals(ProxyConfig.CLIENT)) {
								pipeline.addLast("FizzTcpMessageEncoder", new FizzTcpMessageEncoder());
								pipeline.addLast("FizzTcpMessageDecoder", new FizzTcpMessageDecoder(FizzTcpMessage.MAX_LENGTH, FizzTcpMessage.LENGTH_FIELD_OFFSET,
										FizzTcpMessage.LENGTH_FIELD_LENGTH, FizzTcpMessage.LENGTH_ADJUSTMENT, FizzTcpMessage.INITIAL_BYTES_TO_STRIP, true));
								log.info("proxy tcp client to {}:{} add FizzTcpMessageDecoder FizzTcpMessageEncoder", host, port);
							}*/

							if (proxyConfig.isRightOut()) {
								pipeline.addLast("FizzTcpMessageEncoder", new FizzTcpMessageEncoder());
								log.info("proxy tcp client to {}:{} add FizzTcpMessageEncoder", host, port);
							}

							if (proxyConfig.isRightIn()) {
								pipeline.addLast("FizzTcpMessageDecoder", new FizzTcpMessageDecoder(FizzTcpMessage.MAX_LENGTH, FizzTcpMessage.LENGTH_FIELD_OFFSET,
										FizzTcpMessage.LENGTH_FIELD_LENGTH, FizzTcpMessage.LENGTH_ADJUSTMENT, FizzTcpMessage.INITIAL_BYTES_TO_STRIP, true));
								log.info("proxy tcp client to {}:{} add FizzTcpMessageDecoder", host, port);
							}

							pipeline.addLast(new TcpClientHandler(proxyConfig, proxyServerChannelCtx));
						}
					});
			break;
		case ProxyServer.PROTOCOL_UDP:
			bootstrap.group(group).channel(NioDatagramChannel.class)
					.handler(new ChannelInitializer<NioDatagramChannel>() {
						@Override
						protected void initChannel(NioDatagramChannel ch) {
							ChannelPipeline pipeline = ch.pipeline();
							pipeline.addLast(new IdleStateHandler(0, 0, 60 * 30));
							pipeline.addLast(new UdpClientHandler(senderAddress, proxyServerChannelCtx, ProxyClient.this, proxyConfig));
						}
					});
			break;
		}
	}

	public void connect() {
		try {
			switch (protocol) {
			case ProxyServer.PROTOCOL_TCP:
				channelFuture = bootstrap.connect().sync();
				channel = (Channel) channelFuture.channel();
				break;
			case ProxyServer.PROTOCOL_UDP:
				channelFuture = bootstrap.bind(0).sync();
				channel = (Channel) channelFuture.channel();
				break;
			}
		} catch (InterruptedException e) {
			log.warn("connect failed, host: {}, port: {}", host, port, e);
		}
	}

	public void disconnect() {
		closed = true;
		if (channelFuture != null && channelFuture.channel().isRegistered()) {
			try {
				channelFuture.channel().close().sync();
			} catch (InterruptedException e) {
				log.warn("disconnect failed, host: {}, port: {}", host, port, e);
			}
		}
	}

	public void write(Object msg) {
		if (this.channel != null) {
			switch (protocol) {
			case ProxyServer.PROTOCOL_TCP:

				// if (this.proxyConfig.getRole().equals(ProxyConfig.CLIENT)) {
				if (proxyConfig.isRightOut()) {
					ByteBuf buf = (ByteBuf) msg;
					byte[] bytes = new byte[buf.readableBytes()];
					buf.readBytes(bytes);
					FizzTcpMessage fizzTcpMessage = new FizzTcpMessage();
					fizzTcpMessage.setType(1);
					fizzTcpMessage.setDedicatedLine("41d7a1573d054bbca7cbcf4008d7b925"); // TODO
					fizzTcpMessage.setTimestamp(System.currentTimeMillis());
					String sign = DedicatedLineUtils.sign(fizzTcpMessage.getDedicatedLineStr(), fizzTcpMessage.getTimestamp(), "ade052c1ec3e44a3bbfbaac988a6e7d4");
					fizzTcpMessage.setSign(sign.substring(0, FizzTcpMessage.SIGN_LENGTH));
					fizzTcpMessage.setLength(bytes.length);
					fizzTcpMessage.setContent(bytes);
					this.channel.writeAndFlush(fizzTcpMessage);
					if (log.isDebugEnabled()) {
						log.debug("proxy tcp client to {}:{} send: {}", this.host, this.port, fizzTcpMessage);
					}

				} else {
					this.channel.writeAndFlush(msg);
				}

				break;

			case ProxyServer.PROTOCOL_UDP:

				InetSocketAddress address = new InetSocketAddress(host, port);

				ByteBuf buf = (ByteBuf) msg;
				if (proxyConfig.isRightOut()) {
					byte[] bufBytes = new byte[buf.readableBytes()];
					buf.readBytes(bufBytes);
					List<DatagramPacket> packets = FizzUdpMessage.disassemble(address, bufBytes);
					for (DatagramPacket packet : packets) {
						if (log.isDebugEnabled()) {
							DatagramPacket copy = packet.copy();
							log.debug("{} send {}:{} udp msg: {}", buf.hashCode(), host, port, FizzUdpMessage.decode(copy));
						}
						channel.writeAndFlush(packet);
					}
				} else {
					DatagramPacket packet = new DatagramPacket(buf, address);
					if (log.isDebugEnabled()) {
						DatagramPacket copy = packet.copy();
						log.debug("{} send {}:{} udp msg: {}", packet.hashCode(), host, port, copy.content().toString());
					}
					channel.writeAndFlush(packet);
				}

				/*if (this.proxyConfig.getRole().equals(ProxyConfig.CLIENT)) {
					DatagramPacket datagramPacket = (DatagramPacket) msg;
					ByteBuf content = datagramPacket.content();
					byte[] contentBytes = new byte[content.readableBytes()];
					content.readBytes(contentBytes);
					List<DatagramPacket> packets = FizzUdpMessage.disassemble(address, contentBytes);
					for (DatagramPacket packet : packets) {
						if (log.isDebugEnabled()) {
							DatagramPacket copy = packet.copy();
							log.debug("{} send {}:{} udp msg: {}", datagramPacket.hashCode(), host, port, FizzUdpMessage.decode(copy));
						}
						channel.writeAndFlush(packet);
					}

				} else {
					ByteBuf buf = (ByteBuf) msg;
					DatagramPacket packet = new DatagramPacket(buf, address);
					if (log.isDebugEnabled()) {
						DatagramPacket copy = packet.copy();
						log.debug("{} send {}:{} udp msg: {}", packet.hashCode(), host, port, copy.content().toString());
					}
					channel.writeAndFlush(packet);
				}*/

				break;
			}
		}
	}

	public boolean isClosed() {
		return closed;
	}
}
