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

import java.util.ArrayList;
import java.util.HashMap;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;

public class BTPeerIDByteDecoderDefinitions {

	// Az style two byte code identifiers to real client name.
	private static final HashMap az_style_code_map = new HashMap();
	private static final HashMap az_client_version_map = new HashMap();

	// Shadow's style one byte code identifiers to real client name.
	private static final HashMap shadow_style_code_map = new HashMap();
	private static final HashMap shadow_client_version_map = new HashMap();

	// Mainline's new style uses one byte code identifiers too.
	private static final HashMap mainline_style_code_map = new HashMap();

	/**
	 * List of objects which describes clients with their own custom
	 * naming scheme.
	 */
	private static final ArrayList custom_style_client_list = new ArrayList();

	// Version number formats.
	static final String VER_AZ_THREE_DIGITS = "1.2.3";
	static final String VER_AZ_THREE_DIGITS_PLUS_MNEMONIC = "1.2.3 [4]";
	static final String VER_AZ_ONE_MAJ_TWO_MIN_PLUS_MNEMONIC = "1.23 [4]";
	static final String VER_AZ_FOUR_DIGITS = "1.2.3.4";
	static final String VER_AZ_V_FOUR_DIGITS = "v1.2.3.4";
	static final String VER_AZ_TWO_MAJ_TWO_MIN = "12.34";
	static final String VER_AZ_ONE_MAJ_TWO_MIN_ONE_TRAIL = "1.23.4";
	static final String VER_AZ_SKIP_FIRST_ONE_MAJ_TWO_MIN = "2.34";
	static final String VER_AZ_KTORRENT_STYLE = "1.2.3=[RD].4";
	static final String VER_AZ_TRANSMISSION_STYLE = "transmission";
	static final String VER_AZ_LAST_THREE_DIGITS = "2.3.4";
	static final String VER_AZ_THREE_ALPHANUMERIC_DIGITS = "2.33.4"; // So 'B' = 11, for example, like Shadow's style of numbering.

	// These are used to describe how the version number is extracted from the
	// peer ID as well as how that version number is formatted.
	static final String VER_BLOCK = "abcde"; // Is given as a block in the peer ID, we show the same block
	static final String VER_DOTTED_BLOCK = "a.b.c.d.e"; // Is given as a dotted block in the peer ID, we show the block in the same dotted format.
	static final String VER_BYTE_BLOCK_DOTTED_CHAR = "abcde -> a.b.c.d.e"; // Is given a block in the peer ID, but should be displayed in a dotted format.
	static final String VER_BYTE_BLOCK_DOTTED_NUM  = "abcde -> n.o.p.q.r"; // Is given a block in the peer ID, but should be displayed in a dotted format but converted to digits a->10,b->11 etc.
	static final String VER_TWOBYTE_BLOCK_DOTTED_CHAR = "abcde -> ab.cd"; // Is given a block in the peer ID, but should be displayed in a dotted format.
	static final String VER_BITS_ON_WHEELS = "BOW-STYLE";
	static final String VER_TWO_BYTE_THREE_PART = "ab -> a . b/10 . b%10";
	static final String NO_VERSION = "NO_VERSION";
	static final String VER_BYTE_UM_STYLE = "abcd -> a.b.cd";
	static final String VER_BITLORD = "abcdef -> a.b.c-edf";
	static final String VER_TWO_BYTE_MAINLINE = "a-bb-d";

	// Used to register client information.
	private static void addAzStyle(String id, String client) {
		addAzStyle(id, client, VER_AZ_FOUR_DIGITS);
	}

	private static void addAzStyle(String id, String client, String version_style) {
		if (id.length() != 2) {throw new RuntimeException("not two chars long - " + id);}
		az_style_code_map.put(id, client);
		az_client_version_map.put(client, version_style);
	}

	private static void addShadowStyle(char id, String client) {
		addShadowStyle(id, client, VER_AZ_THREE_DIGITS);
	}

	private static void addShadowStyle(char id, String client, String version_style) {
		shadow_style_code_map.put("" + id, client);
		shadow_client_version_map.put(client, version_style);
	}

	private static void addMainlineStyle(char id, String client) {
		mainline_style_code_map.put("" + id, client);
	}

