package com.biglybt.core.util;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class Base32Test
{

	@Test
	public void encodeInvalidCharsThrowsNoException() {
		assertThatCode(() -> Base32.encode(new byte[]{(byte) 0xc3, (byte)0x28}))
				.describedAs("Invalid 2 Octet Sequence: \\xc3\\x28")
				.doesNotThrowAnyException();
	}

	@Test
	public void decodeInvalidCharsThrowsNoException() {
		assertThatCode(() -> Base32.decode(new String(new byte[]{(byte)0xc3, (byte)0x28}, StandardCharsets.UTF_8)))
				.describedAs("Invalid 2 Octet Sequence: \\xc3\\x28")
				.doesNotThrowAnyException();
	}



	@Test
	public void encodeControlCharsThrowsNoException() {
		assertThatCode(() -> Base32.encode(new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0x02}))
				.describedAs("Control chars: \\x00\\x01\\x02")
				.doesNotThrowAnyException();
	}

}
