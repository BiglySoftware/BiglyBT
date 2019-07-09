/*
 * Created on 7 Jun 2007
 * Created by Allan Crooks
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.core.peermanager.utils;

/**
 * @author Allan Crooks
 */
class BTPeerIDByteDecoderUtils {

	public static String decodeMnemonic(char c) {
		switch (c) {
			case 'b':
			case 'B':
				return "Beta";
			case 'X':
			case 'x':
			case 'Z':
				return "(Dev)"; // Just for Transmission at the moment.
		}
		return null;
	}

	public static String decodeNumericValueOfByte(byte b) {
		return String.valueOf((int)b & 0xFF);
	}

	public static String decodeNumericValueOfByte(byte b, int min_digits) {
		String result = decodeNumericValueOfByte(b);
		while (result.length() < min_digits) {result = "0" + result;}
		return result;
	}

	public static String decodeNumericChar(char c) {
		String result = decodeAlphaNumericChar(c);
		if (result == null || result.length() == 1) {return result;}
		return null;
	}

	public static String intchar(char c) {
		String result = decodeNumericChar(c);
		if (result == null) {throw new IllegalArgumentException("not an integer character: " + c);}
		return result;
	}

	public static String decodeAlphaNumericChar(char c) {
		if ('0' <= c && c <= '9') {
			return String.valueOf(c);
		}
		else if ('A' <= c && c <= 'Z') {
			return String.valueOf(10 + (c - 'A'));
		}
		else if ('a' <= c && c <= 'z') {
			return String.valueOf(36 + (c - 'A'));
		}
		else if (c == '.') {
			return "62";
		}
		return null;
	}

	public static boolean isAzStyle(String peer_id) {
		if (peer_id.charAt(0) != '-') {return false;}
		if (peer_id.charAt(7) == '-') {return true;}

		/**
		 * Hack for FlashGet - it doesn't use the trailing dash.
		 * Also, LH-ABC has strayed into "forgetting about the delimiter" territory.
		 *
		 * In fact, the code to generate a peer ID for LH-ABC is based on BitTornado's,
		 * yet tries to give an Az style peer ID... oh dear.
		 *
		 * BT Next Evolution seems to be in the same boat as well.
		 *
		 * KTorrent 3 appears to use a dash rather than a final character.
		 */
		if (peer_id.substring(1, 3).equals("FG")) {return true;}
		if (peer_id.substring(1, 3).equals("LH")) {return true;}
		if (peer_id.substring(1, 3).equals("NE")) {return true;}
		if (peer_id.substring(1, 3).equals("KT")) {return true;}
		if (peer_id.substring(1, 3).equals("SP")) {return true;}
		return false;
	}

	/**
	 * Checking whether a peer ID is Shadow style or not is a bit tricky.
	 *
	 * The BitTornado peer ID convention code is explained here:
	 *   http://forums.degreez.net/viewtopic.php?t=7070
	 *
	 * The main thing we are interested in is the first six characters.
	 * Although the other characters are base64 characters, there's no
	 * guarantee that other clients which follow that style will follow
	 * that convention (though the fact that some of these clients use
	 * BitTornado in the core does blur the lines a bit between what is
	 * "style" and what is just common across clients).
	 *
	 * So if we base it on the version number information, there's another
	 * problem - there isn't the use of absolute delimiters (no fixed dash
	 * character, for example).
	 *
	 * There are various things we can do to determine how likely the peer
	 * ID is to be of that style, but for now, I'll keep it to a relatively
	 * simple check.
	 *
	 * We'll assume that no client uses the fifth version digit, so we'll
	 * expect a dash. We'll also assume that no client has reached version 10
	 * yet, so we expect the first two characters to be "letter,digit".
	 *
	 * We've seen some clients which don't appear to contain any version
	 * information, so we need to allow for that.
	 */
	public static boolean isShadowStyle(String peer_id) {
		if (peer_id.charAt(5) != '-') {return false;}
		if (!Character.isLetter(peer_id.charAt(0))) {return false;}
		if (!(Character.isDigit(peer_id.charAt(1)) || peer_id.charAt(1) == '-')) {return false;}

		// Find where the version number string ends.
		int last_ver_num_index = 4;
		for (; last_ver_num_index>0; last_ver_num_index--) {
			if (peer_id.charAt(last_ver_num_index) != '-') {break;}
		}

		// For each digit in the version string, check it is a valid version identifier.
		char c;
		for (int i=1; i<=last_ver_num_index; i++) {
			c = peer_id.charAt(i);
			if (c == '-') {return false;}
			if (decodeAlphaNumericChar(c) == null) {return false;}
		}

		return true;
	}

