/*
 * Created on Nov 12, 2003
 * Created by Alon Rohter
 * Copyright (C) 2003-2004 Alon Rohter, All Rights Reserved.
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
 *
 */
package com.biglybt.core.peermanager.utils;

import java.io.IOException;
import java.util.HashSet;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;


/**
 * Used for identifying clients by their peerID.
 */
public class BTPeerIDByteDecoder {

	final static boolean LOG_UNKNOWN;

	static {
		String	prop = System.getProperty("log.unknown.peerids");
		LOG_UNKNOWN = prop == null || prop.equals("1");
	}

	private static String logUnknownClient0(byte[] peer_id_bytes) throws IOException {
		String text = new String(peer_id_bytes, 0, 20, Constants.BYTE_ENCODING_CHARSET);
		text = text.replace((char)12, (char)32);
		text = text.replace((char)10, (char)32);

		return "[" + text + "] " + ByteFormatter.encodeString(peer_id_bytes) + " ";
	}

	private static String asUTF8ByteString(String text) {
		byte[] utf_bytes = text.getBytes(Constants.DEFAULT_ENCODING_CHARSET);
		return ByteFormatter.encodeString(utf_bytes);
	}

	private static final HashSet logged_discrepancies = new HashSet();
	public static void logClientDiscrepancy(String peer_id_name, String handshake_name, String discrepancy, String protocol, byte[] peer_id) {
		if (!client_logging_allowed) {return;}

		// Generate the string used that we will log.
		String line_to_log = discrepancy + " [" + protocol + "]: ";
		line_to_log += "\"" + peer_id_name + "\" / \"" + handshake_name + "\" ";

		// We'll encode the name in byte form to help us decode it.
		line_to_log += "[" + asUTF8ByteString(handshake_name) + "]";

		// Avoid logging the same combination of things again.
		boolean log_to_debug_out = Constants.isCVSVersion();
		if (log_to_debug_out || LOG_UNKNOWN) {
			// If this text has been recorded before, then avoid doing it again.
			if (!logged_discrepancies.add(line_to_log)) {return;}
		}

		// Add peer ID bytes.
		if (peer_id != null) {
			line_to_log += ", Peer ID: " + ByteFormatter.encodeString(peer_id);
		}

		// Enable this block for now - just until we get more feedback about
		// problematic clients.
		if (log_to_debug_out) {
			Debug.outNoStack("Conflicting peer identification: " + line_to_log);
		}

		if (!LOG_UNKNOWN) {return;}
		logClientDiscrepancyToFile(line_to_log);
	}

	private static AEDiagnosticsLogger logger = null;
	private synchronized static void logClientDiscrepancyToFile(String line_to_log) {
		if (logger == null) {logger = AEDiagnostics.getLogger("clientid");}
		try {logger.log(line_to_log);}
		catch (Throwable e) {Debug.printStackTrace(e);}
	}

	static boolean client_logging_allowed = true;

	// I don't expect this to grow too big, and it won't grow if there's no logging going on.
	private static final HashSet logged_ids = new HashSet();
	
	static void logUnknownClient(byte[] peer_id_bytes, boolean to_debug_out) {

		if (!client_logging_allowed) {return;}

		// Avoid logging the same client ID multiple times.
		boolean log_to_debug_out = to_debug_out && Constants.isCVSVersion();
		if (log_to_debug_out || LOG_UNKNOWN) {
			// If the ID has been recorded before, then avoid doing it again.
			if (!logged_ids.add(makePeerIDReadableAndUsable(peer_id_bytes))) {return;}
		}

		// Enable this block for now - just until we get more feedback about
		// unknown clients.
		if (log_to_debug_out) {
			Debug.outNoStack("Unable to decode peer correctly - peer ID bytes: " + makePeerIDReadableAndUsable(peer_id_bytes));
		}

		if (!LOG_UNKNOWN) {return;}
		try {logClientDiscrepancyToFile(logUnknownClient0(peer_id_bytes));}
		catch (Throwable t) {Debug.printStackTrace(t);}

	}

	static void logUnknownClient(String peer_id, String net ) {
		logUnknownClient(peer_id.getBytes(Constants.BYTE_ENCODING_CHARSET), net == AENetworkClassifier.AT_PUBLIC);
	}