	private static ClientData addSimpleClient(String client_name, String identifier) {
		return addSimpleClient(client_name, identifier, 0);
	}

	private static ClientData addSimpleClient(String client_name, String identifier, int position) {
		ClientData result = new ClientData(client_name, identifier, position);
		custom_style_client_list.add(result);
		return result;
	}

	private static void addVersionedClient(ClientData client, String version_type, int length) {
		addVersionedClient(client, version_type, length, null);
	}

	private static void addVersionedClient(ClientData client, String version_type, int length, String format) {
		addVersionedClient(client, version_type, length, format, client.simple_string.length() + client.simple_string_pos);
	}

	private static void addVersionedClient(ClientData client, String version_type, int length, int version_pos) {
		addVersionedClient(client, version_type, length, null, version_pos);
	}

	private static void addVersionedClient(ClientData client, String version_type, int length, String format, int version_pos) {
		client.version_data = new VersionNumberData(version_type, length, format, version_pos);
	}

	public static String getAzStyleClientName(String peer_id) {
		return (String)az_style_code_map.get(peer_id.substring(1, 3));
	}

	public static String getShadowStyleClientName(String peer_id) {
		return (String)shadow_style_code_map.get(peer_id.substring(0, 1));
	}

	public static String getMainlineStyleClientName(String peer_id) {
		return (String)mainline_style_code_map.get(peer_id.substring(0, 1));
	}

	public static String getAzStyleClientVersion(String client_name, String peer_id, String net ) {
		String version_scheme = (String)az_client_version_map.get(client_name);
		if (version_scheme == NO_VERSION) {return null;}
		try {
			return client_name + " " + BTPeerIDByteDecoderUtils.decodeAzStyleVersionNumber(
				peer_id.substring(3, 7), version_scheme
			);
		}
		catch (Exception e) {
			BTPeerIDByteDecoder.logUnknownClient(peer_id, net);
			return null;
		}
	}

	public static ClientData getSubstringStyleClient(String peer_id) {
		ClientData cd = null;
		for (int i=0; i<custom_style_client_list.size(); i++) {
			cd = (ClientData)custom_style_client_list.get(i);
			if (peer_id.startsWith(cd.simple_string, cd.simple_string_pos)) {
				return cd;
			}
		}
		return null;
	}

	public static String getSubstringStyleClientVersion(ClientData client_data, String peer_id, byte[] peer_id_bytes, String net) {
		VersionNumberData verdata = client_data.version_data;
		if (verdata == null) {return null;}

		String version_scheme = verdata.type;
		String version_string = null;
		try {
			if (version_scheme == VER_TWO_BYTE_THREE_PART) {
				int start_byte_index = verdata.pos;
				version_string = BTPeerIDByteDecoderUtils.getTwoByteThreePartVersion(peer_id_bytes[start_byte_index], peer_id_bytes[start_byte_index+1]);
			}
			else if (version_scheme == VER_BLOCK && verdata.length == -1) {
				version_string = BTPeerIDByteDecoderUtils.extractReadableVersionSubstringFromPeerID(peer_id.substring(verdata.pos, peer_id.length()));
			}
			else {
				version_string = BTPeerIDByteDecoderUtils.decodeCustomVersionNumber(
					peer_id.substring(verdata.pos, verdata.pos + verdata.length), version_scheme
				);
			}

			if (verdata.fmt == null) {return client_data.client_name + " " + version_string;}
			else {return client_data.client_name + verdata.fmt.replaceFirst("%s", version_string);}
		}
		catch (Exception e) {
			BTPeerIDByteDecoder.logUnknownClient(peer_id, net);
			return null;
		}
	}

	public static String formatUnknownAzStyleClient(String peer_id) {
		String version_string = peer_id.substring(3, 7);
		try {
		    version_string = BTPeerIDByteDecoderUtils.decodeAzStyleVersionNumber(
		    		version_string, VER_AZ_FOUR_DIGITS);
		}
		catch (Exception e) {/* Just use the unformatted string */}

		return MessageText.getString("PeerSocket.unknown_az_style", new String[] {
		    peer_id.substring(1, 3), version_string
		});
	}

