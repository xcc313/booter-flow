package org.rxbooter.flow.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.rxbooter.flow.ExecutionType;
import org.rxbooter.flow.Flow;
import org.rxbooter.flow.Reactor;
import org.rxbooter.flow.Step.EH;
import org.rxbooter.flow.Tuples;
import org.rxbooter.flow.Tuples.Tuple;
import org.rxbooter.flow.Tuples.Tuple1;

public class ThreadPoolReactor implements Reactor {
    private static final long POLL_INTERVAL = 100;

    private final BlockingQueue<FlowExecutor<?, ?>> computingInput = new LinkedBlockingQueue<>();
    private final BlockingQueue<FlowExecutor<?, ?>> blockingInput = new LinkedBlockingQueue<>();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    public ThreadPoolReactor(ThreadPool computingPool, ThreadPool ioPool) {
        computingPool.start(this::computingHandler);
        ioPool.start(this::ioHandler);
    }

    public static ThreadPoolReactor defaultReactor() {
        return DefaultReactorHolder.INSTANCE.reactor();
    }

    public static ThreadPoolReactor with(ThreadPool computingPool, ThreadPool ioPool) {
        return new ThreadPoolReactor(computingPool, ioPool);
    }

    @Override
    public void shutdown() {
        shutdown.compareAndSet(false, true);
    }

    @Override
    public <O extends Tuple, I extends Tuple> O await(FlowExecutor<O, I> flowExecutor) {
        return putTask(flowExecutor).promise().await();
    }

    @Override
    public <O extends Tuple, I extends Tuple> void async(FlowExecutor<O, I> flowExecutor) {
        putTask(flowExecutor);
    }

    @Override
    public void async(Runnable runnable) {
        putTask(Flow.async((a) -> {runnable.run(); return Tuples.empty();}).applyTo(null));
    }

    @Override
    public <T> T await(Supplier<T> function) {
        return await(Flow.await((t) -> Tuples.of(function.get())).applyTo(null)).get();
    }

    @Override
    public void async(Runnable runnable, EH<Tuple1<Void>> handler) {
        putTask(Flow.async((a) -> {runnable.run(); return Tuples.of(null);}, handler).applyTo(null));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T await(Supplier<T> function, EH<Tuple1<T>> handler) {
        return await(Flow.await((t) -> Tuples.of(function.get()), handler).applyTo(null)).get();
    }

    @Override
    public <T> T awaitAny(Supplier<T>... suppliers) {
        Promise<Tuple1<T>> promise = Promise.empty();

        for (Supplier<T> supplier : suppliers) {
            putTask(Flow.await((t) -> Tuples.of(supplier.get())).applyTo(null, promise));
        }

        return promise.await().get();
    }

    @Override
    public <O extends Tuple, I extends Tuple> Promise<O> submit(FlowExecutor<O, I> flowExecutor) {
        return putTask(flowExecutor).promise();
    }

    private void ioHandler() {
        while (!shutdown.get()) {
            FlowExecutor<?, ?> flowExecutor = pollQueueForSingle(blockingInput);

            if (flowExecutor == null) {
                continue;
            }

            if (flowExecutor.isAsync()) {
                putTask(flowExecutor.forCurrent());
                flowExecutor.advance();
            }

            flowExecutor.stepAndAdvance();
            putTask(flowExecutor);
        }
    }

    private void computingHandler() {
        while (!shutdown.get()) {
            List<FlowExecutor<?, ?>> flowExecutors = pollQueue(computingInput);

            if (flowExecutors.isEmpty()) {
                continue;
            }

            flowExecutors.forEach(this::executeSingle);
        }
    }

    private void executeSingle(FlowExecutor<?, ?> flowExecutor) {
        if (flowExecutor.isAsync()) {
            putTask(flowExecutor.forCurrent());
            flowExecutor.advance();
        }

        ExecutionType type = flowExecutor.type();

        if (type == null) {
            return;
        }

        while (type == flowExecutor.type()) {
            flowExecutor.stepAndAdvance();
        }

        putTask(flowExecutor);
    }

    private List<FlowExecutor<?, ?>> pollQueue(BlockingQueue<FlowExecutor<?, ?>> queue) {
        try {
            FlowExecutor<?, ?> element = queue.poll(POLL_INTERVAL, TimeUnit.MILLISECONDS);
            if (element == null) {
                return Collections.emptyList();
            }
            List<FlowExecutor<?, ?>> result = new ArrayList<>();
            result.add(element);
            queue.drainTo(result);
            return result;

        } catch (InterruptedException e) {
            // Ignore it and return empty collection
            return Collections.emptyList();
        }
    }

    private FlowExecutor<?, ?> pollQueueForSingle(BlockingQueue<FlowExecutor<?, ?>> queue) {
        try {
            return queue.poll(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Ignore it and return null
            return null;
        }
    }

    private<O extends Tuple, I extends Tuple> FlowExecutor<O, I> putTask(FlowExecutor<O, I> flowExecutor) {
        if(flowExecutor.isReady()) {
            return flowExecutor;
        }

        try {
            (flowExecutor.isBlocking() ? blockingInput : computingInput).put(flowExecutor);
        } catch (InterruptedException e) {
            //TODO: how take handle it correctly? can we just ignore it?
        }
        return flowExecutor;
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