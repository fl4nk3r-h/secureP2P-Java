package com.zerotrust.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link AsyncPeer}.
 * <p>
 * Spins up local listener/connector peer pairs and exercises connection,
 * identity exchange, ML-KEM key exchange, and encrypted message exchange.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see AsyncPeer
 */
public class AsyncPeerTest {
    /**
     * Finds an unused local TCP port.
     *
     * @return A free port number
     * @throws IOException if a temporary server socket cannot be opened
     */
    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Pairs a listener peer with a connector peer for test setup.
     *
     * @author fl4nk3r-h
     * @version 2.0.0
     */
    private static class PeerPair {
        private final AsyncPeer listener;
        private final AsyncPeer connector;

        /**
         * @param listener  Peer in listening mode
         * @param connector Peer that initiated the connection
         */
        private PeerPair(AsyncPeer listener, AsyncPeer connector) {
            this.listener = listener;
            this.connector = connector;
        }
    }

    /**
     * Creates a connected listener/connector peer pair.
     *
     * @return The connected peer pair
     * @throws Exception if connection establishment or readiness checks fail
     */
    private PeerPair createConnectedPeers() throws Exception {
        int listenerPort = findFreePort();
        int connectorPort = findFreePort();
        AsyncPeer listener = new AsyncPeer("Alice", listenerPort);
        AsyncPeer connector = new AsyncPeer("Bob", connectorPort);

        CountDownLatch connected = new CountDownLatch(2);
        listener.acceptConnectionAsync(p -> connected.countDown());
        connector.connectToPeerAsync("localhost", listenerPort, p -> connected.countDown());

        assertTrue(connected.await(5, TimeUnit.SECONDS));
        assertTrue(listener.waitForConnectionReady(5000));
        assertTrue(connector.waitForConnectionReady(5000));

        return new PeerPair(listener, connector);
    }

