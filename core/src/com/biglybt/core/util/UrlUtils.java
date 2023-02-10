/*
 * Created on Mar 21, 2006 3:09:00 PM
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
package com.biglybt.core.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.*;

import org.gudy.bouncycastle.util.encoders.Base64;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.protocol.URLConnectionExt;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAnnounceURLList;
import com.biglybt.pif.torrent.TorrentAnnounceURLListSet;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourceuploader.ResourceUploader;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;
import com.biglybt.plugin.magnet.MagnetPlugin;


/**
 * @author TuxPaper
 * @created Mar 21, 2006
 *
 */
public class UrlUtils
{
	private static Pattern patMagnetSHA1HashFinder 		= Pattern.compile("(?i)xt=urn:(?:btih|sha1):([^&]+)");
	private static Pattern patMagnetMultiHashFinder 	= Pattern.compile("(?i)xt=urn:btmh:([^&]+)");

	private static final String[] prefixes = new String[] {
			"http://",
			"https://",
			"udp://",			// for trackers
			"biglybt://",
			"ftp://",
			"dht://",
			"magnet:?",
			"magnet://?",
			"maggot://" };

	private static final int MAGNETURL_STARTS_AT = 5;	// dht:// is a form of magnet URL

	private static final Object[] XMLescapes = new Object[] {
		new String[] { "&", "&amp;" },
		new String[] { ">", "&gt;" },
		new String[] { "<", "&lt;" },
		new String[] { "\"", "&quot;" },
		new String[] { "'", "&apos;" },
	};

	public static Map<String,String>
	decodeArgs(
		String	args )
	{
		Map<String,String>	result = new HashMap<>();

		String[] bits = (args.startsWith("?")?args.substring(1):args).split( "&" );

		for ( String bit: bits ){

			String[] temp = bit.split( "=", 2 );

			if ( temp.length == 2 ){

				String	lhs = temp[0].toLowerCase( Locale.US );

				String	rhs = decode( temp[1] );

				result.put( lhs, rhs );

			}else{

				result.put( "", decode( temp[0] ));
			}
		}

		return( result );
	}

	public static String
	getMagnetURI(
		byte[]		hash )
	{
		int	length = hash.length;
		
		if ( length == 32 ){
		
				// multi-hash prefix for sha256 is 0x1220
			
			return( "magnet:?xt=urn:btmh:" + "1220" + ByteFormatter.encodeString( hash ));
			
		}else{
			
			if (length != 20 ){
				
				Debug.out( "Invalid hash length " + length );
			}

				// switched this as apparently the standard is base16...
			
			//return( "magnet:?xt=urn:btih:" + Base32.encode( hash ));
			return( "magnet:?xt=urn:btih:" + ByteFormatter.encodeString( hash ).toLowerCase( Locale.US ));
		}
	}

	public static String
	getURLForm(
		InetSocketAddress	address )
	{
		return( AddressUtils.getHostAddressForURL(address) + ":" + address.getPort());
	}
	
	public static String
	getURLForm(
		InetAddress		address,
		int				port )
	{
		if ( address instanceof Inet6Address ){
			
			return( "[" + ( address.isLoopbackAddress()?"::1":address.getHostAddress()) + "]:" + port );
			
		}else{
			
			return(address.getHostAddress() + ":" + port );
		}
	}
	
	public static String
	getURLForm(
		String			address,
		int				port )
	{
		if ( address.indexOf( ':' ) != -1 ){
			
			return( "[" + address + "]:" + port );
			
		}else{
			
			return( address + ":" + port );
		}
	}
	
	public static String
	extractURLHost(
		String		str )
	{
		if ( str.startsWith( "[" )){
			
			int pos = str.indexOf( ']' );
			
			if ( pos != -1 ){
				
				return( str.substring( 0, pos+1 ));
			}
		}
		
		int pos = str.lastIndexOf( ':' );
		
		if ( pos == -1 ){
			
			return( str );
			
		}else{
			
			return( str.substring( 0, pos ));
		}
	}
		
	public static String
	getMagnetURI(
		byte[]		hash,
		String		name,
		String[]	networks )
	{
		String magnet_uri = getMagnetURI( hash );

		magnet_uri += encodeName( name );

		magnet_uri += encodeNetworks( networks );

		return( magnet_uri );
	}
	
	public static String
	getMagnetURI(
		byte[]		hash,
		byte[]		hash_v2,
		String		name,
		String[]	networks )
	{
		String magnet_uri = getMagnetURI( hash );

		if ( hash_v2 != null ){
			
			magnet_uri += "&xt=urn:btmh:" + "1220" + ByteFormatter.encodeString( hash_v2 );
		}

		magnet_uri += encodeName( name );

		magnet_uri += encodeNetworks( networks );

		return( magnet_uri );
	}

	private static String
	encodeName(
		String	name )
	{
		if ( name == null ){

			return( "" );

		}else{

			return( "&dn=" + UrlUtils.encode(name));
		}
	}

	private static String
	encodeNetworks(
		String[]	networks )
	{
		String	net_str = "";

		if ( networks != null && networks.length > 0 ){

			for ( String net: networks ){

				if ( net == AENetworkClassifier.AT_PUBLIC && networks.length == 1 ){

					break;
				}

				net_str += "&net=" + net;
			}
		}

		return( net_str );
	}

	public static Set<String>
	extractNetworks(
		String[]		magnet_uri )
	{
		String magnet_uri_in = magnet_uri[0];

		Set<String>	result = new HashSet<>();

		int	pos = magnet_uri_in.indexOf( '?' );

		if ( pos != -1 ){

			String magnet_uri_out = magnet_uri_in.substring( 0, pos+1 );

			String[] bits = magnet_uri_in.substring( pos+1 ).split( "&" );

			for ( String bit: bits ){

				String[] temp = bit.split( "=", 2 );

				boolean	remove = false;

				if ( temp.length == 2 ){

					String	lhs = temp[0];

					if ( lhs.equalsIgnoreCase( "net" )){

						String	rhs = decode( temp[1] );

						result.add( AENetworkClassifier.internalise( rhs ));

						remove = true;
					}
				}

				if ( !remove ){

					if ( !magnet_uri_out.endsWith( "?" )){

						magnet_uri_out += "&";
					}

					magnet_uri_out += bit;
				}
			}

			if ( result.size() > 0 ){

				magnet_uri[0] = magnet_uri_out;
			}
		}

		return( result );
	}

	public static String
	getMagnetURI(
		Download		download )
	{
		return( getMagnetURI( PluginCoreUtils.unwrap(download)));
	}

	public static String
	getMagnetURI(
		Download		download,
		int				max_name_len )
	{
		return( getMagnetURI( PluginCoreUtils.unwrap(download), max_name_len ));
	}

	public static String
	getMagnetURI(
		DownloadManager		dm )
	{
		return( getMagnetURI( dm, Integer.MAX_VALUE ));
	}

	public static String
	truncateForURI(
		String		str,
		int			max_utf8_bytes )
	{	
		if ( max_utf8_bytes == Integer.MAX_VALUE ){
			
			return( str );
		}
		
		boolean truncated = false;
		
			// assume min size for simplicity, only an idiot would truncate below this...
		
		while( str.length() > 4 ){
			
			int enc_len = UrlUtils.encode( str ).getBytes( Constants.UTF_8 ).length;
		
			if ( enc_len > max_utf8_bytes ){
				
				if ( !truncated ){
				
					truncated = true;
					
					max_utf8_bytes -= 3;	// room for the ...
				}
				
				str = str.substring( 0, str.length() - 1 );
				
				if ( Character.isHighSurrogate( str.charAt( str.length()-1 ))){
					
					str = str.substring( 0, str.length() - 1 );
				}
			}else{
				
				break;
			}
		}
		
		if ( truncated ){
			
			str = str + "...";
		}
		
		return( str );
	}
	
