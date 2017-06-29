package org.rxbooter.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.rxbooter.flow.Step.EH;
import org.rxbooter.flow.Step.TF;
import org.rxbooter.flow.impl.FlowBuilders.FlowBuilder0;
import org.rxbooter.flow.impl.FlowExecutor;
import org.rxbooter.flow.impl.Promise;

import static org.rxbooter.flow.Tuples.Tuple;

public class Flow<O extends Tuple, I extends Tuple> {
    private final List<Step<?, ?>> steps;

    private Flow(List<Step<?, ?>> steps) {
        this.steps = steps;
    }

    public FlowExecutor<O, I> applyTo(I input) {
        return new FlowExecutor<>(steps, input, Promise.with());
    }

    public FlowExecutor<O, I> applyTo(I input, Promise<O> promise) {
        return new FlowExecutor<>(steps, input, promise);
    }

    public static <O1 extends Tuple, I1 extends Tuple> Flow<O1, I1> of(FlowBuilder0<O1> builder) {
        List<Step<?, ?>> steps = new ArrayList<>();
        builder.apply(steps::add);

        return new Flow<>(steps);
    }

    public static <O1 extends Tuple, I1 extends Tuple> Flow<O1, I1> single(TF<O1, I1> function) {
        return new Flow<>(Collections.singletonList(Step.sync(function)));
    }

    public static <O1 extends Tuple, I1 extends Tuple> Flow<O1, I1> single(TF<O1, I1> function, EH<O1> errorHandler) {
        return new Flow<>(Collections.singletonList(Step.sync(function, errorHandler)));
    }

    public static <O1 extends Tuple, I1 extends Tuple> Flow<O1, I1> singleWaiting(TF<O1, I1> function) {
        return new Flow<>(Collections.singletonList(Step.await(function)));
    }

    public static <O1 extends Tuple, I1 extends Tuple> Flow<O1, I1> singleWaiting(TF<O1, I1> function, EH<O1> errorHandler) {
        return new Flow<>(Collections.singletonList(Step.await(function, errorHandler)));
    }

    public static <O1 extends Tuple, I1 extends Tuple> Flow<O1, I1> singleAsync(TF<O1, I1> function) {
        return new Flow<>(Collections.singletonList(Step.async(function)));
    }

    public static <O1 extends Tuple, I1 extends Tuple> Flow<O1, I1> singleAsync(TF<O1, I1> function, EH<O1> errorHandler) {
        return new Flow<>(Collections.singletonList(Step.async(function, errorHandler)));
    }
}