	public static String decode0(byte[] peer_id_bytes, String net) {
		String peer_id = new String(peer_id_bytes, Constants.BYTE_ENCODING_CHARSET);

		// We store the result here.
		String client = null;

		/**
		 * If the client reuses parts of the peer ID of other peers, then try to determine this
		 * first (before we misidentify the client).
		 */
		if (BTPeerIDByteDecoderUtils.isPossibleSpoofClient(peer_id)) {
			client = decodeBitSpiritClient(peer_id, peer_id_bytes);
			if (client != null) {return client;}
			client = decodeBitCometClient(peer_id, peer_id_bytes);
			if (client != null) {return client;}
			String BAD_PEER_ID = MessageText.getString("PeerSocket.bad_peer_id");
			return "BitSpirit? (" + BAD_PEER_ID + ")";
		}

		/**
		 * See if the client uses Az style identification.
		 */
		if (BTPeerIDByteDecoderUtils.isAzStyle(peer_id)) {
			client = BTPeerIDByteDecoderDefinitions.getAzStyleClientName(peer_id);
			if (client != null) {
				String client_with_version = BTPeerIDByteDecoderDefinitions.getAzStyleClientVersion(client, peer_id, net);

				/**
				 * Hack for fake ZipTorrent clients - there seems to be some clients
				 * which use the same identifier, but they aren't valid ZipTorrent clients.
				 */
				if (client.startsWith("ZipTorrent") && peer_id.startsWith("bLAde", 8)) {
					String client_name = (client_with_version == null) ? client : client_with_version;
					String UNKNOWN = MessageText.getString("PeerSocket.unknown");
					String FAKE = MessageText.getString("PeerSocket.fake_client");
					return UNKNOWN + " [" + FAKE  + ": " + client_name + "]";
				}

				/**
				 * BitTorrent 6.0 Beta currently misidentifies itself.
				 */
				if ("\u00B5Torrent 6.0.0 Beta".equals(client_with_version)) {
					return "Mainline 6.0 Beta";
				}

				/**
				 * If it's the rakshasa libtorrent, then it's probably rTorrent.
				 */
				if (client.startsWith("libTorrent (Rakshasa)")) {
					String client_name = (client_with_version == null) ? client : client_with_version;
					return client_name + " / rTorrent*";
				}

				if (client_with_version != null) {return client_with_version;}

				return client;
			}

		}

		/**
		 * See if the client uses Shadow style identification.
		 */
		if (BTPeerIDByteDecoderUtils.isShadowStyle(peer_id)) {
			client = BTPeerIDByteDecoderDefinitions.getShadowStyleClientName(peer_id);
			if (client != null) {
				String client_ver = BTPeerIDByteDecoderUtils.getShadowStyleVersionNumber(peer_id);
				if (client_ver != null) {return client + " " + client_ver;}
				return client;
			}
		}

		/**
		 * See if the client uses Mainline style identification.
		 */
		client = BTPeerIDByteDecoderDefinitions.getMainlineStyleClientName(peer_id);
		if (client != null) {
			/**
			 * We haven't got a good way of detecting whether this is a Mainline style
			 * version of peer ID until we start decoding peer ID information. So for
			 * that reason, we wait until we get client version information here - if
			 * we don't manage to determine a version number, then we assume that it
			 * has been misidentified and carry on with it.
			 */
			String client_ver = BTPeerIDByteDecoderUtils.getMainlineStyleVersionNumber(peer_id);

			if (client_ver != null) {
				String result = client + " " + client_ver;
				return result;
			}
		}

		/**
		 * Check for BitSpirit / BitComet (non possible spoof client mode).
		 */
		client = decodeBitSpiritClient(peer_id, peer_id_bytes);
		if (client != null) {return client;}
		client = decodeBitCometClient(peer_id, peer_id_bytes);
		if (client != null) {return client;}


		/**
		 * See if the client identifies itself using a particular substring.
		 */
		BTPeerIDByteDecoderDefinitions.ClientData client_data = BTPeerIDByteDecoderDefinitions.getSubstringStyleClient(peer_id);
		if (client_data != null) {
			client = client_data.client_name;
			String client_with_version = BTPeerIDByteDecoderDefinitions.getSubstringStyleClientVersion(client_data, peer_id, peer_id_bytes, net);
			if (client_with_version != null) {return client_with_version;}
			return client;
		}

		// Specific version of BitTorrent used "-M" with all random
		if (peer_id_bytes[0] == '-' && peer_id_bytes[1] == 'M') {
			return "BitTorrent 7.8.2";
		}

		client = identifyAwkwardClient(peer_id_bytes);
		if (client != null) {return client;}
		return null;
	}