	public static String
	getMagnetURI(
		DownloadManager		dm,
		int					max_name_len )
	{
		if ( dm == null ){

			return( null );
		}

		TOTorrent to_torrent = dm.getTorrent();

		if ( to_torrent == null ){

			return( null );
		}

		String name = truncateForURI( dm.getDisplayName(), max_name_len );

		String[]	networks = dm.getDownloadState().getNetworks();

		String magnet_uri = getMagnetURI( name, PluginCoreUtils.wrap( to_torrent ), networks );

		return( magnet_uri );
	}

	public static String
	getMagnetURI(
		TOTorrent		to_torrent )
	{		
		return( getMagnetURI( new TorrentImpl( to_torrent )));
	}

	public static String
	getMagnetURI(
		Torrent		torrent )
	{
		return( getMagnetURI( torrent.getName(), torrent, null ));
	}
	
	public static String
	getMagnetURI(
		String		name,
		Torrent		torrent )
	{
		return( getMagnetURI( name, torrent, null ));
	}
	
	public static String
	getMagnetURI(
		String		name,
		Torrent		torrent,
		String[]	networks )
	{
		byte[] v1_hash = torrent.getFullHash( TOTorrent.TT_V1 );
		
		if ( v1_hash == null ){
			
			v1_hash = torrent.getHash();	// this'll be the truncated v2 hash
		}
		
		String	magnet_str = getMagnetURI( v1_hash );

		byte[] v2_hash = torrent.getFullHash( TOTorrent.TT_V2 );
		
		if ( v2_hash != null ){
			
			magnet_str += "&xt=urn:btmh:" + "1220" + ByteFormatter.encodeString( v2_hash );
		}
		
		magnet_str += encodeName( name);

		if ( networks != null ) {
		
			magnet_str += encodeNetworks( networks );
		}
		
		List<String>	tracker_urls = new ArrayList<>();

		URL announce_url = torrent.getAnnounceURL();

		if ( announce_url != null ){

			if ( !TorrentUtils.isDecentralised( announce_url )){

				tracker_urls.add( announce_url.toExternalForm());
			}
		}

		TorrentAnnounceURLList list = torrent.getAnnounceURLList();

		TorrentAnnounceURLListSet[] sets = list.getSets();

		for ( TorrentAnnounceURLListSet set: sets ){

			URL[] set_urls = set.getURLs();

			if ( set_urls.length > 0 ){

				URL set_url = set_urls[0];

				if ( !TorrentUtils.isDecentralised( set_url )){

					String str = set_url.toExternalForm();

					if ( !tracker_urls.contains( str )){

						tracker_urls.add( str );
					}
				}
			}
		}

		for ( String str: tracker_urls ){

			magnet_str += "&tr=" + UrlUtils.encode( str );
		}

		List<String>	ws_urls = new ArrayList<>();

		Object obj = torrent.getAdditionalProperty( "url-list" );

		if ( obj instanceof byte[] ){

			try{
				ws_urls.add( new URL( new String((byte[])obj, "UTF-8" )).toExternalForm());

			}catch( Throwable e ){
			}
		}else if ( obj instanceof List ){

			for ( Object o: (List)obj ){

				try{
					if (o instanceof byte[]) {
						ws_urls.add( new URL( new String((byte[])o, "UTF-8" )).toExternalForm());
					} else if (o instanceof String) {
						ws_urls.add( new URL((String) o).toExternalForm());
					}

				}catch( Throwable e ){
				}
			}
		} else if ( obj instanceof String ) {
			try{
				ws_urls.add(new URL((String) obj).toExternalForm());
			}catch( Throwable e ){
			}
		}

		for ( String str: ws_urls ){

			magnet_str += "&ws=" + UrlUtils.encode( str );
		}

		return( magnet_str );
	}
	
	public static String
	addSource(
		Download			download,
		String				magnet,
		InetSocketAddress	address )
	{
		if ( address != null ){

			MagnetPlugin magnet_plugin = (MagnetPlugin)CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass( MagnetPlugin.class ).getPlugin();

			if ( magnet_plugin != null ){
				
				magnet = magnet_plugin.addSource( download, magnet, address );
			}
		}
		
		return( magnet );
	}
	
		/**
		 * returns magnet uri if input is base 32 or base 16 encoded sha1 or sha256 hash, null otherwise
		 * @param base_hash
		 * @return
		 */

	public static String
	normaliseMagnetURI(
		String		base_hash )
	{
		byte[]	hash = decodeSHA1Hash( base_hash );

		if ( hash != null ){

			return( getMagnetURI( hash ));
		}
		
		hash = decodeMultiHash( base_hash );

		if ( hash != null ){

			return( getMagnetURI( hash ));
		}

		return( null );
	}

		/**
		 * decodes a sha1 or sha256 multihash from an actual magnet URI and truncates to 20 bytes
		 * @param hash_str
		 * @return
		 */
	
	public static byte[] 
	getTruncatedHashFromMagnetURI(
		String magnetURI) 
	{
		if ( magnetURI == null ){
			
			return null;
		}
		
		Matcher matcher = patMagnetSHA1HashFinder.matcher(magnetURI);
		
		if ( matcher.find()){
			
			return( decodeSHA1Hash(matcher.group(1)));
		}
		
		matcher = patMagnetMultiHashFinder.matcher(magnetURI);
		
		if ( matcher.find()){
			
			return( decodeTruncatedMultiHash( matcher.group(1)));
		}
		
		return null;
	}

		/**
		 * decodes a sha1 or sha256 multihash as found in a magnet URI and truncates to 20 bytes
		 * @param hash_str
		 * @return
		 */
	
	public static byte[] 
	decodeTruncatedHashFromMagnetURI(
		String hash_str ) 
	{
		if ( hash_str == null ){
			
			return( null );
		}
		
		byte[] hash = decodeSHA1Hash( hash_str );
		
		if ( hash == null ){
			
			hash = decodeTruncatedMultiHash( hash_str );
		}
		
		return( hash );
	}
		
	private static byte[] 
	decodeTruncatedMultiHash(
		String 	hash_str )
	{
		byte[] hash = decodeMultiHash( hash_str );
		
		if ( hash != null ){
			
			if ( hash.length > 20 ){
				
				byte[] trunc = new byte[20];
			
				System.arraycopy( hash, 0, trunc, 0, 20 );
			
				return( trunc );
			}
		}
		
		return( hash );
	}
	
		/**
		 * Decodes a hex or base32 encoded sha1, sha256 or multihash
		 * @param hash_str
		 * @return
		 */
	
	public static byte[]
	decodeTruncatedHash(
		String		hash_str )
	{
		byte[] hash = decodeTruncatedHashFromMagnetURI( hash_str );
		
		if ( hash == null ){
			
			hash = decodeSHA256Hash( hash_str );
			
			if ( hash != null ){
				
				byte[] trunc = new byte[20];
			
				System.arraycopy( hash, 0, trunc, 0, 20 );
			
				return( trunc );
			}
		}
		
		return( hash );
	}
	
	public static byte[]
	decodeSHA1Hash(
		String	str )
	{
		if ( str == null ){

			return( null );
		}

		str = str.trim();

		byte[] hash = null;

		try{
			if ( str.length() == 40 ){

				hash = ByteFormatter.decodeString( str );

			}else if ( str.length() == 32 ){

				hash = Base32.decode( str );
			}
		}catch( Throwable e ){
		}

		if ( hash != null ){

			if ( hash.length != 20 ){

				hash = null;
			}
		}

		return( hash );
	}