	public static String formatUnknownShadowStyleClient(String peer_id) {
		String version_string = BTPeerIDByteDecoderUtils.getShadowStyleVersionNumber(peer_id);
		return MessageText.getString("PeerSocket.unknown_shadow_style", new String[] {
			peer_id.substring(0, 1), version_string
		});
	}


	/**
     * OK - here's where we store the definitions.
	 *
	 * Good place for information about BT peer ID conventions:
	 *  http://wiki.theory.org/BitTorrentSpecification
	 *  http://transmission.m0k.org/trac/browser/trunk/libtransmission/clients.c (hello Transmission authors!) :)
	 *  http://rufus.cvs.sourceforge.net/rufus/Rufus/g3peerid.py?view=log (for older clients)
	 *  http://shareaza.svn.sourceforge.net/viewvc/shareaza/trunk/shareaza/BTClient.cpp?view=markup
	 *  http://libtorrent.rakshasa.no/browser/trunk/libtorrent/src/torrent/peer/client_list.cc
	 *
	 * By default - if you are unsure about a client's versioning scheme, you should
	 * register it without passing an explicit value.
	 *
	 * There are some clients which generate peer ID's in Azureus style, but some don't
	 * provide accurate or maybe even any information about what version they are.
	 *
	 * I've decided that we should follow this criteria - if a client generates a peer
	 * ID which strictly matches the Az-style specification (0'th char is '-', 7'th char
	 * is '-'), then we should include it in this list - if there's no derivable version
	 * information, then we flag it as NO_VERSION.
	 *
	 * Oh, and hello any closed source BitTorrent client authors who may be reading! ;)
	 */
	static {
		// We define ourselves first... :)

		addAzStyle(Constants.BIGLY_PEER_ID, Constants.BIGLY_PROTOCOL_NAME, VER_AZ_FOUR_DIGITS);
		addAzStyle("AZ", Constants.AZUREUS_PROTOCOL_NAME, VER_AZ_FOUR_DIGITS);

		// ... and then do everything else alphabetically.
		addAzStyle("A~", "Ares", VER_AZ_THREE_DIGITS);
		addAzStyle("AG", "Ares", VER_AZ_THREE_DIGITS);
		addAzStyle("AN", "Ares", VER_AZ_FOUR_DIGITS);
		//addAzStyle("AR", "ArcticTorrent", NO_VERSION); //based on libtorrent but same peerid for different versions
		addAzStyle("AR", "Ares"); // Ares is more likely than ArcticTorrent
		addAzStyle("AV", "Avicora");
		addAzStyle("AX", "BitPump", VER_AZ_TWO_MAJ_TWO_MIN);
		addAzStyle("AT", "Artemis");
		addAzStyle("BB", "BitBuddy", "1.234");
		addAzStyle("BC", "BitComet", VER_AZ_SKIP_FIRST_ONE_MAJ_TWO_MIN);
		addAzStyle("BE", "BitTorrent SDK");
		addAzStyle("BF", "BitFlu", NO_VERSION);
		addAzStyle("BG", "BTG", VER_AZ_FOUR_DIGITS);
		addAzStyle("bk", "BitKitten (libtorrent)");
		addAzStyle("BR", "BitRocket", "1.2(34)");
		addAzStyle("BS", "BTSlave");
		// addAzStyle("BW", "BitWombat");	dead apparently and re-used by BitTorrent Web
		addAzStyle("BX", "BittorrentX");
		addAzStyle("CB", "Shareaza Plus");
		addAzStyle("CD", "Enhanced CTorrent", VER_AZ_TWO_MAJ_TWO_MIN);
		addAzStyle("CT", "CTorrent", "1.2.34");
		addAzStyle("DP", "Propagate Data Client");
		addAzStyle("DE", "Deluge", VER_AZ_FOUR_DIGITS);
		addAzStyle("EB", "EBit");
		addAzStyle("ES", "Electric Sheep", VER_AZ_THREE_DIGITS);
		addAzStyle("eM", "eMule", NO_VERSION); // has what looks like a 1.2.3.4 version, but 1004 != 0.60
		addAzStyle("FC", "FileCroc");
		addAzStyle("FG", "FlashGet", VER_AZ_SKIP_FIRST_ONE_MAJ_TWO_MIN);
		addAzStyle("FT", "FoxTorrent/RedSwoosh");
		addAzStyle("FW", "FrostWire");
		addAzStyle("GR", "GetRight", "1.2");
		addAzStyle("GS", "GSTorrent"); // TODO: Format is v"abcd"
		addAzStyle("HL", "Halite", VER_AZ_THREE_DIGITS);
		addAzStyle("IL", "iLivid", VER_AZ_THREE_DIGITS);
		addAzStyle("HN", "Hydranode");
		addAzStyle("KG", "KGet");
		addAzStyle("KT", "KTorrent", VER_AZ_KTORRENT_STYLE);
		addAzStyle("LC", "LeechCraft");
		addAzStyle("LH", "LH-ABC");
		addAzStyle("LK", "linkage", VER_AZ_THREE_DIGITS);
		addAzStyle("LP", "Lphant", VER_AZ_TWO_MAJ_TWO_MIN);
		addAzStyle("LT", "libtorrent (Rasterbar)", VER_AZ_THREE_ALPHANUMERIC_DIGITS);
		addAzStyle("lt", "libTorrent (Rakshasa)", VER_AZ_THREE_ALPHANUMERIC_DIGITS);
		addAzStyle("LW", "LimeWire", NO_VERSION); // The "0001" bytes found after the LW commonly refers to the version of the BT protocol implemented. Documented here: http://www.limewire.org/wiki/index.php?title=BitTorrentRevision
		addAzStyle("MO", "MonoTorrent");
		addAzStyle("MP", "MooPolice", VER_AZ_THREE_DIGITS);
		addAzStyle("MR", "Miro");
		addAzStyle("MT", "MoonlightTorrent");
		addAzStyle("NE", "BT Next Evolution", VER_AZ_THREE_DIGITS);
		addAzStyle("NX", "Net Transport");
		addAzStyle("OS", "OneSwarm", VER_AZ_FOUR_DIGITS);
		addAzStyle("OT", "OmegaTorrent");
		addAzStyle("PC", PeerClassifier.CACHE_LOGIC, "12.3-4" );
		addAzStyle("PD", "Pando");
		addAzStyle("PE", "PeerProject");
		addAzStyle("pX", "pHoeniX");
		addAzStyle("qB", "qBittorrent", VER_AZ_THREE_ALPHANUMERIC_DIGITS);
		addAzStyle("QD", "qqdownload");
		addAzStyle("RT", "Retriever");
		addAzStyle("RZ", "RezTorrent");
		addAzStyle("S~", "Shareaza alpha/beta");
		addAzStyle("SB", "SwiftBit");
		addAzStyle("SD", "\u8FC5\u96F7\u5728\u7EBF (Xunlei)"); // Apparently, the English name of the client is "Thunderbolt".
		addAzStyle("SG", "GS Torrent", VER_AZ_FOUR_DIGITS);
		addAzStyle("SN", "ShareNET");
		addAzStyle("SP", "BitSpirit"); // >= 3.6
		addAzStyle("SS", "SwarmScope");
		addAzStyle("ST", "SymTorrent", "2.34");
		addAzStyle("st", "SharkTorrent");
		addAzStyle("SZ", "Shareaza");
		addAzStyle("TB", "Torch");
		addAzStyle("TN", "Torrent.NET");
		addAzStyle("TR", "Transmission", VER_AZ_TRANSMISSION_STYLE);
		addAzStyle("TS", "TorrentStorm");
		addAzStyle("TT", "TuoTu", VER_AZ_THREE_DIGITS);
		addAzStyle("tT", "tTorrent", VER_AZ_V_FOUR_DIGITS);
		addAzStyle("TX", "Tixati");
		addAzStyle("UL", "uLeecher!");
		addAzStyle("UT", "\u00B5Torrent", VER_AZ_THREE_DIGITS_PLUS_MNEMONIC);
		addAzStyle("UM", "\u00B5Torrent Mac", VER_AZ_THREE_DIGITS_PLUS_MNEMONIC);
		addAzStyle("WT", "Bitlet");
		addAzStyle("WW", "WebTorrent");
		addAzStyle("WY", "FireTorrent"); // formerly Wyzo.
		addAzStyle("VG", "\u54c7\u560E (Vagaa)", VER_AZ_FOUR_DIGITS);
		addAzStyle("XF", "Xfplay", VER_AZ_FOUR_DIGITS);
		addAzStyle("XL", "\u8FC5\u96F7\u5728\u7EBF (Xunlei)"); // Apparently, the English name of the client is "Thunderbolt".
		addAzStyle("XT", "XanTorrent");
		addAzStyle("XX", "XTorrent", "1.2.34");
		addAzStyle("XC", "XTorrent", "1.2.34");
		addAzStyle("ZO", "Zona", VER_AZ_FOUR_DIGITS);
		addAzStyle("ZT", "ZipTorrent");
		addAzStyle("7T", "aTorrent");
		addAzStyle("#@", "Invalid PeerID");

		addShadowStyle('A', "ABC");
		addShadowStyle('O', "Osprey Permaseed");
		addShadowStyle('Q', "BTQueue");
		addShadowStyle('R', "Tribler");
		addShadowStyle('S', "Shad0w");
		addShadowStyle('T', "BitTornado");
		addShadowStyle('U', "UPnP NAT");

		addMainlineStyle('M', "Mainline");
		addMainlineStyle('Q', "Queen Bee");

		// Simple clients with no version number.
		addSimpleClient("\u00B5Torrent 1.7.0 RC", "-UT170-"); // http://forum.utorrent.com/viewtopic.php?pid=260927#p260927
		addSimpleClient("Azureus 1", "Azureus");
		addSimpleClient("Azureus 2.0.3.2", "Azureus", 5);
		addSimpleClient("Aria 2", "-aria2-");
		addSimpleClient("BitTorrent Plus! II", "PRC.P---");
		addSimpleClient("BitTorrent Plus!", "P87.P---");
		addSimpleClient("BitTorrent Plus!", "S587Plus");
		addSimpleClient("BitTyrant (Azureus Mod)", "AZ2500BT");
		addSimpleClient("Blizzard Downloader", "BLZ");
		addSimpleClient("BTGetit", "BG", 10);
		addSimpleClient("BTugaXP", "btuga");
		addSimpleClient("BTugaXP", "BTuga", 5);
		addSimpleClient("BTugaXP", "oernu");
		addSimpleClient("Deadman Walking", "BTDWV-");
		addSimpleClient("Deadman", "Deadman Walking-");
		addSimpleClient("External Webseed", "Ext");
		addSimpleClient("G3 Torrent", "-G3");
		addSimpleClient("GreedBT 2.7.1", "271-");
		addSimpleClient("Hurricane Electric", "arclight");
		addSimpleClient("HTTP Seed", "-WS" );	// used internally to denote incoming webseed connections
		addSimpleClient("JVtorrent", "10-------");
		addSimpleClient("Limewire", "LIME");
		addSimpleClient("Martini Man", "martini");
		addSimpleClient("Pando", "Pando");
		addSimpleClient("PeerApp", "PEERAPP");
		addSimpleClient("SimpleBT", "btfans", 4);
		addSimpleClient("Swarmy", "a00---0");
		addSimpleClient("Swarmy", "a02---0");
		addSimpleClient("Teeweety", "T00---0");
		addSimpleClient("TorrentTopia", "346-");
		addSimpleClient("XanTorrent", "DansClient");
		addSimpleClient("MediaGet",     "-MG1");
		addSimpleClient("MediaGet2 2.1", "-MG21");

		/**
		 * This is interesting - it uses Mainline style, except uses two characters instead of one.
		 * And then - the particular numbering style it uses would actually break the way we decode
		 * version numbers (our code is too hardcoded to "-x-y-z--" style version numbers).
		 *
		 * This should really be declared as a Mainline style peer ID, but I would have to
		 * make my code more generic. Not a bad thing - just something I'm not doing right
		 * now.
		 */
		addSimpleClient("Amazon AWS S3", "S3-");

		// Clients with their own custom format and version number style.
		ClientData client = null;

		// DNA01000 becomes version 1.0 - we'll ignore the other digits for now.
		client = addSimpleClient("BitTorrent DNA", "DNA");
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_CHAR, 2, 4);

