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

public class AsyncPeerTest {
    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static class PeerPair {
        private final AsyncPeer listener;
        private final AsyncPeer connector;

        private PeerPair(AsyncPeer listener, AsyncPeer connector) {
            this.listener = listener;
            this.connector = connector;
        }
    }

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

    private void performKeyExchange(AsyncPeer a, AsyncPeer b) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        a.performKeyExchangeAsync(latch::countDown);
        b.performKeyExchangeAsync(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(a.isEncrypted());
        assertTrue(b.isEncrypted());
    }

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
