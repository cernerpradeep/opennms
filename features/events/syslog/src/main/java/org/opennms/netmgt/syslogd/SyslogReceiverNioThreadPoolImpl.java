/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2002-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.syslogd;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.opennms.core.concurrent.LogPreservingThreadFactory;
import org.opennms.core.concurrent.WaterfallExecutor;
import org.opennms.core.logging.Logging;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.config.SyslogdConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Seth
 * @author <a href="mailto:weave@oculan.com">Brian Weaver</a>
 * @author <a href="http://www.oculan.com">Oculan Corporation</a>
 * @fiddler joed
 */
public class SyslogReceiverNioThreadPoolImpl implements SyslogReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(SyslogReceiverNioThreadPoolImpl.class);

    private static final int SOCKET_TIMEOUT = 500;

    /**
     * This size is used as the size of each {@link ByteBuffer} used to capture syslog
     * messages.
     */
    public static final int MAX_PACKET_SIZE = 4096;

    /**
     * This is the number of NIO listener threads that will process {@link DatagramChannel#receive(ByteBuffer)}
     * calls on the syslog port.
     */
    public static final int SOCKET_RECEIVER_COUNT = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * The Fiber's status.
     */
    private volatile boolean m_stop;

    private DatagramChannel m_channel;

    /**
     * The context thread
     */
    private Thread m_context;

    private final SyslogdConfig m_config;

    private final ExecutorService m_executor;

    private final ExecutorService m_socketReceivers;

    public static DatagramChannel openChannel(SyslogdConfig config) throws SocketException, IOException {
        DatagramChannel channel = DatagramChannel.open();
        if (config.getListenAddress() != null && config.getListenAddress().length() != 0) {
            channel.socket().bind(new InetSocketAddress(InetAddressUtils.addr(config.getListenAddress()), config.getSyslogPort()));
        } else {
            channel.socket().bind(new InetSocketAddress(config.getSyslogPort()));
        }
        return channel;
    }

    /**
     * Construct a new receiver
     *
     * @param sock
     * @param matchPattern
     * @param hostGroup
     * @param messageGroup
     */
    public SyslogReceiverNioThreadPoolImpl(SyslogdConfig config) throws SocketException, IOException {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        m_stop = false;
        m_channel = null;
        m_config = config;

        m_executor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            1000L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new LogPreservingThreadFactory(getClass().getSimpleName(), Integer.MAX_VALUE)
        );

        // This thread pool is used to process {@link DatagramChannel#receive(ByteBuffer)} calls
        // on the syslog port. By using multiple threads, we can optimize the receipt of
        // packet data from the syslog port and avoid discarding UDP syslog packets.
        m_socketReceivers = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            1000L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new LogPreservingThreadFactory(getClass().getSimpleName() + "-SocketReceiver", Integer.MAX_VALUE)
        );
    }

    @Override
    public String getName() {
        String listenAddress = (m_config.getListenAddress() != null && m_config.getListenAddress().length() > 0) ? m_config.getListenAddress() : "0.0.0.0";
        return getClass().getSimpleName() + " [" + listenAddress + ":" + m_config.getSyslogPort() + "]";
    }

    /**
     * stop the current receiver
     * @throws InterruptedException
     * 
     */
    @Override
    public void stop() throws InterruptedException {
        m_stop = true;

        // Shut down the thread pool that is processing DatagramChannel.receive() calls
        m_socketReceivers.shutdown();

        // Shut down the thread pools that are executing SyslogConnection and SyslogProcessor tasks
        m_executor.shutdown();

        try {
            m_channel.close();
        } catch (IOException e) {
            LOG.warn("Exception while closing syslog channel: " + e.getMessage());
        } finally {
            m_channel = null;
        }

        if (m_context != null) {
            LOG.debug("Stopping and joining thread context {}", m_context.getName());
            m_context.interrupt();
            m_context.join();
            LOG.debug("Thread context stopped and joined");
        }
    }

    /**
     * The execution context.
     */
    @Override
    public void run() {
        // get the context
        m_context = Thread.currentThread();

        // Get a log instance
        Logging.putPrefix(Syslogd.LOG4J_CATEGORY);

        if (m_stop) {
            LOG.debug("Stop flag set before thread started, exiting");
            return;
        } else {
            LOG.debug("Thread context started");
        }

        try {
            LOG.debug("Opening syslog channel...");
            m_channel = openChannel(m_config);
        } catch (IOException e) {
            LOG.warn("An I/O error occured while trying to set the socket timeout", e);
        }

        // set an SO timeout to make sure we don't block forever
        // if a socket is closed.
        try {
            LOG.debug("Setting socket timeout to {}ms", SOCKET_TIMEOUT);
            m_channel.socket().setSoTimeout(SOCKET_TIMEOUT);
        } catch (SocketException e) {
            LOG.warn("An I/O error occured while trying to set the socket timeout", e);
        }

        // Increase the receive buffer for the socket
        try {
            LOG.debug("Attempting to set receive buffer size to {}", Integer.MAX_VALUE);
            m_channel.socket().setReceiveBufferSize(Integer.MAX_VALUE);
            LOG.debug("Actual receive buffer size is {}", m_channel.socket().getReceiveBufferSize());
        } catch (SocketException e) {
            LOG.info("Failed to set the receive buffer to {}", Integer.MAX_VALUE, e);
        }

        for (int i = 0; i < SOCKET_RECEIVER_COUNT; i++) {
            m_socketReceivers.execute(new Runnable() {
                public void run() {

                    // set to avoid numerous tracing message
                    boolean ioInterrupted = false;

                    // Allocate a buffer that's big enough to handle any sane syslog message
                    ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
                    buffer.clear();

                    // now start processing incoming requests
                    while (!m_stop) {
                        if (m_context.isInterrupted()) {
                            LOG.debug("Thread context interrupted");
                            break;
                        }

                        try {
                            if (!ioInterrupted) {
                                LOG.debug("Waiting on a datagram to arrive");
                            }

                            // Write the datagram into the ByteBuffer
                            InetSocketAddress source = (InetSocketAddress)m_channel.receive(buffer);

                            // Flip the buffer from write to read mode
                            buffer.flip();

                            WaterfallExecutor.waterfall(m_executor, new SyslogConnection(SyslogConnection.copyPacket(source.getAddress(), source.getPort(), buffer), m_config));

                            // Clear the buffer so that it's ready for writing again
                            buffer.clear();

                            // reset the flag
                            ioInterrupted = false; 
                        } catch (SocketTimeoutException e) {
                            ioInterrupted = true;
                            continue;
                        } catch (InterruptedIOException e) {
                            ioInterrupted = true;
                            continue;
                        } catch (ExecutionException e) {
                            LOG.error("Task execution failed in {}", this.getClass().getSimpleName(), e);
                            break;
                        } catch (InterruptedException e) {
                            LOG.error("Task interrupted in {}", this.getClass().getSimpleName(), e);
                            break;
                        } catch (IOException e) {
                            LOG.error("An I/O exception occured on the datagram receipt port, exiting", e);
                            break;
                        }

                    } // end while status OK

                    LOG.debug("Thread context exiting");
                }
            });
        }
    }
}
