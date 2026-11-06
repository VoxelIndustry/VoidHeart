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
    private final T state;

    private final Function<T, Boolean> deferredWorker;

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

    public static <T> DeferredRollbackWork<T> willFail()
    {
        return new DeferredRollbackWork<>(false, null, null);
    }

    public static <T> DeferredRollbackWork<T> maySucceed(T state, Function<T, Boolean> deferredWorker)
    {
        return new DeferredRollbackWork<>(true, state, deferredWorker);
    }
}
