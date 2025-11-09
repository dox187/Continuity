package me.pepperbell.continuity.impl.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import me.pepperbell.continuity.api.client.ProcessingDataKey;
import net.minecraft.util.Identifier;

/**
 * Immutable record storing processing data key metadata. Converted to record in Java 21 for
 * improved readability and automatic equals/hashCode/toString. Explicitly implements interface
 * methods since record accessors use component names, not get* pattern.
 */
public record ProcessingDataKeyImpl<T>(Identifier id, int rawId, Supplier<T> valueSupplier,
		@Nullable Consumer<T> valueResetAction) implements ProcessingDataKey<T> {

	@Override
	public Identifier getId() {
		return id();
	}

	@Override
	public int getRawId() {
		return rawId();
	}

	@Override
	public Supplier<T> getValueSupplier() {
		return valueSupplier();
	}

	@Override
	public Consumer<T> getValueResetAction() {
		return valueResetAction();
	}
}
