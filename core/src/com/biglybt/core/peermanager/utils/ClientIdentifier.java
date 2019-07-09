/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.core.peermanager.utils;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.ByteFormatter;

public class ClientIdentifier {

	public static String identifyBTOnly(String peer_id_client, byte[] handshake_bytes) {

		// BitThief check.
		if (peer_id_client.equals("Mainline 4.4.0") && (handshake_bytes[7] & (byte)1) == 0) {
			return asDiscrepancy("BitThief*", peer_id_client, "fake_client");
		}

		  // We do care if something is claiming to be Azureus when it isn't. If
		  // it's a recent version of Azureus, but doesn't support advanced messaging, we
		  // know it's a fake.
		  if (!peer_id_client.startsWith("Azureus ")) {return peer_id_client;}

		  // Older versions of Azureus won't have support, so discount these first.
		  String version = peer_id_client.substring(8);
		  if (version.startsWith("1") || version.startsWith("2.0") ||
				  version.startsWith("2.1") || version.startsWith("2.2")) {
			  return peer_id_client;
		  }

		  // Must be a fake.
		  return asDiscrepancy(null, peer_id_client, "fake_client");
	}

	public static String identifyAZMP(String peer_id_client_name, String az_msg_client_name, String az_msg_client_version, byte[] peer_id) {

		/**
		 * Hack for BitTyrant - the handshake resembles this:
		 *   Client: AzureusBitTyrant
		 *   ClientVersion: 2.5.0.0BitTyrant
		 *
		 * Yuck - let's format it so it resembles something pleasant.
 	     */
		if (az_msg_client_name.endsWith("BitTyrant")) {
			  return "BitTyrant " + az_msg_client_version.replaceAll("BitTyrant", "") + " (Azureus Mod)";
		  }

		  String msg_client_name = az_msg_client_name + " " + az_msg_client_version;

		  /**
		   * Do both names seem to match?
		   */
		  if (msg_client_name.equals(peer_id_client_name)) {return msg_client_name;}

		  /**
		   * There may be some discrepancy - a different version number perhaps.
		   * If the main client name still seems to be the same, then return the one
		   * given to us in the AZ handshake.
		   */
		  String peer_id_client = peer_id_client_name.split(" ", 2)[0];
		  String az_client_name = az_msg_client_name.split(" ", 2)[0];
		  if (peer_id_client.equals(az_client_name)) {
			  /**
			   * If both are Azureus, the version numbers shouldn't differ. This is what
			   * we should have - 15 characters both the same (sometimes beta version
			   * is included in the version number but not in the peer ID, but we can deal
			   * with that.
			   *   "Azureus a.b.c.d"
			   */
			  if (az_client_name.equals("Azureus") && peer_id_client.equals("Azureus")) {
				  if (msg_client_name.length()<15 || peer_id_client_name.length() < 15 || !msg_client_name.substring(0, 15).equals(peer_id_client_name.substring(0, 15))) {
					  return asDiscrepancy("Azureus (Hacked)", peer_id_client_name, msg_client_name, "fake_client", "AZMP", peer_id);
				  }
			  }
			  return msg_client_name;
		  }

			// Transmission and XTorrent.
			String res = checkForTransmissionBasedClients(msg_client_name, peer_id_client, peer_id_client_name, msg_client_name, peer_id, "AZMP");
			if (res != null) {return res;}

		  // There is an inconsistency. Let's try figuring out what we can.
		  String client_displayed_name = null;
		  boolean is_peer_id_azureus = peer_id_client_name.startsWith("Azureus ");
		  boolean is_msg_client_azureus = az_msg_client_name.equals("Azureus");
		  boolean is_fake = false;
		  boolean is_mismatch = true;
		  boolean is_peer_id_unknown = peer_id_client_name.startsWith(MessageText.getString("PeerSocket.unknown"));

		  if (is_peer_id_azureus) {

			  // Shouldn't happen.
			  if (is_msg_client_azureus) {
				  throw new RuntimeException("logic error in getExtendedClientName - both clients are Azureus");
			  }
			  else {
				  // We've got a peer ID that says Azureus, but it doesn't say Azureus in the handshake.
				  // It's definitely fake.
				  is_fake = true;

				  // It might be XTorrent - it does use AZ2504 in the peer ID and "Transmission 0.7-svn"
				  // in the handshake.
				  if (msg_client_name.equals("Transmission 0.7-svn")) {client_displayed_name = "XTorrent";}
			  }
		  }
		  else {
			  if (is_msg_client_azureus) {is_fake = true;}
			  else if (is_peer_id_unknown) {
				  // Our peer ID decoding can't decode it, but the client identifies itself anyway.
				  // In that case, we won't say that it is a mismatch, and we'll just use the name
				  // provided to us.
				  client_displayed_name = msg_client_name;
				  is_mismatch = false;

				  // Log it though.
				  BTPeerIDByteDecoder.logClientDiscrepancy(peer_id_client_name, msg_client_name, "unknown_client", "AZMP", peer_id);
			  }
			  else {
				  // We've got a general mismatch, we don't know what client it is - in most cases.

				  // Ares Galaxy sometimes uses the same peer ID as Arctic Torrent, so allow it to be
				  // overridden.
				  if (msg_client_name.startsWith("Ares") && peer_id_client.equals("ArcticTorrent")) {
					  return msg_client_name;
				  }
			  }
		  }

		  String discrepancy_type;
		  if (is_fake) {discrepancy_type = "fake_client";}
		  else if (is_mismatch) {discrepancy_type = "mismatch_id";}
		  else {discrepancy_type = null;}

		  if (discrepancy_type != null) {
			  return asDiscrepancy(null, peer_id_client_name, msg_client_name, discrepancy_type, "AZMP", peer_id);
		  }

		  return client_displayed_name;
	  }