	/**
	 * Decodes the given peerID, returning an identification string.
	 */
	public static String
	decode(byte[] peer_id, String net )
	{
		if ( peer_id.length > 0 ){

			try {
				String client = decode0(peer_id, net);

				if (client != null ){

					return client;
				}
			}catch (Throwable e) {

				Debug.out( "Failed to decode peer id " + ByteFormatter.encodeString(peer_id) + ": " + Debug.getNestedExceptionMessageAndStack( e ));
			}

			try {
				String peer_id_as_string = new String(peer_id, Constants.BYTE_ENCODING_CHARSET);

				boolean is_az_style = BTPeerIDByteDecoderUtils.isAzStyle(peer_id_as_string);

				boolean is_shadow_style = BTPeerIDByteDecoderUtils.isShadowStyle(peer_id_as_string);

				logUnknownClient(peer_id, !(net != AENetworkClassifier.AT_PUBLIC || is_az_style || is_shadow_style));

				if (is_az_style) {
					return BTPeerIDByteDecoderDefinitions.formatUnknownAzStyleClient(peer_id_as_string);
				}
				else if (is_shadow_style) {
					return BTPeerIDByteDecoderDefinitions.formatUnknownShadowStyleClient(peer_id_as_string);
				}
			}catch( Throwable e ){

				Debug.out( "Failed to decode peer id " + ByteFormatter.encodeString(peer_id) + ": " + Debug.getNestedExceptionMessageAndStack( e ));
			}
		}

		String sPeerID = getPrintablePeerID(peer_id);
		return MessageText.getString("PeerSocket.unknown") + " [" + sPeerID + "]";
	}

	public static String identifyAwkwardClient(byte[] peer_id) {

		int iFirstNonZeroPos = 0;

		iFirstNonZeroPos = 20;
		for( int i=0; i < 20; i++ ) {
			if( peer_id[i] != (byte)0 ) {
				iFirstNonZeroPos = i;
				break;
			}
		}

		//Shareaza check
		if( iFirstNonZeroPos == 0 ) {
			boolean bShareaza = true;
			for( int i=0; i < 16; i++ ) {
				if( peer_id[i] == (byte)0 ) {
					bShareaza = false;
					break;
				}
			}
			if( bShareaza ) {
				for( int i=16; i < 20; i++ ) {
					if( peer_id[i] != ( peer_id[i % 16] ^ peer_id[15 - (i % 16)] ) ) {
						bShareaza = false;
						break;
					}
				}
				if( bShareaza )  return "Shareaza";
			}
		}

		byte three = (byte)3;
		if ((iFirstNonZeroPos == 9)
				&& (peer_id[9] == three)
				&& (peer_id[10] == three)
				&& (peer_id[11] == three)) {
			return "I2PSnark";
		}

		if ((iFirstNonZeroPos == 12) && (peer_id[12] == (byte)97) && (peer_id[13] == (byte)97)) {
			return "Experimental 3.2.1b2";
		}
		if ((iFirstNonZeroPos == 12) && (peer_id[12] == (byte)0) && (peer_id[13] == (byte)0)) {
			return "Experimental 3.1";
		}
		if (iFirstNonZeroPos == 12) return "Mainline";

		return null;

	}

	private static String decodeBitSpiritClient(String peer_id, byte[] peer_id_bytes) {
		if (!peer_id.substring(2, 4).equals("BS")) {return null;}
		String version = BTPeerIDByteDecoderUtils.decodeNumericValueOfByte(peer_id_bytes[1]);
		if ("0".equals(version)) {version = "1";}
		return "BitSpirit v" + version;
	}

