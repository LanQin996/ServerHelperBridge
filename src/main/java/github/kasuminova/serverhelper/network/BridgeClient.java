package github.kasuminova.serverhelper.network;

import github.kasuminova.serverhelper.ServerHelperBridge;
import github.kasuminova.serverhelper.data.BridgeClientConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.internal.ThrowableUtil;

import java.io.Serializable;

public class BridgeClient {
    private final ConnectionDaemonThread connectionDaemonThread = new ConnectionDaemonThread(this);
    private final MessageSyncThread messageSyncThread = new MessageSyncThread(this);
    private ChannelFuture future = null;
    private EventLoopGroup work;
    private ChannelHandlerContext ctx = null;
    private BridgeClientConfig config;

    public BridgeClient(BridgeClientConfig config) {
        this.config = config;
    }

    public void connect() throws Exception {
        if (work != null && future != null) disconnect();

        work = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(work)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ClientInitializer(this));

        ServerHelperBridge.instance.logger.info(
                String.format("正在连接至中心服务器 %s:%s ...", config.getIp(), config.getPort()));
        future = bootstrap.connect(config.getIp(), config.getPort()).sync();
        connectionDaemonThread.start();
        messageSyncThread.start();
    }

    public void disconnect() {
        try {
            connectionDaemonThread.interrupt();
            messageSyncThread.interrupt();

            work.shutdownGracefully();

            if (future != null) {
                future.channel().closeFuture().sync();
                future = null;
            }
            work = null;
        } catch (Exception e) {
            ServerHelperBridge.instance.logger.warning(ThrowableUtil.stackTraceToString(e));
        }
    }

    public ChannelHandlerContext getCtx() {
        return ctx;
    }

    public void setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public ChannelFuture getFuture() {
        return future;
    }

    public void setFuture(ChannelFuture future) {
        this.future = future;
    }

    public BridgeClientConfig getConfig() {
        return config;
    }

    public void setConfig(BridgeClientConfig config) {
        this.config = config;
    }

    public ConnectionDaemonThread getConnectionDaemonThread() {
        return connectionDaemonThread;
    }

    public MessageSyncThread getCmdExecSyncThread() {
        return messageSyncThread;
    }

    public <M extends Serializable> void sendMessageToServer(M message) {
        if (ctx == null) {
            ServerHelperBridge.instance.logger.warning("尝试在与中心服务器断开连接的情况下发送网络包，已丢弃数据包。");
            return;
        }
        ctx.writeAndFlush(message);
    }
}
