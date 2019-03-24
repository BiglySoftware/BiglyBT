package com.biglybt.core.internat;

/**
 * Provides a String.
 *
 * Implementation note:
 *
 * When BiglyBT-android project is at least minimum API level 24
 * then this can be replaced with {@code java.util.function.Supplier<String>}.
 */
public interface StringSupplier
{
	String get();
}