	private static String decodeBitCometClient(String peer_id, byte[] peer_id_bytes) {
		String mod_name = null;
		if (peer_id.startsWith("exbc")) {mod_name = "";}
		else if (peer_id.startsWith("FUTB")) {mod_name  = "(Solidox Mod) ";}
		else if (peer_id.startsWith("xUTB")) {mod_name  = "(Mod 2) ";}
		else {return null;}

		boolean is_bitlord = (peer_id.substring(6, 10).equals("LORD"));

		/**
		 * Older versions of BitLord are of the form x.yy, whereas new versions (1 and onwards),
		 * are of the form x.y. BitComet is of the form x.yy.
		 */
		String client_name = (is_bitlord) ? "BitLord " : "BitComet ";
		String maj_version = BTPeerIDByteDecoderUtils.decodeNumericValueOfByte(peer_id_bytes[4]);
		int min_version_length = (is_bitlord && !maj_version.equals("0")) ? 1 : 2;

		return client_name + mod_name + maj_version + "." +
		BTPeerIDByteDecoderUtils.decodeNumericValueOfByte(peer_id_bytes[5], min_version_length);
	}


	protected static String getPrintablePeerID(byte[] peer_id) {
		return getPrintablePeerID(peer_id, '-');
	}

	protected static String getPrintablePeerID(byte[] peer_id, char fallback_char) {
		byte[] peerID = new byte[ peer_id.length ];
		System.arraycopy( peer_id, 0, peerID, 0, peer_id.length );

		for (int i = 0; i < peerID.length; i++) {
			int b = (0xFF & peerID[i]);
			if (b < 32 || b > 127)
				peerID[i] = (byte) fallback_char;
		}
		String sPeerID = new String(peerID, Constants.BYTE_ENCODING_CHARSET);

		return sPeerID;
	}

	private static String makePeerIDReadableAndUsable(byte[] peer_id) {
		boolean as_ascii = true;
		for (int i = 0; i < peer_id.length; i++) {
			int b = 0xFF & peer_id[i];
			if (b < 32 || b > 127 || b == 10 || b == 9 || b == 13) {
				as_ascii = false;
				break;
			}
		}
		if (as_ascii) {
			return new String(peer_id, Constants.BYTE_ENCODING_CHARSET);
		} else {
			return ByteFormatter.encodeString(peer_id);
		}
	}

	static byte[] peerIDStringToBytes(String peer_id) throws Exception {
		if (peer_id.length() > 40) {
			peer_id = peer_id.replaceAll("[ ]", "");
		}

		byte[] byte_peer_id = null;
		if (peer_id.length() == 40) {
			byte_peer_id = ByteFormatter.decodeString(peer_id);
			String readable_peer_id = makePeerIDReadableAndUsable(byte_peer_id);
			if (!peer_id.equals(readable_peer_id)) {
				throw new RuntimeException("Use alternative format for peer ID - from " + peer_id + " to " + readable_peer_id);
			}
		}
		else if (peer_id.length() == 20) {
			byte_peer_id = peer_id.getBytes(Constants.BYTE_ENCODING_CHARSET);
		}
		else {
			throw new IllegalArgumentException(peer_id);
		}
		return byte_peer_id;
	}

	private static void assertDecode(String client_result, String peer_id) throws Exception {
		assertDecode(client_result, peerIDStringToBytes(peer_id));
	}

	private static void assertDecode(String client_result, byte[] peer_id) throws Exception {
		String peer_id_as_string = getPrintablePeerID(peer_id, '*');
		System.out.println("   Peer ID: " + peer_id_as_string + "   Client: " + client_result);

		// Do not log any clients.
		String decoded_result = decode(peer_id,AENetworkClassifier.AT_PUBLIC);
		if (client_result.equals(decoded_result)) {return;}
		throw new RuntimeException("assertion failure - expected \"" + client_result + "\", got \"" + decoded_result + "\": " + peer_id_as_string);
	}

