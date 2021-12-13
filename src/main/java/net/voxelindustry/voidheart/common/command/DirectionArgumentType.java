package net.voxelindustry.voidheart.common.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.command.CommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.Direction;
import net.voxelindustry.voidheart.VoidHeart;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DirectionArgumentType implements ArgumentType<Direction>
{
    public static final SimpleCommandExceptionType INVALID_DIRECTION_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText(VoidHeart.MODID + ".argument.direction.unknown"));

    private static final List<String> DIRECTIONS = Arrays.stream(Direction.values()).map(Enum::name).collect(toList());

    public static DirectionArgumentType direction()
    {
        return new DirectionArgumentType();
    }

    @Override
    public Direction parse(StringReader reader) throws CommandSyntaxException
    {
        var candidate = Direction.byName(reader.readString());
        if (candidate == null)
            throw INVALID_DIRECTION_EXCEPTION.create();
        return candidate;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder)
    {
        return CommandSource.suggestMatching(DIRECTIONS, builder);
    }

    @Override
    public Collection<String> getExamples()
    {
        return DIRECTIONS;
    }
}
