package net.voxelindustry.voidheart.common.content.essence;

public interface IEssenceStorage
{
    int fill(EssenceType type, int amount);

    int drain(EssenceType type, int amount);
}