	private static byte[]
	decodeSHA256Hash(
		String	str )
	{
		if ( str == null ){

			return( null );
		}

		str = str.trim();

		byte[] hash = null;

		try{
			if ( str.length() == 64 ){

				hash = ByteFormatter.decodeString( str );

			}else if ( str.length() == 52 ){

				hash = Base32.decode( str );
			}
		}catch( Throwable e ){
		}

		if ( hash != null ){

			if ( hash.length != 32 ){

				hash = null;
			}
		}

		return( hash );
	}
	
	private static byte[]
	decodeMultiHash(
		String	str )
	{
		if ( str == null ){

			return( null );
		}

		str = str.trim();

		byte[] hash = null;

		try{
			int len = str.length();
			
			byte[] multi_hash;
			
				// support both sha1 and sha256 multi-hash formats (2-byte hash type prefix adds 4 chars to string)
			
			if ( len == 4 + 40 || len == 4 + 64 ){

				multi_hash = ByteFormatter.decodeString( str );

					// prefix extends size in base32 by 4 chars for sha1 and 3 for sha256
				
			}else if ( len == 4 + 32 || len == 3 + 52 ){

				multi_hash = Base32.decode( str );
				
			}else{
				
				return( null );
			}
			
			if ( multi_hash[0] == 0x11 && multi_hash[1] == 0x14 && multi_hash.length == 2 + 20 ){
					
				hash = new byte[20];
					
				System.arraycopy( multi_hash, 2, hash, 0, 20 );
					
			}else if ( multi_hash[0] == 0x12 && multi_hash[1] == 0x20 && multi_hash.length == 2 + 32 ){
			
				hash = new byte[32];
				
				System.arraycopy( multi_hash, 2, hash, 0, 32 );
			}
		}catch( Throwable e ){
		}

		return( hash );
	}
	
	public static URL
	getRawURL(
		String		url )
	{
		if ( url.endsWith( "]]" )){
			
			int pos = url.lastIndexOf( "[[" );
			
			if ( pos != -1 ){
		
				url = url.substring( 0, pos );
			}
		}
		
		try{
			URL u = new URL( url );
			
			String protocol = u.getProtocol();
			
			if ( protocol != null && !protocol.isEmpty()){
				
				return( u );
			}
			
		}catch( Throwable e ){
		}
		
		return( null );
	}
	
	public static String
	getFriendlyName(
		URL			url,
		String		url_str )
	{
		if ( url_str.endsWith( "]]" )){
			
			int pos = url_str.lastIndexOf( "[[" );
			
			if ( pos != -1 ){
		
				return( decode( url_str.substring( pos + 2, url_str.length() - 2 )));
			}
		}

		try{
			URLConnection con = url.openConnection();
			
			try{
				if ( con instanceof URLConnectionExt ){
					
					return(((URLConnectionExt)con).getFriendlyName());
				}
			}finally{
				
				if ( con instanceof HttpURLConnection ){
					
					try{
						((HttpURLConnection)con).disconnect();
						
					}catch( Throwable e ){
					}
				}
			}
		}catch( Throwable e ){
		}
		
		return( url.toExternalForm());
	}
	
	/**
	 * test string for possibility that it's an URL.  Considers 40 byte hex
	 * strings as URLs
	 *
	 * @param sURL
	 * @return
	 */
	public static boolean isURL(String sURL) {
		return parseTextForURL(sURL, true) != null;
	}

	public static boolean isURL(String sURL, boolean bGuess) {
		return parseTextForURL(sURL, true, bGuess) != null;
	}

	public static String parseTextForURL(String text, boolean accept_magnets) {
		return parseTextForURL(text, accept_magnets, true);
	}

	public static String
	getURL(
		String	text )
	{
		return( parseTextForURL(text, false, false ));
	}

	public static boolean
	isInternalProtocol(
		String		url )
	{
		url = url.toLowerCase();

		return(
			url.startsWith( "magnet:" ) ||
			url.startsWith( "chat:") ||
			url.startsWith( "azplug:") ||
			url.startsWith( "vuze:") ||
			url.startsWith( "biglybt:") ||
			url.startsWith( "tor:" ) ||
			url.startsWith( "i2p:" ));
	}

	public static String parseTextForURL(String text, boolean accept_magnets,
			boolean guess) {

		if (text == null || text.length() < 5) {
			return null;
		}

		text = text.trim();

		if ( text.startsWith( "azplug:" )){

			return( text );
		}

		if ( text.startsWith( "chat:" )){

			return( "azplug:?id=azbuddy&arg=" + UrlUtils.encode( text ));
		}

		if ( text.startsWith( "tor:" )){

			String href = parseTextForURL(text.substring(4), false, false );
			if (href != null) {
				return( "tor:" + href );
			}
		}

		if ( text.startsWith( "i2p:" )){

			String href = parseTextForURL(text.substring(4), false, false );
			if (href != null) {
				return( "i2p:" + href );
			}
		}

		String href = parseHTMLforURL(text);
		if (href != null) {
			return href;
		}

		try {
			text = text.trim();
			text = decodeIfNeeded(text);
		} catch (Exception e) {
			// sometimes fires a IllegalArgumentException
			// catch everything and ignore.
		}

		String textLower;
		try {
			textLower = text.toLowerCase();
		} catch (Throwable e) {
			textLower = text;
		}
		int max = accept_magnets ? prefixes.length : MAGNETURL_STARTS_AT;
		int end = -1;
		int start = textLower.length();
		String strURL = null;
		for (int i = 0; i < max; i++) {
			final int testBegin = textLower.indexOf(prefixes[i]);
			if (testBegin >= 0 && testBegin < start) {
				end = text.indexOf("\n", testBegin + prefixes[i].length());
				String strURLTest = (end >= 0) ? text.substring(testBegin, end)
						: text.substring(testBegin);
				try {
					URL parsedURL = new URL(strURLTest);
					strURL = parsedURL.toExternalForm();
				} catch (MalformedURLException e1) {
					e1.printStackTrace();
					if (i >= MAGNETURL_STARTS_AT) {
						strURL = strURLTest;
					}
				}
			}
		}
		if ( strURL != null ){
			
				// we're never going to want raw whitespace in the url, even though new URL(...) accepts them...
			
			char[] chars = strURL.toCharArray();
			
			for ( int i=0;i<chars.length;i++){
				
				if ( Character.isWhitespace( chars[i] )){
					
					strURL = strURL.substring( 0, i );
					
					break;
				}
			}
			return strURL;
		}

		if (FileUtil.newFile(text).exists()) {
			return null;
		}

			// be lenient for raw anon addresses

		try{
			URL u = new URL( "http://" + text );

			String host = u.getHost();

			if ( host != null && AENetworkClassifier.categoriseAddress( host ) != AENetworkClassifier.AT_PUBLIC ){

				return( u.toExternalForm());
			}
		}catch( Throwable e ){
		}

		if (accept_magnets
				&& (text.startsWith("bc://") || text.startsWith("bctp://"))) {
			return parseTextForMagnets(text);
		}

			// hack to support appending args to raw hashes

		String text_prefix = text;
		String text_suffix = "";

		int a_pos = text_prefix.indexOf( '?' );
		if ( a_pos == -1 ){
			a_pos = text_prefix.indexOf( '&' );
		}
		if ( a_pos != -1 ){
			String args = text_prefix.substring( a_pos+1 ).trim();
			if ( args.contains( "=" )){
				int s_pos = args.indexOf(' ');
				if ( s_pos != -1 ){
					args = args.substring( 0, s_pos );
				}
				text_prefix = text_prefix.substring( 0, a_pos );
				text_suffix = "&" + args;
			}
		}

		// accept raw hash of 40 hex chars
		if (accept_magnets ){

			for ( int i=0;i<2;i++){
				
				int hex_len = i==0?40:64;		// sha1/sha256
					
				if ( text_prefix.matches("^[a-fA-F0-9]{"+hex_len+"}$")) {
	
					// convert from HEX to raw bytes
					byte[] infohash = ByteFormatter.decodeString(text_prefix.toUpperCase());
					// convert to BASE32
					return( getMagnetURI(infohash) + text_suffix );
				}
	
				String temp_text = text_prefix.replaceAll( "\\s+", "" );
	
				if ( temp_text.matches("^[a-fA-F0-9]{"+hex_len+"}$")) {
	
					// convert from HEX to raw bytes
					byte[] infohash = ByteFormatter.decodeString(temp_text.toUpperCase());
					// convert to BASE32
					return( getMagnetURI(infohash) + text_suffix );
				}
			}
	
			for ( int i=0;i<2;i++){
				
				int b32_len	= i==0?32:52;	// sha1/sha256

				// accept raw hash of 32 base-32 chars
				if ( text_prefix.matches("^[a-zA-Z2-7]{" + b32_len + "}$")) {
					return( getMagnetURI(Base32.decode(text_prefix)) + text_suffix );
				}
			}
			
			if ( guess ){
				
				for ( int i=0;i<2;i++){
	
					int hex_len = i==0?40:64;		// sha1/sha256
					int b32_len	= i==0?32:52;		// sha1/sha256

						// accept raw hash of 32 base-32 chars, with garbage around it
				
					Pattern pattern = Pattern.compile("[^a-zA-Z2-7]([a-zA-Z2-7]{" + b32_len + "})[^a-zA-Z2-7]");
					Matcher matcher = pattern.matcher(text);
					if (matcher.find()) {
						String infohash = matcher.group(1);
						return( getMagnetURI(Base32.decode(infohash)));
					}
		
					pattern = Pattern.compile("[^a-fA-F0-9]([a-fA-F0-9]{" + hex_len + "})[^a-fA-F0-9]");
					matcher = pattern.matcher(text);
					if (matcher.find()) {
						String hash = matcher.group(1);
						// convert from HEX to raw bytes
						byte[] infohash = ByteFormatter.decodeString(hash.toUpperCase());
						// convert to BASE32
						return( getMagnetURI(infohash) );
					}
				}
			}
		}

		return null;
	}

