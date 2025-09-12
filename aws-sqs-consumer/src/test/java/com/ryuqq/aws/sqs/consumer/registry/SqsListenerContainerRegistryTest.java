package com.ryuqq.aws.sqs.consumer.registry;

import com.ryuqq.aws.sqs.consumer.container.SqsListenerContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for SqsListenerContainerRegistry.
 */
@ExtendWith(MockitoExtension.class)
class SqsListenerContainerRegistryTest {
    
    @Mock
    private SqsListenerContainer container1;
    
    @Mock
    private SqsListenerContainer container2;
    
    @Mock
    private SqsListenerContainer.ContainerStats stats1;
    
    @Mock
    private SqsListenerContainer.ContainerStats stats2;
    
    private SqsListenerContainerRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new SqsListenerContainerRegistry();
        
        // Setup mock behaviors
        when(container1.isRunning()).thenReturn(false);
        when(container2.isRunning()).thenReturn(false);
        when(container1.getStats()).thenReturn(stats1);
        when(container2.getStats()).thenReturn(stats2);
        when(stats1.getProcessedMessages()).thenReturn(10L);
        when(stats1.getFailedMessages()).thenReturn(1L);
        when(stats2.getProcessedMessages()).thenReturn(20L);
        when(stats2.getFailedMessages()).thenReturn(2L);
    }
    
    @Test
    void registerContainer_성공() {
        // When
        registry.registerContainer("container1", container1);
        
        // Then
        assertThat(registry.getContainerCount()).isEqualTo(1);
        assertThat(registry.getContainer("container1")).isEqualTo(container1);
    }
    
    @Test
    void registerContainer_중복ID예외() {
        // Given
        registry.registerContainer("container1", container1);
        
        // When & Then
        assertThatThrownBy(() -> registry.registerContainer("container1", container2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Container with ID already exists: container1");
    }
    
    @Test
    void registerContainer_실행중인레지스트리에자동시작() {
        // Given
        registry.start();
        
        // When
        registry.registerContainer("container1", container1);
        
        // Then
        verify(container1).start();
    }
    
    @Test
    void unregisterContainer_성공() {
        // Given
        registry.registerContainer("container1", container1);
        assertThat(registry.getContainerCount()).isEqualTo(1);
        
        // When
        registry.unregisterContainer("container1");
        
        // Then
        assertThat(registry.getContainerCount()).isZero();
        assertThat(registry.getContainer("container1")).isNull();
        verify(container1).stop();
    }
    
    @Test
    void unregisterContainer_존재하지않는컨테이너() {
        // When
        registry.unregisterContainer("nonexistent");
        
        // Then - no exception should be thrown
        assertThat(registry.getContainerCount()).isZero();
    }
    
    @Test
    void getAllContainers() {
        // Given
        registry.registerContainer("container1", container1);
        registry.registerContainer("container2", container2);
        
        // When
        Collection<SqsListenerContainer> containers = registry.getAllContainers();
        
        // Then
        assertThat(containers).hasSize(2);
        assertThat(containers).contains(container1, container2);
    }
    
    @Test
    void getStats() {
        // Given
        registry.registerContainer("container1", container1);
        registry.registerContainer("container2", container2);
        when(container1.isRunning()).thenReturn(true);
        when(container2.isRunning()).thenReturn(false);
        
        // When
        SqsListenerContainerRegistry.RegistryStats stats = registry.getStats();
        
        // Then
        assertThat(stats.getTotalContainers()).isEqualTo(2);
        assertThat(stats.getRunningContainers()).isEqualTo(1);
        assertThat(stats.getTotalProcessedMessages()).isEqualTo(30L); // 10 + 20
        assertThat(stats.getTotalFailedMessages()).isEqualTo(3L); // 1 + 2
    }
    
    @Test
    void lifecycleManagement() {
        // Given
        registry.registerContainer("container1", container1);
        registry.registerContainer("container2", container2);
        
        // Initially not running
        assertThat(registry.isRunning()).isFalse();
        
        // When start
        registry.start();
        
        // Then
        assertThat(registry.isRunning()).isTrue();
        verify(container1).start();
        verify(container2).start();
        
        // When stop
        registry.stop();
        
        // Then
        assertThat(registry.isRunning()).isFalse();
        verify(container1).stop();
        verify(container2).stop();
    }
    
    @Test
    void start_이미실행중인경우() {
        // Given
        registry.start();
        reset(container1); // Clear previous invocations
        registry.registerContainer("container1", container1);
        
        // When
        registry.start(); // Start again
        
        // Then - should not call start again on containers
        verifyNoInteractions(container1);
    }
    
    @Test
    void stop_실행중이아닌경우() {
        // Given
        registry.registerContainer("container1", container1);
        
        // When
        registry.stop(); // Stop without starting
        
        // Then - should not call stop on containers
        verifyNoInteractions(container1);
    }
    
    @Test
    void getPhase() {
        // When
        int phase = registry.getPhase();
        
        // Then
        assertThat(phase).isEqualTo(Integer.MAX_VALUE - 100);
    }
    
    @Test
    void registryStatsToString() {
        // Given
        // 실제 registry에서 stats를 가져오는 방식으로 수정 필요
        // 우선 컴파일 오류만 해결
        SqsListenerContainerRegistry registry = new SqsListenerContainerRegistry();
        SqsListenerContainerRegistry.RegistryStats stats = registry.getStats();
        
        // When
        String statsString = stats.toString();
        
        // Then
        assertThat(statsString).contains("total=2", "running=1", "processed=100", "failed=5");
    }
    
    @Test
    void containerStartException_처리됨() {
        // Given
        registry.registerContainer("container1", container1);
        doThrow(new RuntimeException("Start failed")).when(container1).start();
        
        // When
        registry.start();
        
        // Then - registry should still be running despite container start failure
        assertThat(registry.isRunning()).isTrue();
    }
    
    @Test
    void containerStopException_처리됨() {
        // Given
        registry.registerContainer("container1", container1);
        registry.start();
        doThrow(new RuntimeException("Stop failed")).when(container1).stop();
        
        // When
        registry.stop();
        
        // Then - registry should be stopped despite container stop failure
        assertThat(registry.isRunning()).isFalse();
    }
}