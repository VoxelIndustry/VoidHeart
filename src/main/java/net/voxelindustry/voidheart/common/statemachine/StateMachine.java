package net.voxelindustry.voidheart.common.statemachine;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.function.Consumer;

public class StateMachine<THIS extends StateMachine<THIS, P>, P>
{
    @Getter
    @Setter
    private P currentParameter;

    public StateMachineStep<THIS, P> step(String stepName)
    {
        return new StateMachineStep<>(this, stepName);
    }

    public static final class StateMachineStep<THIS extends StateMachine<THIS, P>, P>
    {
        private final StateMachine<THIS, P> machine;

        private final String stepName;

        private Object2IntFunction<P> durationFunction;

        private Consumer<P> tickFunction;

        public StateMachineStep(
                StateMachine<THIS, P> machine,
                String stepName
        )
        {
            this.machine = machine;
            this.stepName = stepName;
        }

/*        public StateMachineStep<THIS, P> everyTick(Consumer<StateMachine<THIS, P>> tickFunction)
        {
            this.tickFunction = tickFunction;
            return this;
        }

        public StateMachineStepCompletion<THIS, P> fixedDuration(int duration)
        {
            durationFunction = unused -> duration;
            return new StateMachineStepCompletion<>(machine, this, stepName);
        }

        public StateMachineStepCompletion<THIS, P> parameterizedDuration(Object2IntFunction<P> durationFunction)
        {
            this.durationFunction = durationFunction;
            return new StateMachineStepCompletion<>(machine, this, stepName);
        }

        public StateMachineStepCompletion<THIS, P> flexibleDuration(Object2BooleanFunction<P> isCompleteFunction)
        {
            return new StateMachineStepCompletion<>(machine, this, stepName);
        }

        public StateMachine<T> machine()
        {
            return machine;
        }*/

        public String stepName()
        {
            return stepName;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (StateMachineStep) obj;
            return Objects.equals(this.machine, that.machine) &&
                    Objects.equals(this.stepName, that.stepName);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(machine, stepName);
        }

        @Override
        public String toString()
        {
            return "StateMachineStep[" +
                    "machine=" + machine + ", " +
                    "stepName=" + stepName + ']';
        }
    }
}