	public static String identifyLTEP(String peer_id_name, String handshake_name, byte[] peer_id) {
		if (handshake_name == null) {return peer_id_name;}

		/**
		 * Official BitTorrent clients should still be shown as Mainline.
		 * This is to be consistent with previous Azureus behaviour.
		 */
		String handshake_name_to_process = handshake_name;
//		if (handshake_name.startsWith("BitTorrent ")) {
//			handshake_name_to_process = handshake_name.replaceFirst("BitTorrent", "Mainline");
//		}

		if (peer_id_name.startsWith("\u00B5Torrent")) {

			// 1.6.0 misidentifies itself as 1.5 in the handshake.
			if (peer_id_name.equals("\u00B5Torrent 1.6.0")) {
				return peer_id_name;
			}

			// Older �Torrent versions will not always use the appropriate character for the
			// first letter, so compensate here.
			if (!handshake_name.startsWith("\u00B5Torrent") && handshake_name.startsWith("Torrent", 1)) {
				handshake_name_to_process = "\u00B5" + handshake_name.substring(1);
			}

			// Some versions indicate they are the beta version in the peer ID, but not in the
			// handshake - we prefer to keep the beta identifier.
			if (peer_id_name.endsWith("Beta") && peer_id_name.startsWith(handshake_name_to_process)) {
				return peer_id_name;
			}
		}

		// Some Mainline 4.x versions identify themselves as �Torrent - according to alus,
		// this was a bug, so just identify as Mainline.
		if (peer_id_name.startsWith("Mainline 4.") && handshake_name.startsWith("Torrent", 1)) {
			return peer_id_name;
		}

		// Azureus should never be using LTEP when connected to another Azureus client!
		if (peer_id_name.startsWith("Azureus") && handshake_name.startsWith("Azureus")) {
			return asDiscrepancy(null, peer_id_name, handshake_name, "fake_client", "LTEP", peer_id);
		}

		// We allow a client to have a different version number than the one decoded from
		// the peer ID. Some clients separate version and client name using a forward slash,
		// so we split on that as well.
		String client_type_peer = peer_id_name.split(" ", 2)[0];
		String client_type_handshake = handshake_name_to_process.split(" ", 2)[0].split("/", 2)[0];

		// Transmission and XTorrent.
		String res = checkForTransmissionBasedClients(handshake_name_to_process, client_type_peer, peer_id_name, handshake_name, peer_id, "LTEP");
		if (res != null) {return res;}

		if (client_type_peer.toLowerCase().equals(client_type_handshake.toLowerCase())) {return handshake_name_to_process;}

		// Like we do with AZMP peers, allow the handshake to define the client even if we can't extract the
		// name from the peer ID, but log it so we can possibly identify it in future.
		if (peer_id_name.startsWith(MessageText.getString("PeerSocket.unknown"))) {
			BTPeerIDByteDecoder.logClientDiscrepancy(peer_id_name, handshake_name, "unknown_client", "LTEP", peer_id);
			return handshake_name_to_process;
		}

		/**
		 * libtorrent is... unsurprisingly... a torrent library. Many clients use it, so cope with clients
		 * which don't identify themselves through the peer ID, but *do* identify themselves through the
		 * handshake.
		 */
		if (peer_id_name.startsWith("libtorrent (Rasterbar)")) {
			if (!handshake_name_to_process.toLowerCase().contains("libtorrent")) {
				handshake_name_to_process += " (" + peer_id_name + ")";
			}
			return handshake_name_to_process;
		}

		/**
		 * And some clients do things the other way round - they don't bother with the handshake name,
		 * but do remember to change the peer ID name.
		 */
		if (client_type_handshake.startsWith("libtorrent")) {
			// Peer ID doesn't mention libtorrent (just the client name) and the handshake name doesn't
			// mention the client name (just "libtorrent"), then combine them together.
			if (!client_type_peer.toLowerCase().contains("libtorrent") &&
				!client_type_handshake.toLowerCase()
					.contains(client_type_peer.toLowerCase())) {
				return peer_id_name + " (" + handshake_name_to_process + ")";
			}
		}

		if (client_type_peer.startsWith("\u8FC5\u96F7\u5728\u7EBF")
				&& handshake_name_to_process.length() > 0
				// Not sure if [0] is a valid check, but recent versions report
				// "-XL0".., so I added a check at [3]
				&& (Character.isDigit(handshake_name_to_process.charAt(0))
						|| Character.isDigit(handshake_name_to_process.charAt(3)))) {
			return peer_id_name + " (" + handshake_name_to_process + ")";
		}

			// meh, now we have Mainline and BitTorrent confusion from 7.9.2 onwards, fix here is just to do a further check on the names as they will
			// have been 'normalised' into BitTorrent by here

		if ( peer_id_name.equals( handshake_name )){

			return( peer_id_name );
		}

		// Can't determine what the client is.
		return asDiscrepancy(null, peer_id_name, handshake_name, "mismatch_id", "LTEP", peer_id);
	}

