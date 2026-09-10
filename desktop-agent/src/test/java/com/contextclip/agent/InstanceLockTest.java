package com.contextclip.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InstanceLockTest {

    private int testPort;
    private InstanceLock primaryLock;
    private InstanceLock secondaryLock;

    @BeforeEach
    void setUp() throws IOException {
        // Allocate a free ephemeral port for each test run
        try (ServerSocket s = new ServerSocket(0)) {
            testPort = s.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() {
        if (primaryLock != null) {
            primaryLock.release();
        }
        if (secondaryLock != null) {
            secondaryLock.release();
        }
    }

    @Test
    void testAcquirePrimary() {
        primaryLock = new InstanceLock(testPort);
        InstanceLock.Role role = primaryLock.tryAcquire();

        assertEquals(InstanceLock.Role.PRIMARY, role);
    }

    @Test
    void testSecondaryDetectionWhenPrimaryRunning() {
        primaryLock = new InstanceLock(testPort);
        InstanceLock.Role primaryRole = primaryLock.tryAcquire();
        assertEquals(InstanceLock.Role.PRIMARY, primaryRole);

        secondaryLock = new InstanceLock(testPort);
        InstanceLock.Role secondaryRole = secondaryLock.tryAcquire();
        assertEquals(InstanceLock.Role.SECONDARY, secondaryRole);
    }

    @Test
    void testForwardPairingUriOverIpc() throws InterruptedException {
        primaryLock = new InstanceLock(testPort);
        primaryLock.tryAcquire();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedUri = new AtomicReference<>();

        primaryLock.startIpcServer(uri -> {
            receivedUri.set(uri);
            latch.countDown();
        });

        secondaryLock = new InstanceLock(testPort);
        assertEquals(InstanceLock.Role.SECONDARY, secondaryLock.tryAcquire());

        String testUri = "contextclip://pair?code=pair_test_12345";
        boolean success = secondaryLock.forwardToExistingInstance(testUri);

        assertTrue(success, "Secondary should successfully forward the URI");
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Primary should receive the IPC message within 3 seconds");
        assertEquals(testUri, receivedUri.get());
    }

    @Test
    void testForwardRawPairCodeOverIpc() throws InterruptedException {
        primaryLock = new InstanceLock(testPort);
        primaryLock.tryAcquire();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedUri = new AtomicReference<>();

        primaryLock.startIpcServer(uri -> {
            receivedUri.set(uri);
            latch.countDown();
        });

        secondaryLock = new InstanceLock(testPort);
        boolean success = secondaryLock.forwardToExistingInstance("pair_token_xyz");

        assertTrue(success);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("pair_token_xyz", receivedUri.get());
    }

    @Test
    void testRejectInvalidUri() throws InterruptedException {
        primaryLock = new InstanceLock(testPort);
        primaryLock.tryAcquire();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedUri = new AtomicReference<>();

        primaryLock.startIpcServer(uri -> {
            receivedUri.set(uri);
            latch.countDown();
        });

        secondaryLock = new InstanceLock(testPort);
        boolean success = secondaryLock.forwardToExistingInstance("http://malicious-site.com");

        assertFalse(success, "Non-contextclip URI should be rejected");
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS), "Callback should not have been invoked");
        assertNull(receivedUri.get());
    }

    @Test
    void testForwardFailsGracefullyWhenPrimaryNotRunning() {
        secondaryLock = new InstanceLock(testPort);
        // No primary is listening on testPort
        boolean success = secondaryLock.forwardToExistingInstance("contextclip://pair?code=pair_none");
        assertFalse(success, "Forwarding should return false if no primary is listening");
    }

    @Test
    void testReleaseFreesPortForNextInstance() {
        primaryLock = new InstanceLock(testPort);
        assertEquals(InstanceLock.Role.PRIMARY, primaryLock.tryAcquire());

        // Release the primary lock
        primaryLock.release();

        // Now a new instance should be able to acquire PRIMARY
        secondaryLock = new InstanceLock(testPort);
        assertEquals(InstanceLock.Role.PRIMARY, secondaryLock.tryAcquire());
    }
}