	public static String
	parseTextForMagnets(
		String		text )
	{
		return( parseTextForMagnets( text, true ));
	}
	
	public static String
	parseTextForMagnets(
		String		text,
		boolean		check_raw_embeded_hashes )
	{
		if (text.startsWith("magnet:") || text.startsWith( "maggot:" )){
			return text;
		}

		for ( int i=0;i<2;i++){
			
			int hex_len = i==0?40:64;		// sha1/sha256
			int b32_len	= i==0?32:52;		// sha1/sha256

			// accept raw hash of 40 hex chars
			if (text.matches("^[a-fA-F0-9]{" + hex_len + "}$")) {
				// convert from HEX to raw bytes
				byte[] infohash = ByteFormatter.decodeString(text.toUpperCase());
				// convert to BASE32
				return( getMagnetURI(infohash));
			}
	
			String temp_text = text.replaceAll( "\\s+", "" );
			if (temp_text.matches("^[a-fA-F0-9]{" + hex_len + "}$")) {
				// convert from HEX to raw bytes
				byte[] infohash = ByteFormatter.decodeString(temp_text.toUpperCase());
				// convert to BASE32
				return( getMagnetURI(infohash));
			}
	
			// accept raw hash of 32 base-32 chars
			if (text.matches("^[a-zA-Z2-7]{" + b32_len + "}$")) {
				return( getMagnetURI(Base32.decode(text)));
			}
		}
		
		Pattern pattern;
		Matcher matcher;

		pattern = Pattern.compile("magnet:\\?[a-z%0-9=_:&.]+", Pattern.CASE_INSENSITIVE);
		matcher = pattern.matcher(text);
		if (matcher.find()) {
			return matcher.group();
		}

		pattern = Pattern.compile("maggot://[a-z0-9]+:[a-z0-9]", Pattern.CASE_INSENSITIVE);
		matcher = pattern.matcher(text);
		if (matcher.find()) {
			return matcher.group();
		}

		pattern = Pattern.compile("bc://bt/([a-z0-9=\\+/]+)", Pattern.CASE_INSENSITIVE);
		matcher = pattern.matcher(text.replaceAll(" ", "+"));
		if (matcher.find()) {
			String base64 = matcher.group(1);
			byte[] decode = Base64.decode(base64);
			if (decode != null && decode.length > 0) {
				// Format is AA/<name>/<size>/<hash>/ZZ
				try {
					String decodeString = new String(decode, "utf8");
					pattern = Pattern.compile("AA.*/(.*)/ZZ", Pattern.CASE_INSENSITIVE);
					matcher = pattern.matcher(decodeString);
					if (matcher.find()) {
						String hash = matcher.group(1);
						String magnet = parseTextForMagnets(hash);
						if (magnet != null) {
							pattern = Pattern.compile("AA/(.*)/[0-9]+", Pattern.CASE_INSENSITIVE);
							matcher = pattern.matcher(decodeString);
							if (matcher.find()) {
								String name = matcher.group(1);
								return magnet + "&dn=" + encode(name);
							}
							return magnet;
						}
					}
				} catch (UnsupportedEncodingException e) {
				}
			}
		}

		pattern = Pattern.compile("bctp://task/(.*)", Pattern.CASE_INSENSITIVE);
		matcher = pattern.matcher(text);
		if (matcher.find()) {
			// Format is <name>/<size>/<hash>
			String decodeString = matcher.group(1);
			String magnet = parseTextForMagnets(decodeString);
			if (magnet != null) {
				pattern = Pattern.compile("(.*)/[0-9]+", Pattern.CASE_INSENSITIVE);
				matcher = pattern.matcher(decodeString);
				if (matcher.find()) {
					String name = matcher.group(1);
					return magnet + "&dn=" + encode(name);
				}
				return magnet;
			}
		}

		if ( check_raw_embeded_hashes ){
			
			// accept raw hash of 32 base-32 chars, with garbage around it
			
			for ( int i=0;i<2;i++){
				
				int hex_len = i==0?40:64;		// sha1/sha256
				int b32_len	= i==0?32:52;		// sha1/sha256
	
				pattern = Pattern.compile("[^a-zA-Z2-7]([a-zA-Z2-7]{" + b32_len + "})[^a-zA-Z2-7]");
				matcher = pattern.matcher(text);
				if (matcher.find()) {
					String hash = matcher.group(1);
					return( getMagnetURI(Base32.decode(hash)));
				}
	
				pattern = Pattern.compile("[^a-fA-F0-9]([a-fA-F0-9]{" + hex_len + "})[^a-fA-F0-9]");
				matcher = pattern.matcher(text);
				if (matcher.find()) {
					String hash = matcher.group(1);
					// convert from HEX to raw bytes
					byte[] infohash = ByteFormatter.decodeString(hash.toUpperCase());
					// convert to BASE32
					return( getMagnetURI(infohash));
				}
			}
		}

		return( null );
	}