    /**
     * Runs the peer identity exchange concurrently on both peers and asserts
     * each side learned the other's id.
     *
     * @param a First peer
     * @param b Second peer
     * @throws Exception if the exchange does not complete in time
     */
    private void exchangePeerIds(AsyncPeer a, AsyncPeer b) throws Exception {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch done = new CountDownLatch(2);
            AtomicReference<String> aRemote = new AtomicReference<>();
            AtomicReference<String> bRemote = new AtomicReference<>();

            exec.execute(() -> {
                try {
                    aRemote.set(a.exchangePeerId());
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
            exec.execute(() -> {
                try {
                    bRemote.set(b.exchangePeerId());
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals("Bob", aRemote.get());
            assertEquals("Alice", bRemote.get());
        } finally {
            exec.shutdownNow();
        }
    }

    /**
     * Runs the ML-KEM key exchange concurrently on both peers and asserts both
     * sessions become encrypted.
     *
     * @param a First peer
     * @param b Second peer
     * @throws InterruptedException if interrupted while waiting
     */
    private void performKeyExchange(AsyncPeer a, AsyncPeer b) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        a.performKeyExchangeAsync(latch::countDown);
        b.performKeyExchangeAsync(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(a.isEncrypted());
        assertTrue(b.isEncrypted());
    }

    /**
     * Verifies {@link AsyncPeer#acceptConnectionAsync} and the connect path
     * result in both peers being connected.
     */
    @Test
    void testAcceptConnectionAsync() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            assertTrue(pair.listener.isConnected());
            assertTrue(pair.connector.isConnected());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies closing a peer releases its connection state.
     */
    @Test
    void testClose() {
        AsyncPeer peer = null;
        try {
            peer = new AsyncPeer("CloseTest", findFreePort());
            peer.close();
            assertFalse(peer.isConnected());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (peer != null) {
                peer.close();
            }
        }
    }

    /**
     * Verifies {@link AsyncPeer#connectToPeerAsync} establishes the connection.
     */
    @Test
    void testConnectToPeerAsync() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            assertTrue(pair.connector.isConnected());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies the peer identity exchange produces matching remote ids.
     */
    @Test
    void testExchangePeerId() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            exchangePeerIds(pair.listener, pair.connector);
            assertEquals("Bob", pair.listener.getRemotePeerId());
            assertEquals("Alice", pair.connector.getRemotePeerId());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies the configured peer id is reported.
     */
    @Test
    void testGetPeerId() {
        AsyncPeer peer = null;
        try {
            peer = new AsyncPeer("Alice", findFreePort());
            assertEquals("Alice", peer.getPeerId());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (peer != null) {
                peer.close();
            }
        }
    }

    /**
     * Verifies the configured port is reported.
     */
    @Test
    void testGetPort() {
        AsyncPeer peer = null;
        try {
            int port = findFreePort();
            peer = new AsyncPeer("PortTest", port);
            assertEquals(port, peer.getPort());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (peer != null) {
                peer.close();
            }
        }
    }

    /**
     * Verifies a fresh peer has an empty message queue.
     */
    @Test
    void testGetQueueSize() {
        AsyncPeer peer = null;
        try {
            peer = new AsyncPeer("QueueTest", findFreePort());
            assertEquals(0, peer.getQueueSize());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (peer != null) {
                peer.close();
            }
        }
    }

    /**
     * Verifies remote peer ids match after the identity exchange.
     */
    @Test
    void testGetRemotePeerId() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            exchangePeerIds(pair.listener, pair.connector);
            assertEquals("Bob", pair.listener.getRemotePeerId());
            assertEquals("Alice", pair.connector.getRemotePeerId());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies both peers report a connected socket after pairing.
     */
    @Test
    void testIsConnected() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            assertTrue(pair.listener.isConnected());
            assertTrue(pair.connector.isConnected());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies encryption state transitions only after the key exchange.
     */
    @Test
    void testIsEncrypted() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            assertFalse(pair.listener.isEncrypted());
            assertFalse(pair.connector.isEncrypted());
            exchangePeerIds(pair.listener, pair.connector);
            performKeyExchange(pair.listener, pair.connector);
            assertTrue(pair.listener.isEncrypted());
            assertTrue(pair.connector.isEncrypted());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies an error callback can be registered without failure.
     */
    @Test
    void testOnError() {
        AsyncPeer peer = null;
        try {
            peer = new AsyncPeer("ErrorTest", findFreePort());
            peer.onError(e -> {
                // no-op
            });
            assertNotNull(peer);
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (peer != null) {
                peer.close();
            }
        }
    }

    /**
     * Verifies an encrypted message is delivered decrypted to the handler.
     */
    @Test
    void testOnMessageReceived() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            exchangePeerIds(pair.listener, pair.connector);
            performKeyExchange(pair.listener, pair.connector);

            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<String> payload = new AtomicReference<>();
            pair.listener.onMessageReceived(msg -> {
                payload.set(msg);
                received.countDown();
            });

            pair.connector.sendMessageAsync("hello-from-bob");
            assertTrue(received.await(5, TimeUnit.SECONDS));
            assertEquals("hello-from-bob", payload.get());
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies a send-complete callback can be registered without failure.
     */
    @Test
    void testOnSendComplete() {
        AsyncPeer peer = null;
        try {
            peer = new AsyncPeer("SendCompleteTest", findFreePort());
            peer.onSendComplete(success -> {
                // no-op
            });
            assertNotNull(peer);
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (peer != null) {
                peer.close();
            }
        }
    }

    /**
     * Verifies both peers complete the async key exchange.
     */
    @Test
    void testPerformKeyExchangeAsync() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            exchangePeerIds(pair.listener, pair.connector);
            performKeyExchange(pair.listener, pair.connector);
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies a sent message is buffered and retrievable via
     * {@link AsyncPeer#pollMessage(long, TimeUnit)}.
     */
    @Test
    void testPollMessage() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            exchangePeerIds(pair.listener, pair.connector);
            performKeyExchange(pair.listener, pair.connector);
            pair.connector.sendMessageAsync("payload-1");
            String msg = pair.listener.pollMessage(5, TimeUnit.SECONDS);
            assertNotNull(msg);
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies a second message round trip through the established session.
     */
    @Test
    void testPollMessage2() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            exchangePeerIds(pair.listener, pair.connector);
            performKeyExchange(pair.listener, pair.connector);
            pair.connector.sendMessageAsync("payload-2");
            String msg = pair.listener.pollMessage(5, TimeUnit.SECONDS);
            assertNotNull(msg);
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies async sending delivers a buffered message on the peer side.
     */
    @Test
    void testSendMessageAsync() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            exchangePeerIds(pair.listener, pair.connector);
            performKeyExchange(pair.listener, pair.connector);
            pair.connector.sendMessageAsync("payload-3");
            String msg = pair.listener.pollMessage(5, TimeUnit.SECONDS);
            assertNotNull(msg);
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies async sending works in the reverse direction.
     */
    @Test
    void testSendMessageAsync2() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            exchangePeerIds(pair.listener, pair.connector);
            performKeyExchange(pair.listener, pair.connector);
            pair.listener.sendMessageAsync("payload-4");
            String msg = pair.connector.pollMessage(5, TimeUnit.SECONDS);
            assertNotNull(msg);
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }

    /**
     * Verifies both peers report readiness after pairing.
     */
    @Test
    void testWaitForConnectionReady() {
        PeerPair pair = null;
        try {
            pair = createConnectedPeers();
            assertTrue(pair.listener.waitForConnectionReady(5000));
            assertTrue(pair.connector.waitForConnectionReady(5000));
        } catch (Exception e) {
            assertTrue(false);
        } finally {
            if (pair != null) {
                pair.listener.close();
                pair.connector.close();
            }
        }
    }
}
