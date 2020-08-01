package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@RequiredArgsConstructor
public class DeferredRollbackWork<T>
{
    private final boolean maySucceed;
    private       boolean success;

    @Getter
    private final boolean canRollback;

    @Getter
    private final T state;

    private final Function<T, Boolean> deferredWorker;
    private final Runnable             rollbackWorker;

    public boolean maySucceed()
    {
        return maySucceed;
    }

    public boolean success()
    {
        return success;
    }

    public void execute()
    {
        if (deferredWorker != null)
            success = deferredWorker.apply(state);
    }

    public void rollback()
    {
        if (rollbackWorker != null)
            rollbackWorker.run();
    }

    public static <T> DeferredRollbackWork<T> willFail()
    {
        return new DeferredRollbackWork<>(false, false, null, null, null);
    }

    public static <T> DeferredRollbackWork<T> maySucceed(boolean canRollback, T state, Function<T, Boolean> deferredWorker, Runnable rollbackWorker)
    {
        return new DeferredRollbackWork<>(true, canRollback, state, deferredWorker, rollbackWorker);
    }
}