	private static String checkForTransmissionBasedClients(String handshake_name_to_process, String client_type_peer, String peer_id_name, String handshake_name, byte[] peer_id, String protocol) {

		// Bloody XTorrent.
		if (handshake_name_to_process.equals("Transmission 0.7-svn") && client_type_peer.equals("Azureus")) {
			return asDiscrepancy("XTorrent", peer_id_name, handshake_name, "fake_client", protocol, peer_id);
		}

		// Bloody XTorrent!
		if (handshake_name_to_process.startsWith("Transmission") && client_type_peer.startsWith("XTorrent")) {
			return asDiscrepancy(client_type_peer, handshake_name_to_process, "fake_client");
		}

		// Transmission 0.96 still uses 0.95 in the LT handshake, so cope with that and just display
		// 0.96.
		if (peer_id_name.equals("Transmission 0.96") && handshake_name.equals("Transmission 0.95")) {
			return peer_id_name;
		}

		return null;
	}

	  private static String asDiscrepancy(String client_name, String peer_id_name, String handshake_name, String discrepancy_type, String protocol_type, byte[] peer_id) {
		  if (client_name == null) {
			  BTPeerIDByteDecoder.logClientDiscrepancy(peer_id_name, handshake_name, discrepancy_type, protocol_type, peer_id);
		  }

		  // Use this form as it is shorter.
		  if (peer_id_name.equals(handshake_name)) {return asDiscrepancy(client_name, peer_id_name, discrepancy_type);}
		  return asDiscrepancy(client_name, peer_id_name + "\" / \"" + handshake_name, discrepancy_type);
	  }

