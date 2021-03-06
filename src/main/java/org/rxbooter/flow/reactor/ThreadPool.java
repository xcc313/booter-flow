package org.rxbooter.flow.reactor;

/*
 * Copyright (c) 2017 Sergiy Yevtushenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

import org.rxbooter.flow.reactor.impl.DaemonThreadFactory;
import org.rxbooter.flow.reactor.impl.FixedThreadPool;

import java.util.concurrent.ThreadFactory;

/**
 * Abstract thread pool necessary for {@link Reactor}.
 */
public interface ThreadPool {
    int DEFAULT_COMPUTING_POOL_SIZE = FixedThreadPool.DEFAULT_COMPUTING_POOL_SIZE;
    int DEFAULT_IO_POOL_SIZE = FixedThreadPool.DEFAULT_IO_POOL_SIZE;

    ThreadFactory DEFAULT_COMPUTING_THREAD_FACTORY = new DaemonThreadFactory("ThreadPoolReactor-computing-");
    ThreadFactory DEFAULT_IO_THREAD_FACTORY = new DaemonThreadFactory("ThreadPoolReactor-io-");

    /**
     * Start thread pool. All threads will run provided {@link Runnable} instance.
     *
     * @param target
     *          {@link Runnable} instance to be executed in thread pool.
     *
     * @return {@code this} for call chaining (fluent syntax).
     */
    ThreadPool start(Runnable target);

    /**
     * Start shutting down of the thread pool.
     */
    void shutdown();

    /**
     * Factory method for creating thread pool configured for blocking I/O operations.
     *
     * @return created thread pool
     */
    static ThreadPool defaultIo() {
        return new FixedThreadPool(DEFAULT_IO_POOL_SIZE, DEFAULT_IO_THREAD_FACTORY);
    }

    /**
     * Factory method for creating thread pool configured for non-blocking computing operations.
     *
     * @return created thread pool
     */
    static ThreadPool defaultComputing() {
        return new FixedThreadPool(DEFAULT_COMPUTING_POOL_SIZE, DEFAULT_COMPUTING_THREAD_FACTORY);
    }

    /**
     * Create thread pool of specified size configured for blocking I/O operations.
     *
     * @param size
     *          Size of thread pool
     * @return created thread pool
     */
    static ThreadPool fixedIo(int size) {
        return new FixedThreadPool(size, DEFAULT_IO_THREAD_FACTORY);
    }

    /**
     * Create thread pool of specified size configured for non-blocking computing operations.
     *
     * @param size
     *          Size of thread pool
     * @return created thread pool
     */
    static ThreadPool fixedComputing(int size) {
        return new FixedThreadPool(size, DEFAULT_COMPUTING_THREAD_FACTORY);
    }
}