		// Pre build 10000 versions.
		client = addSimpleClient("Opera", "OP");
		addVersionedClient(client, VER_BLOCK, 4, " (Build %s)");

		// Post build 10000 versions.
		client = addSimpleClient("Opera", "O");
		addVersionedClient(client, VER_BLOCK, 5, " (Build %s)");

		client = addSimpleClient("Burst!", "Mbrst");
		addVersionedClient(client, VER_DOTTED_BLOCK, 5); // 3 version components, 5 chars which describe it

		client = addSimpleClient("TurboBT", "turbobt");
		addVersionedClient(client, VER_BLOCK, 5);

		client = addSimpleClient("BT Protocol Daemon", "btpd");
		addVersionedClient(client, VER_BLOCK, 3, 5);

		client = addSimpleClient("Plus!", "Plus");
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_CHAR, 3);

		client = addSimpleClient("XBT", "XBT");
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_CHAR, 3);

		client = addSimpleClient("BitsOnWheels", "-BOW");
		addVersionedClient(client, VER_BITS_ON_WHEELS, 3);

		client = addSimpleClient("eXeem", "eX");
		addVersionedClient(client, VER_BLOCK, 18, " [%s]"); // Refers to user ID.

		client = addSimpleClient("MLdonkey", "-ML");
		addVersionedClient(client, VER_DOTTED_BLOCK, 5);

		client = addSimpleClient("Bitlet", "BitLet");
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_CHAR, 2);

		client = addSimpleClient("AllPeers", "AP");
		addVersionedClient(client, VER_BLOCK, -1);

		client = addSimpleClient("BTuga Revolution", "BTM");
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_CHAR, 2);

		client = addSimpleClient("Rufus", "RS", 2);
		addVersionedClient(client, VER_TWO_BYTE_THREE_PART, 2, 0);

		// BitMagnet - predecessor to Rufus.
		client = addSimpleClient("BitMagnet", "BM", 2);
		addVersionedClient(client, VER_TWO_BYTE_THREE_PART, 2, 0);

		client = addSimpleClient("QVOD", "QVOD");
		addVersionedClient(client, VER_BLOCK, 4, " (Build %s)");

		// Top-BT - based on BitTornado, but doesn't quite stick
		// to Shadow's naming conventions - so we'll use substring
		// matching instead.
		client = addSimpleClient("Top-BT", "TB");
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_CHAR, 3);

		client = addSimpleClient("Tixati", "TIX");
		addVersionedClient(client, VER_TWOBYTE_BLOCK_DOTTED_CHAR, 4);

		client = addSimpleClient("folx", "-FL");	// seems to have a sub-version encoded in following 3 bytes, not worked out how: "folx/1.0.456.591" : 2D 464C 3130 FF862D 486263574A43585F66314D5A
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_CHAR, 2);

		client = addSimpleClient("\u00B5Torrent Mac", "-UM");
		addVersionedClient(client, VER_BYTE_UM_STYLE, 4);

		client = addSimpleClient("\u00B5Torrent", "-UT");		// UT 3.4+
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_CHAR, 3);

		client = addSimpleClient("BitTorrent", "-BT");		// BitTorrent 7.9.1 appeared with this: -BTnnn-
		addVersionedClient(client, VER_BYTE_BLOCK_DOTTED_NUM, 3);

		client = addSimpleClient("BitLord", "-BL");
		addVersionedClient(client, VER_BITLORD, 6, 3);

		// A2-a-bb.d-
		client = addSimpleClient("Aria2", "A2-");
		addVersionedClient(client, VER_TWO_BYTE_MAINLINE, 6, 3);

		addAzStyle( "PI", "PicoTorrent", VER_AZ_ONE_MAJ_TWO_MIN_ONE_TRAIL);

	}

	static class ClientData {

		final String client_name;
		private final int simple_string_pos;
		private final String simple_string;
		private VersionNumberData version_data;

		ClientData(String client_name, String simple_string, int simple_string_pos) {
			this.simple_string_pos = simple_string_pos;
			this.simple_string = simple_string;
			this.client_name = client_name;
			this.version_data = null;
		}

	}

	static class VersionNumberData {

		final String type; // How to extract and put digit components together
		final int pos; // Where does this appear in a client ID string?
		final String fmt; // How do we display that version number?
		final int length; // How long is the version number?

		VersionNumberData(String type, int length, String formatter, int position) {
			this.type = type;
			this.pos = position;
			this.fmt = formatter;
			this.length = length;
		}

	}


}