	  private static String asDiscrepancy(String real_client, String dodgy_client, String discrepancy_type) {
		  if (real_client == null) {
			  real_client = MessageText.getString("PeerSocket.unknown");
		  }
		  return real_client + " [" +
		  	MessageText.getString("PeerSocket." + discrepancy_type) + ": \"" + dodgy_client + "\"]";
	  }

	  private static int test_count = 1;
	  private static void assertDecode(String client_name, String peer_id, String handshake_name, String handshake_version, byte[] handshake_reserved, String type) throws Exception {
		  byte[] byte_peer_id = BTPeerIDByteDecoder.peerIDStringToBytes(peer_id);
		  String peer_id_client = BTPeerIDByteDecoder.decode(byte_peer_id,AENetworkClassifier.AT_PUBLIC);

		  String decoded_client;
		  if (type.equals("AZMP")) {decoded_client = identifyAZMP(peer_id_client, handshake_name, handshake_version, byte_peer_id);}
		  else if (type.equals("LTEP")) {decoded_client = identifyLTEP(peer_id_client, handshake_name, byte_peer_id);}
		  else if (type.equals("BT")) {decoded_client = identifyBTOnly(peer_id_client, handshake_reserved);}
		  else {throw new RuntimeException("invalid extension type: " + type);}

		  boolean passed = client_name.equals(decoded_client);
		  System.out.println("  Test " + test_count++ + ": \"" + client_name + "\" - " + (passed ? "PASSED" : "FAILED"));

		  if (!passed) {
			  throw new Exception("\n" +
			  "Decoded      : " + decoded_client + "\n" +
			  "Peer ID name : " + peer_id_client + "\n" +
			  "Extended name: " + handshake_name + "\n");

			  //throw new Exception("Client name decoded - " + decoded_client);
		  }
	  }

	  private static void assertDecodeAZMP(String client_name, String peer_id, String handshake_name, String handshake_version) throws Exception {
		  assertDecode(client_name, peer_id, handshake_name, handshake_version, null, "AZMP");
	  }

	  private static void assertDecodeLTEP(String client_name, String peer_id, String handshake_name) throws Exception {
		  assertDecode(client_name, peer_id, handshake_name, null, null, "LTEP");
	  }

	  private static void assertDecodeExtProtocol(String client_name, String peer_id, String handshake_name, String handshake_version) throws Exception {
		  assertDecodeAZMP(client_name, peer_id, handshake_name, handshake_version);
		  assertDecodeLTEP(client_name, peer_id, handshake_name + " " + handshake_version);
	  }

	  private static void assertDecodeBT(String client_name, String peer_id, String handshake_reserved) throws Exception {
		  if (handshake_reserved == null) {handshake_reserved = "0000000000000000";}
		  handshake_reserved = handshake_reserved.replaceAll("[ ]", "");
		  byte[] handshake_reserved_bytes = ByteFormatter.decodeString(handshake_reserved);
		  if (handshake_reserved_bytes.length != 8) {throw new RuntimeException("invalid handshake reserved bytes");}
		  assertDecode(client_name, peer_id, null, null, handshake_reserved_bytes, "BT");
	  }

