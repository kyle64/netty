/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.discard;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Discards any incoming data.
 */
public final class DiscardServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8009"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // NioEventLoopGroup is a multithreaded event loop that handles I/O operation.
        // Netty provides various EventLoopGroup implementations for different kind of transports.
        // We are implementing a server-side application in this example, and therefore two NioEventLoopGroup will be used.
        // The first one, often called 'boss', accepts an incoming connection.
        // The second one, often called 'worker', handles the traffic of the accepted connection once the boss accepts the connection
        // and registers the accepted connection to the worker.
        // How many Threads are used and how they are mapped to the created Channels depends on the EventLoopGroup implementation and may be even configurable via a constructor.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // (1)
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            // ServerBootstrap is a helper class that sets up a server.
            // You can set up the server using a Channel directly.
            // However, please note that this is a tedious process, and you do not need to do that in most cases.
            ServerBootstrap b = new ServerBootstrap(); // (2)
            b.group(bossGroup, workerGroup)
             // Here, we specify to use the NioServerSocketChannel class which is used to instantiate a new Channel to accept incoming connections.
             .channel(NioServerSocketChannel.class) // (3)
             .handler(new LoggingHandler(LogLevel.INFO))
             // The handler specified here will always be evaluated by a newly accepted Channel.
             // The ChannelInitializer is a special handler that is purposed to help a user configure a new Channel.
             // It is most likely that you want to configure the ChannelPipeline of the new Channel by adding some handlers
             // such as DiscardServerHandler to implement your network application.
             // As the application gets complicated, it is likely that you will add more handlers to the pipeline
             // and extract this anonymous class into a top-level class eventually.
             .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     p.addLast(new DiscardServerHandler());
                 }
             })
            // You can also set the parameters which are specific to the Channel implementation.
            // We are writing a TCP/IP server, so we are allowed to set the socket options such as tcpNoDelay and keepAlive.
            // Please refer to the apidocs of ChannelOption and the specific ChannelConfig implementations to get an overview about the supported ChannelOptions.
            .option(ChannelOption.SO_BACKLOG, 128) // (5)
            // Did you notice option() and childOption()?
            // option() is for the NioServerSocketChannel that accepts incoming connections.
            // childOption() is for the Channels accepted by the parent ServerChannel, which is NioServerSocketChannel in this case.
            .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(PORT).sync(); // (7)

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
