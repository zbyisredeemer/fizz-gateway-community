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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import we.dedicatedline.proxy.ProxyConfig;
import we.dedicatedline.proxy.codec.FizzSocketTextMessage;
import we.dedicatedline.proxy.codec.FizzUdpTextMessage;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 
 * @author Francis Dong
 *
 */
public class UdpClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

	private static final Logger log = LoggerFactory.getLogger(UdpClientHandler.class);

	private final ChannelHandlerContext proxyServerChannelCtx;

	/**
	 * For UDP
	 */
	private final InetSocketAddress senderAddress;
	private final ProxyClient proxyClient;

	private ProxyConfig proxyConfig;

	public UdpClientHandler(InetSocketAddress senderAddress, ChannelHandlerContext proxyServerChannelCtx, ProxyClient proxyClient, ProxyConfig proxyConfig) {
		super(false);
		this.senderAddress = senderAddress;
		this.proxyServerChannelCtx = proxyServerChannelCtx;
		this.proxyClient = proxyClient;
		this.proxyConfig = proxyConfig;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {

		if (proxyConfig.isRightIn()) {

			ByteBuf content = packet.content();




			if (proxyConfig.isLeftOut()) {
//				List<DatagramPacket> datagramPackets = FizzUdpTextMessage.disassemble(senderAddress, bytes);
//				for (DatagramPacket datagramPacket : datagramPackets) {
//					if (log.isDebugEnabled()) {
//						DatagramPacket copy = datagramPacket.copy();
//						log.debug("{} left out: {}", proxyConfig.logMsg(), copy.content().toString());
//					}
//					proxyServerChannelCtx.writeAndFlush(datagramPacket);
//				}
				DatagramPacket msg = new DatagramPacket(content, senderAddress);
				if (log.isDebugEnabled()) {
					DatagramPacket copy = msg.copy();
					log.debug("{} left out: {}", proxyConfig.logMsg(), copy.content().toString());
				}
				proxyServerChannelCtx.writeAndFlush(msg);

			} else {
				content.skipBytes(FizzSocketTextMessage.METADATA_LENGTH);
				byte[] bytes = new byte[content.readableBytes()];
				content.readBytes(bytes);
				FizzSocketTextMessage.inv(bytes);
				ByteBuf buf = Unpooled.copiedBuffer(bytes);
				DatagramPacket msg = new DatagramPacket(buf, senderAddress);
				if (log.isDebugEnabled()) {
					log.debug("{} left out: {}", proxyConfig.logMsg(), msg.copy().content().toString(CharsetUtil.UTF_8));
				}
				proxyServerChannelCtx.writeAndFlush(msg);

			}



		} else {
			if (log.isDebugEnabled()) {
				log.debug("{} right in: {}", proxyConfig.logMsg(), packet.copy().content().toString(CharsetUtil.UTF_8));
			}

			if (proxyConfig.isLeftOut()) {
				ByteBuf buf = packet.content();
				byte[] contentBytes = new byte[buf.readableBytes()];
				buf.readBytes(contentBytes);
				List<DatagramPacket> datagramPackets = FizzUdpTextMessage.disassemble(senderAddress, contentBytes);
				for (DatagramPacket datagramPacket : datagramPackets) {
					if (log.isDebugEnabled()) {
						DatagramPacket copy = datagramPacket.copy();
						log.debug("{} left out: {}", proxyConfig.logMsg(), copy.content().toString(CharsetUtil.UTF_8));
					}
					proxyServerChannelCtx.writeAndFlush(datagramPacket);
				}
			} else {
				if (log.isDebugEnabled()) {
					log.debug("{} left out: {}", proxyConfig.logMsg(), packet.copy().content().toString(CharsetUtil.UTF_8));
				}
				DatagramPacket pk = new DatagramPacket(packet.content(), senderAddress);
				proxyServerChannelCtx.writeAndFlush(pk);

			}





		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.error("异常:", cause);
		proxyClient.remove();
		proxyClient.disconnect();
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		super.channelRegistered(ctx);
		log.info("client channelRegistered, channelId={}", ctx.channel().id().asLongText());
	}

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		super.channelUnregistered(ctx);
		log.info("client channelUnregistered, channelId={}", ctx.channel().id().asLongText());
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (IdleStateEvent.class.isAssignableFrom(evt.getClass())) {
			IdleStateEvent event = (IdleStateEvent) evt;
			if (event.state() == IdleState.ALL_IDLE) {
				processAllIdle(ctx);
				return;
			}
		}
		super.userEventTriggered(ctx, evt);
	}

	private void processAllIdle(ChannelHandlerContext ctx) {
		String channelId = ctx.channel().id().asLongText();
		proxyClient.remove();
		proxyClient.disconnect();
		log.debug("[Netty]connection(id=" + channelId + ") reached max idle time, connection closed.");
	}

}