	public static void main(String[] args) throws Exception {
		client_logging_allowed = false;

		final String FAKE = MessageText.getString("PeerSocket.fake_client");
		final String UNKNOWN = MessageText.getString("PeerSocket.unknown");
		final String BAD_PEER_ID = MessageText.getString("PeerSocket.bad_peer_id");

		System.out.println("Testing AZ style clients...");
		assertDecode("Ares 2.0.5.3", "-AG2053-Em6o1EmvwLtD");
		assertDecode("Ares 1.6.7.0", "-AR1670-3Ql6wM3hgtCc");
		assertDecode("Artemis 2.5.2.0", "-AT2520-vEEt0wO6v0cr");
		assertDecode("Vuze 2.2.0.0", "-AZ2200-6wfG2wk6wWLc");
		assertDecode("BT Next Evolution 1.0.9", "-NE1090002IKyMn4g7Ko");
		assertDecode("BitRocket 0.3(32)", "-BR0332-!XVceSn(*KIl");
		assertDecode("Mainline 6.0 Beta", "2D555436 3030422D A78DC290 C3F7BDE0 15EC3CC7");
		assertDecode("FlashGet 1.80", "2D464730 31383075 F8005782 1359D64B B3DFD265");
		assertDecode("GetRight 6.3", "-GR6300-13s3iFKmbArc");
		assertDecode("Halite 0.2.9", "-HL0290-xUO*9ugvENUE");
		assertDecode("KTorrent 1.1 RC1", "-KT11R1-693649213030");
		assertDecode("KTorrent 3.0", "2D4B543330302D006A7139727958377731756A4B");
		assertDecode("libTorrent (Rakshasa) 0.11.2 / rTorrent*", "2D6C74304232302D0D739B93E6BE21FEBB557B20");
		assertDecode("libtorrent (Rasterbar) 0.13.0", "-LT0D00-eZ0PwaDDr-~v"); // The latest version at time of writing is v0.12, but I'll assume this is valid.
		assertDecode("linkage 0.1.4", "-LK0140-ATIV~nbEQAMr");
		assertDecode("LimeWire", "2D4C57303030312D31E0B3A0B46F7D4E954F4103");
		assertDecode("Lphant 3.02", "2D4C5030 3330322D 00383336 35363935 37373030");
		assertDecode("Shareaza 2.1.3.2", "2D535A323133322D000000000000000000000000");
		assertDecode("SymTorrent 1.17", "-ST0117-01234567890!");
		assertDecode("Transmission 0.6", "-TR0006-01234567890!");
		assertDecode("Transmission 0.72 (Dev)", "-TR072Z-zihst5yvg22f");
		assertDecode("Transmission 0.72", "-TR0072-8vd6hrmp04an");
		assertDecode("TuoTu 2.1.0", "-TT210w-dq!nWf~Qcext");
		assertDecode("\u00B5Torrent 1.7.0 Beta", "2D555431 3730422D 92844644 1DB0A094 A01C01E5");
		assertDecode("\u54c7\u560E (Vagaa) 2.6.4.4", "2D5647323634342D4FD62CDA69E235717E3BB94B");
		//assertDecode("Wyzo 0.3.0.0", "-WY0300-6huHF5Pr7Vde");
		assertDecode("FireTorrent 0.3.0.0", "-WY0300-6huHF5Pr7Vde");
		assertDecode("CacheLogic 25.1-26", "-PC251Q-6huHF5Pr7Vde");
		assertDecode("KGet 2.4.5.0", "-KG2450-BDEw8OM14Hk6");


		System.out.println();

		// Shadow style clients.
		System.out.println("Testing Shadow style clients...");
		assertDecode("ABC", "A--------YMyoBPXYy2L"); // Seen this quite a bit - not sure that it is ABC, but I guess we should default to that...
		assertDecode("ABC 2.6.9", "413236392D2D2D2D345077199FAEC4A673BECA01");
		assertDecode("ABC 3.1", "A310--001v5Gysr4NxNK");
		assertDecode("BitTornado 0.3.12", "T03C-----6tYolxhVUFS");
		assertDecode("BitTornado 0.3.18", "T03I--008gY6iB6Aq27C");
		assertDecode("BitTornado 0.3.9", "T0390----5uL5NvjBe2z");
		assertDecode("Tribler 1", "R100--003hR6s07XWcov"); // Seen recently - is this really Tribler?
		assertDecode("Tribler 3.7", "R37---003uApHy851-Pq");
		System.out.println();

		// Simple substring style clients.
		System.out.println("Testing simple substring clients...");
		assertDecode("Azureus 1", "417A7572 65757300 00000000 000000A0 76F0AEF7");
		assertDecode("Azureus 2.0.3.2", "2D2D2D2D2D417A757265757354694E7A2A6454A7");
		assertDecode("G3 Torrent", "2D473341 6E6F6E79 6D6F7573 70E8D9CB 30250AD4");
		assertDecode("Hurricane Electric", "6172636C696768742E68652EA5860C157A5ADC35");
		assertDecode("Pando", "Pando-6B511B691CAC2E"); // Seen recently, have they changed peer ID format?
		assertDecode("\u00B5Torrent 1.7.0 RC", "2D55543137302D00AF8BC5ACCC4631481EB3EB60");
		System.out.println();

		// Version substring style clients.
		System.out.println("Testing versioned substring clients...");
		assertDecode("Bitlet 0.1", "4269744C657430319AEA4E02A09E318D70CCF47D");
		assertDecode("BitsOnWheels", "-BOWP05-EPICNZOGQPHP"); // Seen in the wild - no idea what version that's meant to be - a pre-release?
		assertDecode("Burst! 1.1.3", "Mbrst1-1-32e3c394b43");
		assertDecode("Opera (Build 7685)", "OP7685f2c1495b1680bf");
		assertDecode("Opera (Build 10063)", "O100634008270e29150a");
		assertDecode("Rufus 0.6.9", "00455253 416E6F6E 796D6F75 7382BE42 75024AE3");
		assertDecode("BitTorrent DNA 1.0", "444E413031303030DD01C9B2DA689E6E02803E91");
		assertDecode("BTuga Revolution 2.1", "BTM21abcdefghijklmno");
		assertDecode("AllPeers 0.70rc30", "4150302E3730726333302D3E3EB87B31F241DBFE"); // AP0.70rc30->>-{1-A--]"
		assertDecode("External Webseed", "45787420EC7CC30033D7801FEEB713FBB0557AC4");
		assertDecode("QVOD (Build 0054)", "QVOD00541234567890AB"); // Based on description on wiki.theory.org.
		assertDecode("Top-BT 1.0.0", "TB100----abcdefghijk");
		System.out.println();

		// BitComet/Lord/Spirit
		System.out.println("Testing BitComet/Lord/Spirit clients...");
		assertDecode("BitComet 0.56", "6578626300387A4463102D6E9AD6723B339F35A9");
		assertDecode("BitLord 0.56", "6578626300384C4F52443200048ECED57BD71028");
		assertDecode("BitSpirit? (" + BAD_PEER_ID + ")", "4D342D302D322D2D6898D9D0CAF25E4555445030");
		assertDecode("BitSpirit v2", "000242539B7ED3E058A8384AA748485454504254");
		assertDecode("BitSpirit v3", "00034253 07248896 44C59530 8A5FF2CA 55445030");
		System.out.println();

		// Mainline style clients.
		System.out.println("Testing new mainline style clients...");
		assertDecode("Mainline 5.0.7", "M5-0-7--9aa757efd5be");
		assertDecode("Amazon AWS S3", "S3-1-0-0--0123456789"); // Not currently decoded as mainline style...
		System.out.println();

		// Various specialised clients.
		System.out.println("Testing various specialised clients...");
		assertDecode("Mainline", "0000000000000000000000004C53441933104277");
		assertDecode(UNKNOWN + " [" + FAKE + ": ZipTorrent 1.6.0.0]", "-ZT1600-bLAdeY9rdjbe");

		assertDecode("Tixati 1.37", "TIX0137-i6i6f0i5d5b7");
		assertDecode("folx 0.9", "2D464C3039C6F22D5F436863327A6D792E283867");


		System.out.println();

		// Unknown clients - may be random bytes.
		System.out.println("Testing unknown (random byte?) clients...");
		assertDecode(UNKNOWN + " [--------1}-/---A---<]", "0000000000000000317DA32F831FF041A515FE3C");
		assertDecode(UNKNOWN + " [------- --  ------@(]", "000000DF05020020100020200008000000004028");
		assertDecode(UNKNOWN + " [-----------D-y-I--aO]", "0000000000000000F106CE44F179A2498FAC614F");
		assertDecode(UNKNOWN + " [--c--_-5-\\----t-#---]", "E7F163BB0E5FCD35005C09A11BC274C42385A1A0");
		System.out.println();

		// Unknown AZ style clients.
		System.out.println("Testing unknown AZ style clients...");
		String unknown_az;
		unknown_az = MessageText.getString("PeerSocket.unknown_az_style", new String[]{"BD", "0.3.0.0"});
		assertDecode(unknown_az, "-BD0300-1SGiRZ8uWpWH");
		unknown_az = MessageText.getString("PeerSocket.unknown_az_style", new String[]{"wF", "2.2.0.0"});
		assertDecode(unknown_az, "2D7746323230302D9DFF296B56AFC2DF751C609C");
		unknown_az = MessageText.getString("PeerSocket.unknown_az_style", new String[]{"X1", "0.0.6.4"});
		assertDecode(unknown_az, "2D5831303036342D12FB8A5B954153A114267F1F");
		//unknown_az = MessageText.getString("PeerSocket.unknown_az_style", new String[]{"bF", "2q00"}); // I made this one up.
		//assertDecode(unknown_az, "2D6246327130302D9DFF296B56AFC2DF751C609C");
		System.out.println();

		// Unknown Shadow style clients.
		System.out.println("Testing unknown Shadow style clients...");
		String unknown_shadow;
		unknown_shadow = MessageText.getString("PeerSocket.unknown_shadow_style", new String[]{"B", "1.2"});
		assertDecode(unknown_shadow, "B12------xgTofhetSVQ");
		System.out.println();

		// TODO
		//assertDecode("KTorrent 2.2", "-KT22B1-695754334315"); // We could use the B1 information...
		//assertDecode("KTorrent 2.1.4", "-KT2140-584815613993"); // Currently shows as 2.1.
		//assertDecode("", "C8F2D9CD3A90455354426578626300362D2D2D92"); // Looks like a BitLord client - ESTBexbc?
		//assertDecode("", "303030302D2D0000005433585859684B59584C72"); // Seen in the wild, appears to be a modified version of Azureus 2.5.0.0 (that's what was in the AZMP handshake)?
		//assertDecode("", "B5546F7272656E742F3330323520202020202020");

		assertDecode( "\u00B5Torrent Mac 1.5.11", "2D554D3135313130C964BE6F15CA71EF02AF2DD7" );

		assertDecode( "MediaGet",     "2D4D47314372302D3234705F6436000055673362" );

		assertDecode( "Invalid PeerID 0.0.0.0", "-#@0000-Em6o1EmvwLtD");

		assertDecode( "MediaGet2 2.1", "2D4D47323111302D3234705F6436706E55673362" );

		assertDecode( "Ares 2.1.7.1", "-AN2171-nr17R1h19O7n" );

		assertDecode( "\u00B5Torrent 3.4.0", "2D55543334302D000971FDE48C3688D2023506FC" );

		assertDecode( "BitTorrent 7.9.1", "2D42543739312D00A5792226709266A467EAD700" );
		
		assertDecode( "BitTorrent 7.10.1", "2D42543761312D00A5792226709266A467EAD700" );

		assertDecode( "Tixati 1.1.0.7", "-TX1107-811513660630" );

		assertDecode( "Torch 6.2.9.2", "-TB6292-jhBrpKfnZ!6e" );	// I know this is wrong as the real version is apparently Torch 29.0.0.6292 according to LTEP but woreva

		assertDecode( "WebTorrent 0.0.6.8", "-WW0068-b9539e1e4f95" );

		assertDecode( "BitLord 2.4.4-311",  "-BL244311-b9539e1e95" );
		
		assertDecode( "PicoTorrent 0.15.0", "-PI0150-9aa757efd5be" );
		
		assertDecode( "Zona 2.0.2.8", "-ZO2028-yeQBDpxy1b5b" );
		
		assertDecode( "UW 1.2.9.3", "-UW1293-NJu6-J8YPRiE");
		
		System.out.println("Done.");
	}
}