	public static boolean isMainlineStyle(String peer_id) {
		/**
		 * One of the following styles will be used:
		 *   Mx-y-z--
		 *   Mx-yy-z-
		 */
		return peer_id.charAt(2) == '-' && peer_id.charAt(7) == '-' && (
				peer_id.charAt(4) == '-' || peer_id.charAt(5) == '-');
	}

	public static boolean isPossibleSpoofClient(String peer_id) {
		return peer_id.endsWith("UDP0") || peer_id.endsWith("HTTPBT");
	}

	public static String getMainlineStyleVersionNumber(String peer_id) {
		boolean two_digit_in_middle = peer_id.charAt(5) == '-';
		String middle_part = decodeNumericChar(peer_id.charAt(3));
		if (two_digit_in_middle) {
			middle_part = join(middle_part, decodeNumericChar(peer_id.charAt(4)));
		}
		return joinAsDotted(
			decodeNumericChar(peer_id.charAt(1)), middle_part,
			decodeNumericChar(peer_id.charAt(two_digit_in_middle ? 6 : 5))
		);
	}

	public static String getShadowStyleVersionNumber(String peer_id) {
		String ver_number = decodeAlphaNumericChar(peer_id.charAt(1));
		if (ver_number == null) {return null;}
		for (int i=2; i<6 && ver_number != null; i++) {
			char c = peer_id.charAt(i);
			if (c == '-') {break;}
			ver_number = joinAsDotted(ver_number, decodeAlphaNumericChar(peer_id.charAt(i)));
			if ( ver_number == null ){
				return( null );
			}
		}
		// We'll strip off trailing redundant zeroes.
		while (ver_number.endsWith(".0")) {ver_number = ver_number.substring(0, ver_number.length()-2);}
		return ver_number;
	}

	public static String decodeAzStyleVersionNumber(String version_data, String version_scheme) {
		char a = version_data.charAt(0);
		char b = version_data.charAt(1);
		char c = version_data.charAt(2);
		char d = version_data.charAt(3);

		/**
		 * Specialness for Transmission - thanks to BentMyWookie for the information,
		 * I'm sure he'll be reading this comment anyway... ;)
		 */
		if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_TRANSMISSION_STYLE) {
			// Very old client style: -TR0006- is 0.6.
			if (version_data.startsWith("000")) {
				version_scheme = "3.4";
			}

			// Previous client style: -TR0072- is 0.72.
			else if (version_data.startsWith("00")) {
				version_scheme = BTPeerIDByteDecoderDefinitions.VER_AZ_SKIP_FIRST_ONE_MAJ_TWO_MIN;
			}

			// Current client style: -TR072Z- is 0.72 (Dev).
			else {
				version_scheme = BTPeerIDByteDecoderDefinitions.VER_AZ_ONE_MAJ_TWO_MIN_PLUS_MNEMONIC;
			}
		}