	private static String parseHTMLforURL(String text) {
		if (text == null) {
			return null;
		}

		// examples:
		// <A HREF=http://abc.om/moo>test</a>
		// <A style=cow HREF="http://abc.om/moo">test</a>
		// <a href="http://www.gnu.org/licenses/fdl.html" target="_top">moo</a>

		Pattern pat = Pattern.compile("<.*a\\s++.*href=\"?([^\\'\"\\s>]++).*",
				Pattern.CASE_INSENSITIVE);
		Matcher m = pat.matcher(text);
		if (m.find()) {
			String sURL = m.group(1);
			try {
				sURL = decodeIfNeeded(sURL);
			} catch (Exception e) {
				// sometimes fires a IllegalArgumentException
				// catch everything and ignore.
			}
			return sURL;
		}

		return null;
	}



	/**
	 * Like URLEncoder.encode, except translates spaces into %20 instead of +
	 * @param s
	 * @return
	 */
	public static String encode(String s) {
		if (s == null) {
			return "";
		}
		try {
			return URLEncoder.encode(s, "UTF-8").replaceAll("\\+", "%20");
		} catch (UnsupportedEncodingException e) {
			Debug.out(e);
			return(s);
		}
	}

	public static String decode(String s) {
		if (s == null) {
			return "";
		}
		try {
			try{
				return( URLDecoder.decode(s, "UTF-8"));

			}catch( IllegalArgumentException e ){

					// handle truncated encodings somewhat gracefully

				int pos = s.lastIndexOf( "%" );

				if ( pos >= s.length()-2 ){

					return( URLDecoder.decode(s.substring( 0, pos ), "UTF-8"));
				}

				throw( e );
			}
		} catch (UnsupportedEncodingException e) {

			Debug.out(e);
			
			return( s );
		}
	}

	/**
	 * Unfortunately we have code that mindlessly decoded URLs (FileDownloadWindow) which borked (in the case I discovered) magnet uris with encoded
	 * parameters (e.g. the &tr= parameter) - doing so screws stuff up later if, for example, the parameter included an encoded '&'
	 * @param s
	 * @return
	 */

	public static String
	decodeIfNeeded(
		String s )
	{
		if ( s == null ){

			return( "" );
		}

		try{
				// of course someone prolly added the blind URLDecode for a reason so try and just deal with the borkage
				// which means looking for &a=b elements and ensure we don't URLDecode the 'b'

				// only have issues if there's a naked '?' there

			int	q_pos = s.indexOf( '?' );
			int a_pos = s.indexOf( '&' );

			if ( q_pos == -1 && a_pos == -1 ){

				return( decode( s ));
			}

			int start = Math.min( q_pos, a_pos );

			return( decode( s.substring( 0, start )) + s.substring( start ));

		}catch( Throwable e ){

			return( s );
		}
	}

	public static String escapeXML(String s) {
		if (s == null) {
			return "";
		}
		String ret = s;
		for (int i = 0; i < XMLescapes.length; i++) {
			String[] escapeEntry = (String[])XMLescapes[i];
			ret = ret.replaceAll(escapeEntry[0], escapeEntry[1]);
		}
		return ret;
	}

	public static String unescapeXML(String s) {
		if (s == null) {
			return "";
		}
		String ret = s;
		for (int i = 0; i < XMLescapes.length; i++) {
			String[] escapeEntry = (String[])XMLescapes[i];
			ret = ret.replaceAll(escapeEntry[1], escapeEntry[0]);
		}
		return ret;
	}

	public static String
	convertIPV6Host(
		String	host )
	{
		if ( host.indexOf(':') != -1 ){

			int	zone_index = host.indexOf( '%' );

			if ( zone_index != -1 ){

				host = host.substring( 0, zone_index ) + encode( host.substring( zone_index ));
			}

			return( "[" + host + "]" );
		}

		return( host );
	}

