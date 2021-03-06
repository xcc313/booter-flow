package org.rxbooter.flow.reactor.impl;

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

import org.rxbooter.flow.Flow;
import org.rxbooter.flow.Functions;
import org.rxbooter.flow.Tuples;
import org.rxbooter.flow.Tuples.Tuple;
import org.rxbooter.flow.Tuples.Tuple1;
import org.rxbooter.flow.impl.ExecutableFlow;
import org.rxbooter.flow.reactor.*;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Simple yet full featured {@link Reactor} implementation.
 * <br/>
 * This implementation is built around two thread pools - one (computation) thread pool is used
 * for {@link ExecutionType#SYNC} steps and second (I/O or blocking) for remaining tasks.
 */
public class ThreadPoolReactor implements Reactor {
    private static final long POLL_INTERVAL = 100;

    private final BlockingQueue<ExecutableFlow<?, ?>> computingInput = new LinkedBlockingQueue<>();
    private final BlockingQueue<ExecutableFlow<?, ?>> blockingInput = new LinkedBlockingQueue<>();
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final ThreadPool computingPool;
    private final ThreadPool ioPool;

    private ThreadPoolReactor(ThreadPool computingPool, ThreadPool ioPool) {
        this.computingPool = computingPool;
        this.ioPool = ioPool;

        this.computingPool.start(this::computingHandler);
        this.ioPool.start(this::ioHandler);
    }

    /**
     * Obtain default instance of {@link ThreadPoolReactor}
     * @return default reactor instance.
     */
    public static ThreadPoolReactor defaultReactor() {
        return DefaultReactorHolder.INSTANCE.reactor();
    }

    /**
     * Create instance of {@link ThreadPoolReactor} with which will use specified thread pools.
     *
     * @param computingPool
     *          Computing pool which is used for fast non-blocking flow steps execution.
     * @param ioPool
     *          I/O or blocking pool which is used for all asynchronous and blocking tasks.
     * @return built instance of {@link ThreadPoolReactor}
     */
    public static ThreadPoolReactor with(ThreadPool computingPool, ThreadPool ioPool) {
        return new ThreadPoolReactor(computingPool, ioPool);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public final <T> Optional<T> awaitAny(Supplier<T>... suppliers) {
        Promise<Tuple1<T>> promise = Promise.waitingFor(suppliers.length);

        Arrays.stream(suppliers)
              .map(s -> Flow.of(Step.await(Functions.TF.from(s)), Tuples.empty1()))
              .map(f -> f.applyTo(null, promise))
              .forEach(this::submit);

        return promise.safeAwait().map(Tuple1::get1);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public final <T> Optional<T> awaitFirst(Supplier<T>... suppliers) {
        for(Supplier<T> supplier : suppliers) {
            Optional<T> result = submit(supplier).safeAwait().map(t -> t.get1());

            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /**
     * Shutdown reactor.
     */
    @Override
    public void shutdown() {
        shutdown.compareAndSet(false, true);
        computingPool.shutdown();
        ioPool.shutdown();
    }

    /**
     * Submit {@link ExecutableFlow} to reactor for execution. Submission is performed asynchronously
     * and result if returned immediately. Notification about execution are performed via returned
     * instance of {@link Promise} associated with the {@link ExecutableFlow} instance.
     *
     * @param executableFlow
     *          {@link ExecutableFlow} instance which need to be processed.
     * @return  Instance of {@link Promise} associated with submitted {@link ExecutableFlow}.
     */
    @Override
    public <O extends Tuple, I extends Tuple> Promise<O> submit(ExecutableFlow<O, I> executableFlow) {
        if (shutdown.get()) {
            throw new IllegalStateException("Reactor is shutdown()");
        }

        if(executableFlow.isReady()) {
            return executableFlow.promise();
        }

        if(!executableFlow.canRun()) {
            return executableFlow.promise();
        }

        (executableFlow.isBlocking() ? blockingInput : computingInput).offer(executableFlow);

        return executableFlow.promise();
    }

    private void ioHandler() {
        while (!shutdown.get()) {
            ExecutableFlow<?, ?> executableFlow = pollQueueForSingle(blockingInput);

            if (executableFlow == null) {
                continue;
            }

            executableFlow.invoke();
            submit(executableFlow);
        }
    }

    private void computingHandler() {
        while (!shutdown.get()) {
            List<ExecutableFlow<?, ?>> executableFlows = pollQueue(computingInput);

            if (executableFlows.isEmpty()) {
                continue;
            }

            executableFlows.forEach(this::executeSingle);
        }
    }

    private void executeSingle(ExecutableFlow<?, ?> executableFlow) {
        runAllAsync(executableFlow);

        while (ExecutionType.SYNC == executableFlow.type()) {
            executableFlow.invoke();
        }

        submit(executableFlow);
    }

    protected void runAllAsync(ExecutableFlow<?, ?> executableFlow) {
        while (executableFlow.isAsync()) {
            submit(executableFlow.forCurrent());
        }
    }

    private List<ExecutableFlow<?, ?>> pollQueue(BlockingQueue<ExecutableFlow<?, ?>> queue) {
        try {
            ExecutableFlow<?, ?> element = queue.poll(POLL_INTERVAL, TimeUnit.MILLISECONDS);
            if (element == null) {
                return Collections.emptyList();
            }
            List<ExecutableFlow<?, ?>> result = new ArrayList<>();
            result.add(element);
            queue.drainTo(result);
            return result;

        } catch (InterruptedException e) {
            // Ignore it and return empty collection
            return Collections.emptyList();
        }
    }

    private ExecutableFlow<?, ?> pollQueueForSingle(BlockingQueue<ExecutableFlow<?, ?>> queue) {
        try {
            return queue.poll(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Ignore it and return null
            return null;
        }
    }

    private enum DefaultReactorHolder {
        INSTANCE;

        private final ThreadPoolReactor reactor;

        DefaultReactorHolder() {
            reactor = new ThreadPoolReactor(ThreadPool.defaultComputing(), ThreadPool.defaultIo());
        }

        public ThreadPoolReactor reactor() {
            return reactor;
        }
    }
}