		if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_FOUR_DIGITS) {
			return decodeAlphaNumericChar(a) + "." + decodeAlphaNumericChar(b) + "."
					+ decodeAlphaNumericChar(c) + "." + decodeAlphaNumericChar(d);
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_THREE_DIGITS ||
				version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_THREE_DIGITS_PLUS_MNEMONIC ||
				version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_ONE_MAJ_TWO_MIN_PLUS_MNEMONIC) {

			String result;
			if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_ONE_MAJ_TWO_MIN_PLUS_MNEMONIC) {
				result = intchar(a) + "." + intchar(b) + intchar(c);
			}
			else {
				result = intchar(a) + "." + intchar(b) + "." + intchar(c);
			}
			if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_THREE_DIGITS_PLUS_MNEMONIC ||
					version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_ONE_MAJ_TWO_MIN_PLUS_MNEMONIC) {
				String mnemonic = decodeMnemonic(d);
				if (mnemonic != null) {result += " " + mnemonic;}
			}
			return result;
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_TWO_MAJ_TWO_MIN) {
			return (a == '0' ? "" : intchar(a)) + intchar(b) + "." + intchar(c) + intchar(d);
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_LAST_THREE_DIGITS) {
			return intchar(b) + "." + intchar(c) + "." + intchar(d);
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_THREE_ALPHANUMERIC_DIGITS) {
			return decodeAlphaNumericChar(a) + "." + decodeAlphaNumericChar(b) + "." + decodeAlphaNumericChar(c);
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_SKIP_FIRST_ONE_MAJ_TWO_MIN) {
			
			return intchar(b) + "." + intchar(c) + intchar(d);
			
		}else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_ONE_MAJ_TWO_MIN_ONE_TRAIL) {
			
				return intchar(a) + "." + intchar(b) +intchar(c) + "." + intchar(d);
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_KTORRENT_STYLE) {
			// Either something like this:
			//   1.2 RC 4 [where 3 == 'R')
			//   1.2 Dev  [where 3 == 'D')
			//   1.2      [where 3 doesn't equal the above]
			switch (c) {
				case 'R':
					return intchar(a) + "." + intchar(b) + " RC" + intchar(d);
				case 'D':
					return intchar(a) + "." + intchar(b) + " Dev";
				default:
					return intchar(a) + "." + intchar(b);
			}
		}
		else if (version_scheme.equals("1.234")) {
			return intchar(a) + "." + intchar(b) + intchar(c) + intchar(d);
		}
		else if (version_scheme.equals("1.2(34)")) {
			return intchar(a) + "." + intchar(b) + "(" + intchar(c) + intchar(d) + ")";
		}
		else if (version_scheme.equals("1.2.34")) {
			return intchar(a) + "." + intchar(b) + "." + intchar(c) + intchar(d);
		}
		else if (version_scheme.equals("v1234")) {
			return "v" + intchar(a) + intchar(b) + intchar(c) + intchar(d);
		}
		else if (version_scheme.equals("1.2")) {
			return intchar(a) + "." + intchar(b);
		}
		else if (version_scheme.equals("3.4")) {
			return intchar(c) + "." + intchar(d);
		}
		else if (version_scheme.equals("12.3-4")) {
			return decodeAlphaNumericChar(a) + decodeAlphaNumericChar(b) + "." + decodeAlphaNumericChar(c) + "-" + decodeAlphaNumericChar(d);
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_AZ_V_FOUR_DIGITS) {
			return "v" + decodeAzStyleVersionNumber(version_data, BTPeerIDByteDecoderDefinitions.VER_AZ_FOUR_DIGITS);
		}
		else {
			throw new RuntimeException("unknown AZ style version number scheme - " + version_scheme);
		}
	}

	public static String getTwoByteThreePartVersion(byte b1, byte b2) {
		String min_part = decodeNumericValueOfByte(b2, 2);
		return joinAsDotted(decodeNumericValueOfByte(b1), min_part.substring(0, 1), min_part.substring(1, 2));
	}

	/**
	 * Look at the peer ID and just grab as many readable characters to form the version
	 * substring as possible.
	 */
	public static String extractReadableVersionSubstringFromPeerID(String peer_id) {
		for (int i=0; i<peer_id.length(); i++) {
			char c = peer_id.charAt(i);

			// This is based on All Peers peer ID at the time of writing, e.g:
			//   AP0.70rc30->>...
			if (Character.isLetter(c)) continue;
			if (Character.isDigit(c)) continue;
			if (c == '.') continue;
			// Must be delimiter character.
			return peer_id.substring(0, i);
		}
		return peer_id;
	}

	public static String decodeCustomVersionNumber(String version_data, String version_scheme) {
		if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_BLOCK) {
			return version_data;
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_DOTTED_BLOCK ||
				version_scheme == BTPeerIDByteDecoderDefinitions.VER_BYTE_BLOCK_DOTTED_CHAR) {
			int inc_size = (version_scheme == BTPeerIDByteDecoderDefinitions.VER_DOTTED_BLOCK) ? 2 : 1;
			String result = version_data.substring(0, 1);
			for (int i=0+inc_size; i<version_data.length(); i+=inc_size) {
				result = joinAsDotted(result, String.valueOf(version_data.charAt(i)));
			}
			return result;
		}
		else if ( version_scheme == BTPeerIDByteDecoderDefinitions.VER_BYTE_BLOCK_DOTTED_NUM) {
			
			String result = "";
			for (int i=0; i<version_data.length(); i++) {
				char c = version_data.charAt(i);
				String str;
				if ( c >= (int)'a' ){
					str = String.valueOf( c - (int)'a' + 10 );
				}else{
					str = String.valueOf((char)c);
				}
				result = result.isEmpty()?str:joinAsDotted(result, str );
			}
			return result;
		}
		else if ( version_scheme == BTPeerIDByteDecoderDefinitions.VER_TWOBYTE_BLOCK_DOTTED_CHAR) {
			String result = "";
			for (int i=0;i<version_data.length();i+=2 ){
				String s = version_data.substring( i, i+2 );
				if ( i == 0 ){
					if ( s.charAt(0) == '0' ){
						s = s.substring(1);
					}
					result = s;
				}else{
					result = joinAsDotted(result, s );
				}
			}
			return result;
		}
		else if (version_scheme == BTPeerIDByteDecoderDefinitions.VER_BITS_ON_WHEELS) {
			if (version_data.equals("A0C")) {return "1.0.6";}
			else if (version_data.equals("A0B")) {return "1.0.5";}
			throw new RuntimeException("Unknown BitsOnWheels version number - " + version_data);
		}
		else if ( version_scheme == BTPeerIDByteDecoderDefinitions.VER_BYTE_UM_STYLE ){

			char[] chars = version_data.toCharArray();

			return( chars[0] + "." + chars[1] + "." + chars[2] + chars[3] );
		}else if ( version_scheme == BTPeerIDByteDecoderDefinitions.VER_BITLORD ){

			char[] chars = version_data.toCharArray();

			return( chars[0] + "." + chars[1] + "." + chars[2] + "-" + chars[3] + chars[4] + chars[5] );
		}else if ( version_scheme == BTPeerIDByteDecoderDefinitions.VER_TWO_BYTE_MAINLINE ){
			
			char[] chars = version_data.toCharArray();

			return( chars[0] + "." + chars[2] + chars[3] + "." + chars[5] );
		}
		else {
			throw new RuntimeException("unknown custom version number scheme - " + version_scheme);
		}
	}

	private static String join(String a, String b) {
		if (a == null) {return null;}
		if (b == null) {return null;}
		return a + b;
	}

	private static String joinAsDotted(String a, String b) {
		if (a == null) {return null;}
		if (b == null) {return null;}
		return a + "." + b;
	}


	private static String joinAsDotted(String a, String b, String c) {
		if (a == null) {return null;}
		if (b == null) {return null;}
		if (c == null) {return null;}
		return a + "." + b + "." + c;
	}



}
