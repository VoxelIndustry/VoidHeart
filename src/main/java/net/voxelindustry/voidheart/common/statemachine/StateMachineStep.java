package net.voxelindustry.voidheart.common.statemachine;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@Getter
public abstract class StateMachineStep<MACHINE extends StateMachine<MACHINE, P>, P>
{
    private final String      name;
    private final MACHINE     machine;
    @Nullable
    private final Consumer<P> tickFunction;

    protected StateMachineStep(String name, MACHINE machine, @Nullable Consumer<P> tickFunction)
    {
        this.name = name;
        this.machine = machine;
        this.tickFunction = tickFunction;
    }

    public abstract int getDuration();

    @Getter
    public static class StateMachineFixedStep<MACHINE extends StateMachine<MACHINE, P>, P> extends StateMachineStep<MACHINE, P>
    {
        private final int duration;

        protected StateMachineFixedStep(String name,MACHINE machine, @Nullable Consumer<P> tickFunction, int duration)
        {
            super(name, machine,tickFunction);

            this.duration = duration;
        }
    }

    public static class StateMachineParameterizedStep<MACHINE extends StateMachine<MACHINE, P>, P> extends StateMachineStep<MACHINE, P>
    {
        private final Object2IntFunction<P> durationFunction;

        protected StateMachineParameterizedStep(String name, MACHINE machine, @Nullable Consumer<P> tickFunction, Object2IntFunction<P> durationFunction)
        {
            super(name, machine, tickFunction);
            this.durationFunction = durationFunction;
        }

        @Override
        public int getDuration()
        {
            return durationFunction.applyAsInt(getMachine().getCurrentParameter());
        }
    }
}