	public static String
	expandIPV6Host(
		String	host )
	{
		if ( host.indexOf(':') != -1 ){

			try{
				return( InetAddress.getByAddress(InetAddress.getByName( host ).getAddress()).getHostAddress());

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		return( host );
	}

	public static String
	resolveIPv6Host(
		String		url )
	{
		try{
			DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();

			URL http_server_url = new URL( url );

			return( setHost( http_server_url, "[" + dns_utils.getIPV6ByName( http_server_url.getHost()).getHostAddress() + "]").toExternalForm());
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( url );
		}
	}
	
	public static String
	resolveIPv4Host(
		String		url )
	{
		try{
			DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();

			URL http_server_url = new URL( url );

			return( setHost( http_server_url, dns_utils.getIPV4ByName( http_server_url.getHost()).getHostAddress()).toExternalForm());
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( url );
		}
	}
	
	public static void
	connectWithTimeout(
		final URLConnection		connection,
		long					connect_timeout )

		throws IOException
	{
		connectWithTimeouts( connection, connect_timeout, -1 );
	}

	public static void
	connectWithTimeouts(
		final URLConnection		connection,
		long					connect_timeout,
		long					read_timeout )

		throws IOException
	{
		if ( connect_timeout != -1 ){

			connection.setConnectTimeout( (int)connect_timeout );
		}

		if ( read_timeout != -1 ){

			connection.setReadTimeout( (int)read_timeout );
		}

		connection.connect();
	}

	private static String	last_headers = COConfigurationManager.getStringParameter( "metasearch.web.last.headers", null );

	// private static final String default_headers = "SG9zdDogbG9jYWxob3N0OjQ1MTAwClVzZXItQWdlbnQ6IE1vemlsbGEvNS4wIChXaW5kb3dzOyBVOyBXaW5kb3dzIE5UIDUuMTsgZW4tVVM7IHJ2OjEuOC4xLjE0KSBHZWNrby8yMDA4MDQwNCBGaXJlZm94LzIuMC4wLjE0CkFjY2VwdDogdGV4dC94bWwsYXBwbGljYXRpb24veG1sLGFwcGxpY2F0aW9uL3hodG1sK3htbCx0ZXh0L2h0bWw7cT0wLjksdGV4dC9wbGFpbjtxPTAuOCxpbWFnZS9wbmcsKi8qO3E9MC41CkFjY2VwdC1MYW5ndWFnZTogZW4tdXMsZW47cT0wLjUKQWNjZXB0LUVuY29kaW5nOiBnemlwLGRlZmxhdGUKQWNjZXB0LUNoYXJzZXQ6IElTTy04ODU5LTEsdXRmLTg7cT0wLjcsKjtxPTAuNwpLZWVwLUFsaXZlOiAzMDAKQ29ubmVjdGlvbjoga2VlcC1hbGl2ZQ==";
	private static final String default_headers = "QWNjZXB0OiB0ZXh0L2h0bWwsYXBwbGljYXRpb24veGh0bWwreG1sLGFwcGxpY2F0aW9uL3htbDtxPTAuOSwqLyo7cT0wLjgKQWNjZXB0LUNoYXJzZXQ6IElTTy04ODU5LTEsdXRmLTg7cT0wLjcsKjtxPTAuMwpBY2NlcHQtRW5jb2Rpbmc6IGd6aXAsZGVmbGF0ZQpBY2NlcHQtTGFuZ3VhZ2U6IGVuLVVTLGVuO3E9MC44CkNhY2hlLUNvbnRyb2w6IG1heC1hZ2U9MApDb25uZWN0aW9uOiBrZWVwLWFsaXZlClVzZXItQWdlbnQ6IE1vemlsbGEvNS4wIChXaW5kb3dzIE5UIDYuMTsgV09XNjQpIEFwcGxlV2ViS2l0LzUzNi4xMSAoS0hUTUwsIGxpa2UgR2Vja28pIENocm9tZS8yMC4wLjExMzIuNDcgU2FmYXJpLzUzNi4xMQ==";

	public static void
	setBrowserHeaders(
		ResourceDownloader		rd,
		String					referer )
	{
		setBrowserHeaders( rd, null, referer );
	}

	public static void
	setBrowserHeaders(
		ResourceDownloader		rd,
		String					encoded_headers,
		String					referer )
	{
		String	headers_to_use = getBrowserHeadersToUse( encoded_headers );

		try{
			String header_string = new String( Base64.decode( headers_to_use ), "UTF-8" );

			String[]	headers = header_string.split( "\n" );

			for (int i=0;i<headers.length;i++ ){

				String	header = headers[i];

				int	pos = header.indexOf( ':' );

				if ( pos != -1 ){

					String	lhs = header.substring(0,pos).trim();
					String	rhs	= header.substring(pos+1).trim();

					if ( !( lhs.equalsIgnoreCase( "Host") ||
							lhs.equalsIgnoreCase( "Referer" ))){

						rd.setProperty( "URL_" + lhs, rhs );
					}
				}
			}

			if ( referer != null && referer.length() > 0 ){

				rd.setProperty( "URL_Referer", referer );
			}
		}catch( Throwable e ){
		}
	}

	public static void
	setBrowserHeaders(
		ResourceUploader		ru,
		String					encoded_headers,
		String					referer )
	{
		String	headers_to_use = getBrowserHeadersToUse( encoded_headers );

		try{
			String header_string = new String( Base64.decode( headers_to_use ), "UTF-8" );

			String[]	headers = header_string.split( "\n" );

			for (int i=0;i<headers.length;i++ ){

				String	header = headers[i];

				int	pos = header.indexOf( ':' );

				if ( pos != -1 ){

					String	lhs = header.substring(0,pos).trim();
					String	rhs	= header.substring(pos+1).trim();

					if ( !( lhs.equalsIgnoreCase( "Host") ||
							lhs.equalsIgnoreCase( "Referer" ))){

						ru.setProperty( "URL_" + lhs, rhs );
					}
				}
			}

			if ( referer != null && referer.length() > 0 ){

				ru.setProperty( "URL_Referer", referer );
			}
		}catch( Throwable e ){
		}
	}

	public static void
	setBrowserHeaders(
		URLConnection			connection,
		String					referer )
	{
		setBrowserHeaders( connection, null, referer );
	}

	public static void
	setBrowserHeaders(
		URLConnection			connection,
		String					encoded_headers,
		String					referer )
	{
		String	headers_to_use = getBrowserHeadersToUse( encoded_headers );

		try{

			String header_string = new String( Base64.decode( headers_to_use ), "UTF-8" );

			String[]	headers = header_string.split( "\n" );

			for (int i=0;i<headers.length;i++ ){

				String	header = headers[i];

				int	pos = header.indexOf( ':' );

				if ( pos != -1 ){

					String	lhs = header.substring(0,pos).trim();
					String	rhs	= header.substring(pos+1).trim();

					if ( !( lhs.equalsIgnoreCase( "Host") ||
							lhs.equalsIgnoreCase( "Referer" ))){

						connection.setRequestProperty( lhs, rhs );
					}
				}
			}

			if ( referer != null && referer.length() > 0 ){

				connection.setRequestProperty( "Referer", referer );
			}
		}catch( Throwable e ){
		}
	}

	public static Map
	getBrowserHeaders(
		String					referer )
	{
		String	headers_to_use = getBrowserHeadersToUse( null );

		Map	result = new HashMap();

		try{

			String header_string = new String( Base64.decode( headers_to_use ), "UTF-8" );

			String[]	headers = header_string.split( "\n" );

			for (int i=0;i<headers.length;i++ ){

				String	header = headers[i];

				int	pos = header.indexOf( ':' );

				if ( pos != -1 ){

					String	lhs = header.substring(0,pos).trim();
					String	rhs	= header.substring(pos+1).trim();

					if ( !( lhs.equalsIgnoreCase( "Host") ||
							lhs.equalsIgnoreCase( "Referer" ))){

						result.put( lhs, rhs );
					}
				}
			}

			if ( referer != null && referer.length() > 0){

				result.put( "Referer", referer );
			}
		}catch( Throwable e ){
		}

		return( result );
	}

	private static String
	getBrowserHeadersToUse(
		String		encoded_headers )
	{
		String	headers_to_use = encoded_headers;

		synchronized( UrlUtils.class ){

			if ( headers_to_use == null ){

				if ( last_headers != null ){

					headers_to_use = last_headers;

				}else{

					headers_to_use = default_headers;
				}
			}else{

				if ( last_headers == null || !headers_to_use.equals( last_headers )){

					COConfigurationManager.setParameter( "metasearch.web.last.headers", headers_to_use );
				}

				last_headers = headers_to_use;
			}
		}

		return( headers_to_use );
	}

	public static boolean queryHasParameter(String query_string, String param_name, boolean case_sensitive) {
		if (!case_sensitive) {
			query_string = query_string.toLowerCase();
			param_name = param_name.toLowerCase();
		}
		if (query_string.charAt(0) == '?') {
			query_string = '&' + query_string.substring(1);
		}
		else if (query_string.charAt(0) != '&') {
			query_string = '&' + query_string;
		}

		return query_string.contains("&" + param_name + "=");
	}

	public static boolean
	containsPasskey(
		URL		url )
	{
		if ( url == null ){

			return( false );
		}

		String url_str = url.toExternalForm();

		return( url_str.matches(".*[0-9a-z]{20,40}.*"));
	}

	public static URL
	setPort(
		URL		u,
		int		port )
	{
		if ( port == -1 ){
			port = u.getDefaultPort();
		}
		StringBuilder result = new StringBuilder();
		result.append(u.getProtocol());
		result.append(":");
		String authority=u.getAuthority();
		if (authority != null && authority.length() > 0) {
			result.append("//");
			int pos = authority.indexOf( '@' );
			if ( pos != -1 ){
				result.append(authority.substring(0,pos+1));
				authority = authority.substring(pos+1);
			}
			pos = authority.lastIndexOf(':');
			if ( pos == -1 ){
				if ( port > 0 ){
					result.append(authority).append(":").append(port);
				}else{
					result.append(authority);
				}
			}else{
				if ( port > 0 ){
					result.append(authority.substring(0, pos + 1)).append(port);
				}else{
					result.append(authority.substring(0,pos));
				}
			}
		}
		if (u.getPath() != null) {
			result.append(u.getPath());
		}
		if (u.getQuery() != null) {
			result.append('?');
			result.append(u.getQuery());
		}
		if (u.getRef() != null) {
			result.append("#");
			result.append(u.getRef());
		}
		try{
			return( new URL( result.toString()));
		}catch( Throwable e ){
			Debug.out(e);
			return(u);
		}
	}

	public static URL
	setHost(
		URL			u,
		String		host )
	{
		StringBuilder result = new StringBuilder();
		result.append(u.getProtocol());
		result.append(":");
		String authority=u.getAuthority();
		if (authority != null && authority.length() > 0) {
			result.append("//");
			int pos = authority.indexOf( '@' );
			if ( pos != -1 ){
				result.append(authority.substring(0,pos+1));
				authority = authority.substring(pos+1);
			}
			pos = authority.lastIndexOf(':');
			if ( pos == -1 ){
				result.append(host );
			}else{
				result.append(host).append(authority.substring(pos));
			}
		}
		if (u.getPath() != null) {
			result.append(u.getPath());
		}
		if (u.getQuery() != null) {
			result.append('?');
			result.append(u.getQuery());
		}
		if (u.getRef() != null) {
			result.append("#");
			result.append(u.getRef());
		}
		try{
			return( new URL( result.toString()));
		}catch( Throwable e ){
			Debug.out(e);
			return(u);
		}
	}

	public static URL
	setProtocol(
		URL			u,
		String		protocol )
	{
		String str = u.toExternalForm();

		int pos = str.indexOf( ":" );

		try{
			return( new URL( protocol + str.substring( pos )));

		}catch( Throwable e ){

			Debug.out( e );

			return( u );
		}
	}

	public static URL
	getBaseURL(
		URL		u )
	{
		StringBuilder result = new StringBuilder();
		result.append(u.getProtocol());
		result.append(":");
		String authority=u.getAuthority();
		if (authority != null && authority.length() > 0) {
			result.append("//");
			int pos = authority.indexOf( '@' );
			if ( pos != -1 ){
				result.append(authority.substring(0,pos+1));
				authority = authority.substring(pos+1);
			}
			pos = authority.lastIndexOf(':');
			int	port = u.getPort();
			if ( port == -1 ){
				port = u.getDefaultPort();
			}
			if ( pos == -1 ){
				result.append(authority).append(":").append(port);
			}else{
				result.append(authority.substring(0, pos + 1)).append(port);
			}
		}

		try{
			return( new URL( result.toString()));
		}catch( Throwable e ){
			Debug.out(e);
			return(u);
		}
	}

	public static String
	getCanonicalString(
		String		str )
	{
		try{
			return( getCanonicalString( new URL( str )));
			
		}catch( Throwable e ){
		}
		
		return( str );
	}
	
	public static String
	getCanonicalString(
		URL		url )
	{
		String protocol = url.getProtocol();

		if ( !protocol.equals( protocol.toLowerCase( Locale.US ))){

			protocol = protocol.toLowerCase( Locale.US );

			url = setProtocol( url, protocol );
		}

		int	port 			= url.getPort();
		int	default_port	= url.getDefaultPort();
		
		if ( port == default_port ){

			url = UrlUtils.setPort( url, 0 );
		}

		return( url.toExternalForm());
	}

		/**
		 * Returns an explicit IPv4 url if the supplied one has both IPv6 and IPv4 addresses
		 * @param url
		 * @return
		 */

	public static URL
	getIPV4Fallback(
		URL	url )
	{
		try{
			InetAddress[] addresses = AddressUtils.getAllByName( url.getHost());

			if ( addresses.length > 0 ){

				InetAddress	ipv4	= null;
				InetAddress	ipv6	= null;

				for ( InetAddress a: addresses ){

					if ( a instanceof Inet4Address ){

						ipv4 = a;

					}else{

						ipv6 = a;
					}
				}

				if ( ipv4 != null && ipv6 != null ){

					url = UrlUtils.setHost( url, ipv4.getHostAddress());

					return( url );
				}
			}
		}catch( Throwable f ){
		}

		return( null );
	}

	public static long
	getContentLength(
		URLConnection	con )
	{
		long res = con.getContentLength();

		if ( res == -1 ){

			try{
				String	str = con.getHeaderField( "content-length" );

				if ( str != null ){

					res = Long.parseLong( str );
				}
			}catch( Throwable e ){

			}
		}

		return( res );
	}

	public static boolean
	SSLSocketSNIHack(
		String		host_name,
		SSLSocket	socket )
	{
			// http://stackoverflow.com/questions/30817934/extended-server-name-sni-extension-not-sent-with-jdk1-8-0-but-send-with-jdk1-7
			// also https://bugs.openjdk.java.net/browse/JDK-8144566 kinda


		try{
			/*
			Object sni_host_name = Class.forName( "javax.net.ssl.SNIHostName").getConstructor( String.class ).newInstance( host_name );

			List<Object> sni_host_names = new ArrayList<>(1);

			sni_host_names.add( sni_host_name );

			Object ssl_parameters = SSLSocket.class.getMethod( "getSSLParameters" ).invoke( socket );

			Class ssl_parameters_class = Class.forName( "javax.net.ssl.SSLParameters" );

			ssl_parameters_class.getMethod( "setServerNames", List.class ).invoke( ssl_parameters, sni_host_names );

			SSLSocket.class.getMethod( "setSSLParameters" , ssl_parameters_class ).invoke( socket, ssl_parameters );
			*/
			
			SNIHostName serverName = new SNIHostName( host_name );
			
			List<SNIServerName> serverNames = new ArrayList<>(1);
			
			serverNames.add(serverName);

			SSLParameters params = socket.getSSLParameters();
			
			params.setServerNames(serverNames);
			
			socket.setSSLParameters(params);

			return( true );

		}catch( Throwable e ){

			return( false );
		}
	}

	public static SSLSocketFactory
	DHHackIt(
		final SSLSocketFactory	factory )
	{
		SSLSocketFactory hack = new
			SSLSocketFactory()
			{
				@Override
				public Socket createSocket() throws IOException {
					Socket result = factory.createSocket();

					hack( result );

					return( result );
				}
				@Override
					public Socket createSocket(
						InetAddress address,
						int port,
						InetAddress localAddress,
						int localPort)
						throws IOException {
					Socket result = factory.createSocket( address, port, localAddress, localPort );

					hack( result );

					return( result );
				}
				@Override
				public Socket createSocket(
						InetAddress host,
						int port)
						throws IOException {
					Socket result = factory.createSocket( host, port );

					hack( result );

					return( result );
				}
				@Override
				public Socket createSocket(
						Socket s,
						String host,
						int port,
						boolean autoClose)
						throws IOException {
					Socket result = factory.createSocket( s, host, port, autoClose );

					hack( result );

					return( result );
				}
				@Override
				public Socket createSocket(
						String host,
						int port)
						throws IOException,
						UnknownHostException {
					Socket result = factory.createSocket( host, port );

					hack( result );

					return( result );
				}
				@Override
				public Socket createSocket(
						String host,
						int port,
						InetAddress localHost,
						int localPort)
						throws IOException,
						UnknownHostException {
					Socket result = factory.createSocket( host, port, localHost, localPort );

					hack( result );

					return( result );
				}
				@Override
				public String[] getDefaultCipherSuites() {
					String[] result = factory.getDefaultCipherSuites();

					result = hack( result );

					return( result );
				}
				@Override
				public String[] getSupportedCipherSuites() {
					String[] result = factory.getSupportedCipherSuites();

					result = hack( result );

					return( result );
				}

				private void
				hack(
					Socket	socket )
				{
					SSLSocket ssl_socket = (SSLSocket)socket;

					ssl_socket.setEnabledCipherSuites( hack( ssl_socket.getEnabledCipherSuites()));
				}

				private String[]
				hack(
					String[]	cs  )
				{
					List<String> new_cs = new ArrayList<>();

					for ( String x: cs ){

						if ( x.contains( "_DH_" ) || x.contains( "_DHE_" )){

						}else{

							new_cs.add( x );
						}
					}

					return( new_cs.toArray(new String[new_cs.size()]));
				}
			};

		return( hack );
	}

	public static void
	HTTPSURLConnectionSNIHack(
		final String			host_name,
		HttpsURLConnection 		con )
	{
		final SSLSocketFactory factory = con.getSSLSocketFactory();

		SSLSocketFactory hack = new
			SSLSocketFactory()
			{
				@Override
				public Socket createSocket() throws IOException {
					Socket result = factory.createSocket();

					hack( result );

					return( result );
				}
				@Override
					public Socket createSocket(
						InetAddress address,
						int port,
						InetAddress localAddress,
						int localPort)
						throws IOException {
					Socket result = factory.createSocket( address, port, localAddress, localPort );

					hack( result );

					return( result );
				}
				@Override
				public Socket createSocket(
						InetAddress host,
						int port)
						throws IOException {
					Socket result = factory.createSocket( host, port );

					hack( result );

					return( result );
				}
				@Override
				public Socket createSocket(
						Socket s,
						String host,
						int port,
						boolean autoClose)
						throws IOException {
					Socket result = factory.createSocket( s, host, port, autoClose );

					hack( result );

					return( result );
				}
				@Override
				public Socket createSocket(
						String host,
						int port)
						throws IOException,
						UnknownHostException {
					Socket result = factory.createSocket( host, port );

					hack( result );

					return( result );
				}
				@Override
				public Socket createSocket(
						String host,
						int port,
						InetAddress localHost,
						int localPort)
						throws IOException,
						UnknownHostException {
					Socket result = factory.createSocket( host, port, localHost, localPort );

					hack( result );

					return( result );
				}
				@Override
				public String[] getDefaultCipherSuites() {
					String[] result = factory.getDefaultCipherSuites();

					result = hack( result );

					return( result );
				}
				@Override
				public String[] getSupportedCipherSuites() {
					String[] result = factory.getSupportedCipherSuites();

					result = hack( result );

					return( result );
				}

				private void
				hack(
					Socket	socket )
				{
					SSLSocket ssl_socket = (SSLSocket)socket;

					SSLSocketSNIHack( host_name, ssl_socket);
				}

				private String[]
				hack(
					String[]	cs  )
				{
					return( cs );
				}
			};

		con.setSSLSocketFactory( hack );
	}

	public static void
	DHHackIt(
		HttpsURLConnection	ssl_con )
	{
		final SSLSocketFactory factory = ssl_con.getSSLSocketFactory();

		SSLSocketFactory hack = DHHackIt( factory );

		ssl_con.setSSLSocketFactory( hack );
	}

	public static Socket
	connectSocketAndWrite(
		boolean		is_ssl,
		String		target_host,
		int			target_port,
		byte[]		bytes,
		int			connect_timeout,
		int			read_timeout )

		throws Throwable
	{

		return( connectSocketAndWrite( is_ssl, target_host, target_port, bytes, connect_timeout, read_timeout, false ));
	}

	public static Socket
	connectSocketAndWrite(
		boolean		is_ssl,
		String		target_host,
		int			target_port,
		byte[]		bytes,
		int			connect_timeout,
		int			read_timeout,
		boolean		unconnected_socket_hack )

		throws Throwable
	{
		List<InetAddress[]> binds =  NetworkAdmin.getSingleton().getSingleHomedServiceBindings( target_host );

		Throwable last_error = null;
		
		for( InetAddress[] bind: binds ){
			
			try{
				return(
					connectSocketAndWrite(
						is_ssl,
						target_host,
						bind[1],
						bind[0],
						target_port,
						bytes,
						connect_timeout,
						read_timeout,
						unconnected_socket_hack ));
				
			}catch( Throwable e ){
				
				last_error = e;
			}
		}
		
		if ( last_error != null ){
			
			throw( last_error );
		}
		
		throw( new Exception( "hmm"));
	}
	
	public static Socket
	connectSocketAndWrite(
		boolean			is_ssl,
		String			target_host_name,
		InetAddress		bind_ip,
		InetAddress		target_host,
		int				target_port,
		byte[]			bytes,
		int				connect_timeout,
		int				read_timeout,
		boolean			unconnected_socket_hack )

		throws Throwable
	{
		boolean	cert_hack			= false;
		boolean	dh_hack 			= false;
		boolean	internal_error_hack	= false;

		boolean		hacks_to_do = true;
		Exception	last_error 	= null;

		while( hacks_to_do ){

			hacks_to_do = false;

			Socket	target = null;

			boolean	ok = false;

			try{
			    InetSocketAddress targetSockAddress = new InetSocketAddress( target_host, target_port );
			    			    
				if ( is_ssl ){

					TrustManager[] tms_delegate = SESecurityManager.getAllTrustingTrustManager();

					SSLContext sc = SSLContext.getInstance("SSL");

					sc.init( null, tms_delegate, RandomUtils.SECURE_RANDOM );

					SSLSocketFactory factory = sc.getSocketFactory();

					if ( dh_hack ){

						factory = DHHackIt( factory );
					}else{

						factory = DHHackIt( factory );

					}

					if ( unconnected_socket_hack ){

						if ( bind_ip == null ){

							target = factory.createSocket(targetSockAddress.getAddress(), targetSockAddress.getPort());

						}else{

							target = factory.createSocket(targetSockAddress.getAddress(), targetSockAddress.getPort(), bind_ip, 0 );
						}
					}else{

						target = factory.createSocket();
					}
				}else{

					if ( unconnected_socket_hack ){

						if ( bind_ip == null ){

							target = new Socket(targetSockAddress.getAddress(), targetSockAddress.getPort());

						}else{

							target = new Socket(targetSockAddress.getAddress(), targetSockAddress.getPort(), bind_ip, 0 );
						}
					}else{

						target = new Socket();
					}
				}

				if ( internal_error_hack ){

					SSLSocketSNIHack( target_host_name, (SSLSocket)target );
				}

				target.setSoTimeout( read_timeout );

				if ( !unconnected_socket_hack ){

			        if ( bind_ip != null ){

			        	target.bind( new InetSocketAddress( bind_ip, 0 ) );
			        }

			        target.connect( targetSockAddress, connect_timeout );
				}

				target.getOutputStream().write( bytes );

				ok = true;

				return( target );

			}catch( Exception e ){

				last_error = e;

				if ( e instanceof SSLException ){

					String msg = Debug.getNestedExceptionMessage( e );

					if ( msg.contains( "DH keypair" )){

						if ( !dh_hack ){

							dh_hack = true;

							hacks_to_do = true;
						}
					}else if ( msg.contains( "internal_error" ) || msg.contains( "handshake_failure" )){

						if ( !internal_error_hack ){

							internal_error_hack = true;

							hacks_to_do = true;
						}
					}

					if ( !cert_hack ){

						cert_hack = true;

						SESecurityManager.installServerCertificates( new URL( "https://" + target_host +  ":" + target_port + "/" ));

						hacks_to_do = true;
					}
				}

				if ( !hacks_to_do ){

					throw( e );
				}
			}finally{

				if ( !ok ){

					if ( target != null ){

						target.close();
					}
				}
			}
		}

		throw( last_error );
	}
	
	public static List<InetSocketAddress>
	getURLAddresses(
		URL		url )
	{
		String	host	= url.getHost();
		int		port	= url.getPort()==-1?url.getDefaultPort():url.getPort();
			
		if ( port == -1 ){
			
			port = 80;
		}
		
		List<InetAddress> addresses = null;
			
		try{
			addresses = DNSUtils.getSingleton().getAllByName( host );
				
		}catch( Throwable e ){
				
			try{
				addresses = Arrays.asList( InetAddress.getAllByName( host ));
					
			}catch( Throwable f ){
			}
		}
		
		List<InetSocketAddress> result = new ArrayList<>();
		
		if ( addresses == null ){
			
			result.add( new InetSocketAddress( host, port ));
			
		}else{
			
			for ( InetAddress ia: addresses ){
				
				result.add( new InetSocketAddress( ia, port ));
			}
		}
		
		return( result );
	}
}
