package com.ryuqq.aws.sqs.consumer.container;

/**
 * Enumeration representing the lifecycle states of an SQS listener container.
 * 
 * <p>State transitions follow a specific pattern:</p>
 * <ul>
 *   <li>CREATED → STARTING → RUNNING</li>
 *   <li>RUNNING → STOPPING → STOPPED</li>
 *   <li>Any state → FAILED (error condition)</li>
 *   <li>STOPPED → STARTING (restart)</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public enum ContainerState {
    
    /**
     * Container has been created but not yet started.
     */
    CREATED {
        @Override
        public boolean canStart() {
            return true;
        }
        
        @Override
        public boolean canStop() {
            return false;
        }
        
        @Override
        public boolean canTransitionTo(ContainerState to) {
            return to == STARTING || to == FAILED;
        }
    },
    
    /**
     * Container is in the process of starting up.
     */
    STARTING {
        @Override
        public boolean canStart() {
            return false;
        }
        
        @Override
        public boolean canStop() {
            return true; // Allow stopping during startup
        }
        
        @Override
        public boolean canTransitionTo(ContainerState to) {
            return to == RUNNING || to == FAILED || to == STOPPING;
        }
    },
    
    /**
     * Container is actively running and processing messages.
     */
    RUNNING {
        @Override
        public boolean canStart() {
            return false;
        }
        
        @Override
        public boolean canStop() {
            return true;
        }
        
        @Override
        public boolean canTransitionTo(ContainerState to) {
            return to == STOPPING || to == FAILED;
        }
    },
    
    /**
     * Container is in the process of shutting down.
     */
    STOPPING {
        @Override
        public boolean canStart() {
            return false;
        }
        
        @Override
        public boolean canStop() {
            return false; // Already stopping
        }
        
        @Override
        public boolean canTransitionTo(ContainerState to) {
            return to == STOPPED || to == FAILED;
        }
    },
    
    /**
     * Container has been stopped and is not processing messages.
     */
    STOPPED {
        @Override
        public boolean canStart() {
            return true; // Allow restart
        }
        
        @Override
        public boolean canStop() {
            return false; // Already stopped
        }
        
        @Override
        public boolean canTransitionTo(ContainerState to) {
            return to == STARTING || to == FAILED;
        }
    },
    
    /**
     * Container has encountered an error and is in a failed state.
     */
    FAILED {
        @Override
        public boolean canStart() {
            return true; // Allow recovery attempt
        }
        
        @Override
        public boolean canStop() {
            return true; // Allow cleanup
        }
        
        @Override
        public boolean canTransitionTo(ContainerState to) {
            // From failed state, can attempt to start or stop
            return to == STARTING || to == STOPPING || to == STOPPED;
        }
    };
    
    /**
     * Check if the container can be started from this state.
     * 
     * @return true if start operation is allowed
     */
    public abstract boolean canStart();
    
    /**
     * Check if the container can be stopped from this state.
     * 
     * @return true if stop operation is allowed
     */
    public abstract boolean canStop();
    
    /**
     * Check if transition to the specified state is allowed.
     * 
     * @param to the target state
     * @return true if transition is valid
     */
    public abstract boolean canTransitionTo(ContainerState to);
    
    /**
     * Check if this is a terminal state (cannot be changed).
     * 
     * @return true if this is a terminal state
     */
    public boolean isTerminal() {
        return this == STOPPED || this == FAILED;
    }
    
    /**
     * Check if this is an active processing state.
     * 
     * @return true if container is actively processing messages
     */
    public boolean isActive() {
        return this == RUNNING;
    }
    
    /**
     * Check if this is a transitional state.
     * 
     * @return true if container is in process of changing states
     */
    public boolean isTransitional() {
        return this == STARTING || this == STOPPING;
    }
}