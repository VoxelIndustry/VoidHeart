package net.voxelindustry.voidheart.common.ley.flow;

/**
 * Startup registrar for W3 Ley flow and taint, called once by EnergySetup. Install the real flow here with {@code Ley.install}.
 * Until then {@code DefaultLeyFlow} answers.
 */
public final class LeyFlowContent
{
    private LeyFlowContent()
    {
    }

    public static void register()
    {
    }
}
