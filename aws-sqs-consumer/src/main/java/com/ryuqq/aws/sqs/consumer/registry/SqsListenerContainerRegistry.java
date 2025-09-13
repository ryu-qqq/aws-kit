package com.ryuqq.aws.sqs.consumer.registry;

import com.ryuqq.aws.sqs.consumer.container.SqsListenerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registry for managing SQS listener containers.
 * Implements Spring's SmartLifecycle for proper startup/shutdown integration.
 * 
 * CONCURRENCY FIX: Replaced unsafe parallelStream operations with thread-safe
 * sequential processing and proper synchronization mechanisms.
 */
@Component
public class SqsListenerContainerRegistry implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsListenerContainerRegistry.class);

    private final ConcurrentMap<String, SqsListenerContainer> containers = new ConcurrentHashMap<>();
    private final AtomicReference<RegistryState> state = new AtomicReference<>(RegistryState.STOPPED);
    
    // Synchronization object for registry state transitions
    private final Object registryLock = new Object();
    
    /**
     * Registry state enumeration for thread-safe state management.
     */
    public enum RegistryState {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING
    }
    
    /**
     * Register a new SQS listener container with thread safety.
     */
    public void registerContainer(String containerId, SqsListenerContainer container) {
        if (containerId == null || containerId.isEmpty()) {
            throw new IllegalArgumentException("Container ID cannot be null or empty");
        }
        
        if (container == null) {
            throw new IllegalArgumentException("Container cannot be null");
        }
        
        SqsListenerContainer existingContainer = containers.putIfAbsent(containerId, container);
        if (existingContainer != null) {
            throw new IllegalArgumentException("Container with ID already exists: " + containerId);
        }
        
        log.info("Registered SQS listener container: {}", containerId);
        
        // Auto-start if registry is already running (thread-safe check)
        if (state.get() == RegistryState.RUNNING) {
            try {
                container.start();
                log.info("Auto-started container {} as registry is already running", containerId);
            } catch (Exception e) {
                log.error("Failed to auto-start container {}: {}", containerId, e.getMessage(), e);
                // Remove the container if it fails to start
                containers.remove(containerId);
                throw new RuntimeException("Failed to auto-start container: " + containerId, e);
            }
        }
    }
    
    /**
     * Unregister an SQS listener container with proper cleanup.
     */
    public void unregisterContainer(String containerId) {
        if (containerId == null || containerId.isEmpty()) {
            log.warn("Cannot unregister container with null or empty ID");
            return;
        }
        
        SqsListenerContainer container = containers.remove(containerId);
        if (container != null) {
            try {
                container.stop();
                log.info("Unregistered SQS listener container: {}", containerId);
            } catch (Exception e) {
                log.error("Error stopping container {} during unregistration: {}", containerId, e.getMessage(), e);
            }
        } else {
            log.debug("Container {} was not registered", containerId);
        }
    }
    
    /**
     * Get a specific container by ID.
     */
    public SqsListenerContainer getContainer(String containerId) {
        if (containerId == null || containerId.isEmpty()) {
            return null;
        }
        return containers.get(containerId);
    }
    
    /**
     * Get all registered containers (thread-safe snapshot).
     */
    public Collection<SqsListenerContainer> getAllContainers() {
        // Return a snapshot to avoid concurrent modification issues
        return List.copyOf(containers.values());
    }
    
    /**
     * Get container count.
     */
    public int getContainerCount() {
        return containers.size();
    }
    
    /**
     * Get registry statistics with thread-safe aggregation.
     */
    public RegistryStats getStats() {
        // Create snapshot to avoid concurrent modification during computation
        Collection<SqsListenerContainer> containerSnapshot = getAllContainers();
        
        long runningContainers = containerSnapshot.stream()
            .mapToLong(container -> container.isRunning() ? 1 : 0)
            .sum();
        
        long totalProcessed = containerSnapshot.stream()
            .mapToLong(container -> container.getStats().getProcessedMessages())
            .sum();
        
        long totalFailed = containerSnapshot.stream()
            .mapToLong(container -> container.getStats().getFailedMessages())
            .sum();
        
        return new RegistryStats(
            containerSnapshot.size(),
            runningContainers,
            totalProcessed,
            totalFailed,
            state.get()
        );
    }
    
    /**
     * Start all containers with proper error handling and synchronization.
     * CONCURRENCY FIX: Replaced parallelStream with sequential processing and proper synchronization.
     */
    @Override
    public void start() {
        synchronized (registryLock) {
            RegistryState currentState = state.get();
            
            if (currentState == RegistryState.RUNNING) {
                log.debug("Registry is already running");
                return;
            }
            
            if (currentState == RegistryState.STARTING) {
                log.debug("Registry is already starting");
                return;
            }
            
            if (!state.compareAndSet(currentState, RegistryState.STARTING)) {
                log.warn("Failed to transition registry to STARTING state");
                return;
            }
        }
        
        log.info("Starting SQS listener container registry with {} containers", containers.size());
        
        try {
            // Start containers sequentially to avoid concurrent modification issues
            List<SqsListenerContainer> containerList = List.copyOf(containers.values());
            int successCount = 0;
            int failureCount = 0;
            
            for (SqsListenerContainer container : containerList) {
                try {
                    container.start();
                    successCount++;
                    log.debug("Started container: {}", container.getStats().getContainerId());
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to start container: {}", e.getMessage(), e);
                }
            }
            
            // Transition to running state
            if (!state.compareAndSet(RegistryState.STARTING, RegistryState.RUNNING)) {
                throw new IllegalStateException("Failed to transition registry to RUNNING state");
            }
            
            log.info("SQS listener container registry started successfully. Success: {}, Failures: {}", 
                successCount, failureCount);
                
        } catch (Exception e) {
            // Transition to stopped state on failure
            state.set(RegistryState.STOPPED);
            log.error("Failed to start registry: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start SQS listener container registry", e);
        }
    }
    
    /**
     * Stop all containers with proper synchronization and timeout handling.
     * CONCURRENCY FIX: Replaced parallelStream with sequential processing and CountDownLatch for coordination.
     */
    @Override
    public void stop() {
        synchronized (registryLock) {
            RegistryState currentState = state.get();
            
            if (currentState == RegistryState.STOPPED) {
                log.debug("Registry is already stopped");
                return;
            }
            
            if (currentState == RegistryState.STOPPING) {
                log.debug("Registry is already stopping");
                return;
            }
            
            if (!state.compareAndSet(currentState, RegistryState.STOPPING)) {
                log.warn("Failed to transition registry to STOPPING state");
                return;
            }
        }
        
        log.info("Stopping SQS listener container registry");
        
        try {
            List<SqsListenerContainer> containerList = List.copyOf(containers.values());
            
            if (containerList.isEmpty()) {
                log.info("No containers to stop");
                state.set(RegistryState.STOPPED);
                return;
            }
            
            // Use CountDownLatch for coordinated shutdown with timeout
            CountDownLatch stopLatch = new CountDownLatch(containerList.size());
            int successCount = 0;
            int failureCount = 0;
            
            // Stop all containers sequentially to avoid resource contention
            for (SqsListenerContainer container : containerList) {
                try {
                    container.stop();
                    successCount++;
                    log.debug("Stopped container: {}", container.getStats().getContainerId());
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to stop container: {}", e.getMessage(), e);
                } finally {
                    stopLatch.countDown();
                }
            }
            
            // Wait for all stop operations to complete with timeout
            boolean allStopped = stopLatch.await(60, TimeUnit.SECONDS);
            if (!allStopped) {
                log.warn("Not all containers stopped within timeout period");
            }
            
            // Transition to stopped state
            state.set(RegistryState.STOPPED);
            log.info("SQS listener container registry stopped. Success: {}, Failures: {}", 
                successCount, failureCount);
                
        } catch (Exception e) {
            state.set(RegistryState.STOPPED);
            log.error("Error during registry stop: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isRunning() {
        return state.get() == RegistryState.RUNNING;
    }
    
    /**
     * Get current registry state.
     */
    public RegistryState getRegistryState() {
        return state.get();
    }
    
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100; // Start after most other components
    }
    
    /**
     * Registry statistics data class with enhanced state information.
     */
    public static class RegistryStats {
        private final int totalContainers;
        private final long runningContainers;
        private final long totalProcessedMessages;
        private final long totalFailedMessages;
        private final RegistryState registryState;
        
        public RegistryStats(int totalContainers, long runningContainers, 
                           long totalProcessedMessages, long totalFailedMessages,
                           RegistryState registryState) {
            this.totalContainers = totalContainers;
            this.runningContainers = runningContainers;
            this.totalProcessedMessages = totalProcessedMessages;
            this.totalFailedMessages = totalFailedMessages;
            this.registryState = registryState;
        }
        
        public int getTotalContainers() { return totalContainers; }
        public long getRunningContainers() { return runningContainers; }
        public long getTotalProcessedMessages() { return totalProcessedMessages; }
        public long getTotalFailedMessages() { return totalFailedMessages; }
        public RegistryState getRegistryState() { return registryState; }
        
        @Override
        public String toString() {
            return String.format("RegistryStats{total=%d, running=%d, processed=%d, failed=%d, state=%s}", 
                totalContainers, runningContainers, totalProcessedMessages, totalFailedMessages, registryState);
        }
    }
}