	  public static void main(String[] args) throws Exception {
		  System.setProperty("transitory.startup", "1");

		  BTPeerIDByteDecoder.client_logging_allowed = false;

		  System.out.println("Testing simple BT clients:");
		  assertDecodeBT("BitThief* [FAKE: \"Mainline 4.4.0\"]", "M4-4-0--9aa757efd5be", "0000000000000000");
		  assertDecodeBT("Mainline 4.4.0", "M4-4-0--9aa757efd5be", "0000000000000001");
		  assertDecodeBT("Unknown [FAKE: \"Azureus 3.0.3.4\"]", "-AZ3034-6wfG2wk6wWLc", "0000000000000000");
		  System.out.println("");

		  System.out.println("Testing AZMP clients:");
		  assertDecodeAZMP("Azureus 3.0.4.2", "-AZ3042-6ozMq5q6Q3NX", "Azureus", "3.0.4.2");
		  assertDecodeAZMP("Azureus 3.0.4.3_B02", "-AZ3043-6ozMq5q6Q3NX", "Azureus", "3.0.4.3_B02");
		  assertDecodeAZMP("BitTyrant 2.5.0.0 (Azureus Mod)", "AZ2500BTeyuzyabAfo6U", "AzureusBitTyrant", "2.5.0.0BitTyrant");
		  //assertDecodeAZMP("", "-BS5820-oy4La2MWGEFj", "Bearshare Premium P2P", "5.8.2.0");
		  //assertDecodeAZMP("", "-AR6360-6oZyyMWoOOBe", "Imesh Turbo", "6.3.6.0");
		  assertDecodeAZMP("Azureus (Hacked) [FAKE: \"Azureus 2.4.0.2\" / \"Azureus 2.3.0.6\"]", "2D415A32 3430322D 2E414794 2C57D644 4989CA58", "Azureus", "2.3.0.6");
		  //assertDecodeAZMP("", "-AG2083-s1hiF8vGAAg0", "Ares", "2.0.8.3029");
		  //assertDecodeAZMP("", "-AG3003-lEl2Mm4NEO4n", "Ares Destiny", "3.0.0.3805");

		  System.out.println("");

		  System.out.println("Testing LTEP clients:");
		  assertDecodeLTEP("\u00B5Torrent 1.7.6", "2D555431 3736302D B39EC7AD F6B94610 AA4ACD4A", "\u00B5Torrent 1.7.6");
		  assertDecodeLTEP("\u00B5Torrent 1.6.1", "2D5554313631302DEA818D43F5E5EC3D67BF8D67", "\uFDFFTorrent 1.6.1");
		  assertDecodeLTEP("Unknown [FAKE: \"Azureus 3.0.4.2\"]", "-AZ3042-6ozMq5q6Q3NX", "Azureus 3.0.4.2");
		  assertDecodeLTEP("Mainline 6.0", "4D362D30 2D302D2D 8B92860D 05055DF5 B01C2D94", "BitTorrent 6.0");
		  //assertDecodeLTEP("libTorrent 0.11.9", "2D6C7430 4239302D 11F3EB39 5D44EEFD CEA07E79", "libTorrent 0.11.9");
		  assertDecodeLTEP("\u00B5Torrent 1.8.0 Beta", "2D555431 3830422D E69C9942 D1A5A6C2 0BE2E4BD", "\u00B5Torrent 1.8");
		  assertDecodeLTEP("Miro 1.1.0.0 (libtorrent/0.13.0.0)", "-MR1100-00HS~T7*65rm", "libtorrent/0.13.0.0");
		  assertDecodeLTEP("linkage/0.1.4 libtorrent/0.12.0.0", "-LK0140-ATIV~nbEQAMr", "linkage/0.1.4 libtorrent/0.12.0.0");
		  assertDecodeLTEP("KTorrent 2.2.2", "-KT2210-347143496631", "KTorrent 2.2.2");
		  //assertDecodeLTEP("", "B5546F72 72656E74 2F333037 36202020 20202020", "\uFDFFTorrent/3.0.7.6");
		  assertDecodeLTEP("Transmission 0.96", "-TR0960-6ep6svaa61r4", "Transmission 0.95");
		  assertDecodeLTEP("Opera 9.50", "O100634008270e29150a", "Opera 9.50");
		  System.out.println("");

		  System.out.println("Testing common clients:");
		  //assertDecodeExtProtocol("", "-XX1150-dv220cotgj4d", "Transmission", "0.72Z");
		  assertDecodeExtProtocol("XTorrent [FAKE: \"Azureus 2.5.0.4\" / \"Transmission 0.7-svn\"]", "-AZ2504-192gwethivju", "Transmission", "0.7-svn");
		  System.out.println("");

		  System.out.println("Done.");
	  }

}
