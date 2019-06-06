/*
 * File    : TrackerWebPageReplyImpl.java
 * Created : 08-Dec-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.tracker;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.*;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;

public class
TrackerWebPageResponseImpl
	implements TrackerWebPageResponse
{
	private static final String	NL			= "\r\n";

	private ByteArrayOutputStream	baos 		= new ByteArrayOutputStream(2048);
	private boolean					baos_set	= false;
	
	private String				content_type = "text/html";

	private int					reply_status	= 200;

	private Map<String,Object>		header_map 	= new LinkedHashMap<>();

	private TrackerWebPageRequestImpl 	request;
	private boolean						raw_output;
	private boolean						is_async;

	private int							explicit_gzip	= 0; // not set, 1 = gzip, 2 = no gzip
	private boolean						is_gzipped;

	protected
	TrackerWebPageResponseImpl(
		TrackerWebPageRequestImpl 	_request )
	{
		request 		= _request;

		String	formatted_date_now		 = TimeFormatter.getHTTPDate( SystemTime.getCurrentTime());

		setHeader( "Last-Modified",	formatted_date_now );

		setHeader( "Expires", formatted_date_now );
	}

	@Override
	public void
	setLastModified(
		long		time )
	{
		String	formatted_date		 = TimeFormatter.getHTTPDate( time );

		setHeader( "Last-Modified",	formatted_date );
	}

	@Override
	public void
	setExpires(
		long		time )
	{
		String	formatted_date		 = TimeFormatter.getHTTPDate( time );

		setHeader( "Expires",	formatted_date );
	}

	@Override
	public void
	setContentType(
		String		type )
	{
		content_type	= type;
	}

	@Override
	public String 
	getContentType()
	{
		return( content_type );
	}
	
	@Override
	public void
	setReplyStatus(
		int		status )
	{
		reply_status 	= status;
	}

	@Override
	public void
	setHeader(
		String		name,
		String		value )
	{
		if ( name.equalsIgnoreCase( "set-cookie" )){

			Iterator<String>	it = header_map.keySet().iterator();

			while( it.hasNext()){

				String	key = it.next();

				if ( key.equalsIgnoreCase( name )){

					Object existing = header_map.get( key );

					if ( existing instanceof String ){

						String old = (String)existing;

						List l = new ArrayList(3);

						l.add( old );
						l.add( value );

						header_map.put( name, l );

					}else{

						List l = (List)existing;

						if ( !l.contains( value )){

							l.add( value );
						}
					}

					return;
				}
			}

			header_map.put( name, value );

		}else{

			addHeader( name, value, true );
		}
	}

	@Override
	public void
	setGZIP(
		boolean		gzip )
	{
		explicit_gzip = gzip?1:2;
	}

	protected String
	addHeader(
		String		name,
		String		value,
		boolean		replace )
	{
		Iterator<String>	it = header_map.keySet().iterator();

		while( it.hasNext()){

			String	key = it.next();

			if ( key.equalsIgnoreCase( name )){

				if ( replace ){

					it.remove();

				}else{

					Object existing = header_map.get( key );

					if ( existing instanceof String ){

						return((String)existing);

					}else if ( existing instanceof List ){

						List<String> l = (List<String>)existing;

						if ( l.size() > 0 ){

							return( l.get(0));
						}
					}

					return( null );
				}
			}
		}

		header_map.put( name, value );

		return( value );
	}

	@Override
	public OutputStream
	getOutputStream()
	{
		return( baos );
	}
	
	public void
	setOutputStream(
		ByteArrayOutputStream	_baos )
	{
		baos		= _baos;
		baos_set	= true;
	}

	@Override
	public OutputStream
	getRawOutputStream()

		throws IOException
	{
		if ( baos_set ){
			
			throw( new IOException( "OutputStream already set" ));
		}
		
		raw_output = true;

		return( request.getOutputStream());
	}

	@Override
	public boolean
	isActive()
	{
		return( request.isActive());
	}

	protected void
	complete()

		throws IOException
	{
		if ( is_async || raw_output ){

			return;
		}

		byte[]	reply_bytes = baos.toByteArray();

		// System.out.println( "TrackerWebPageResponse::complete: data = " + reply_bytes.length );

		String	status_string = "BAD";

			// random collection

		if ( reply_status == 200 ){

			status_string = "OK";

		}else if ( reply_status == 204 ){

			status_string = "No Content";

		}else if ( reply_status == 206 ){

			status_string = "Partial Content";

		}else if ( reply_status == 401 ){

			status_string = "Unauthorized";

		}else if ( reply_status == 404 ){

			status_string = "Not Found";

		}else if ( reply_status == 501 ){

			status_string = "Not Implemented";
		}

		String reply_header = "HTTP/1.1 " + reply_status + " " + status_string + NL;

			// add header fields if not already present

		addHeader( "Server", Constants.BIGLYBT_NAME + " " + Constants.BIGLYBT_VERSION, false );

		if ( request.canKeepAlive()){

			String	applied_value = addHeader( "Connection", "keep-alive", false );

			if ( applied_value.equalsIgnoreCase( "keep-alive" )){

				request.setKeepAlive( true );
			}
		}else{

			addHeader( "Connection", "close", true );
		}

		addHeader( "Content-Type", content_type, false );

		boolean do_gzip = false;

		if ( explicit_gzip == 1 && !is_gzipped ){

			Map headers = request.getHeaders();

			String	accept_encoding = (String)headers.get("accept-encoding");

			if ( HTTPUtils.canGZIP(accept_encoding)){

				is_gzipped = do_gzip = true;

				header_map.put("Content-Encoding", "gzip");
			}
		}

		Iterator<String>	it = header_map.keySet().iterator();

		while( it.hasNext()){

			String	name 	= it.next();
			Object	value 	= header_map.get(name);

			if ( value instanceof String ){

				reply_header += name + ": " + value + NL;

			}else{

				List<String>	l = (List<String>)value;

				for ( String v: l ){

					reply_header += name + ": " + v + NL;
				}
			}
		}

		if ( do_gzip ){

			// some http readers need valid content-length.
			// For large replies, write to temp file to same memory

			if ( reply_bytes.length < 512*1024 ){

				ByteArrayOutputStream temp = new ByteArrayOutputStream( reply_bytes.length );

				GZIPOutputStream gzos = new GZIPOutputStream(temp);

				gzos.write( reply_bytes );

				gzos.close();

				reply_bytes = temp.toByteArray();

			} else {
				File post_file = AETemporaryFileHandler.createTempFile();

				post_file.deleteOnExit();

				FileOutputStream fos = new FileOutputStream( post_file );
				GZIPOutputStream gzos = new GZIPOutputStream(fos);

				gzos.write( reply_bytes );

				gzos.close();

				FileInputStream fis = new FileInputStream(post_file);

				reply_header +=
						"Content-Length: " + post_file.length() + NL +
						NL;

				OutputStream os = request.getOutputStream();

				os.write( reply_header.getBytes());

				byte[] buffer = new byte[16384];
				while (true) {
					int read = fis.read(buffer);
					if (read == -1) {
						break;
					}
					os.write(buffer, 0, read);
				}

				os.flush();
				fis.close();
				post_file.delete();

				return;

			}
		}

		reply_header +=
			"Content-Length: " + reply_bytes.length + NL +
			NL;

		// System.out.println( "writing reply:" + reply_header );

		OutputStream os = request.getOutputStream();

		os.write( reply_header.getBytes());

		os.write( reply_bytes );

		os.flush();
	}

	@Override
	public boolean
	useFile(
		String		root_dir,
		String		relative_url )

		throws IOException
	{
		// strip parameters from relative_url
		int paramPos = relative_url.indexOf('?');
		if (paramPos >= 0) {
			relative_url = relative_url.substring(0, paramPos);
		}
		String	target = root_dir + relative_url.replace('/',File.separatorChar);

		File canonical_file = new File(target).getCanonicalFile();
		
		File canonical_root = new File(root_dir).getCanonicalFile();

			// make sure some fool isn't trying to use ../../ to escape from web dir

		if ( !canonical_file.toString().toLowerCase().startsWith( canonical_root.toString().toLowerCase())){

			return( false );
		}

		if ( canonical_file.isDirectory()){

			return( false );
		}

		if ( canonical_file.canRead()){

			String str = canonical_file.toString().toLowerCase();

			int	pos = str.lastIndexOf( "." );

			if ( pos == -1 ){

				return( false );
			}

			String	file_type = str.substring(pos+1);

			FileInputStream	fis = null;

			try{
				fis = new FileInputStream(canonical_file);

				useStream( file_type, fis );

				return( true );

			}finally{

				if ( fis != null ){

					fis.close();
				}
			}
		}

		return( false );
	}

	@Override
	public void
	useStream(
		String		file_type,
		InputStream	input_stream )

		throws IOException
	{
		OutputStream	os = getOutputStream();

		String response_type = HTTPUtils.guessContentTypeFromFileType(file_type);

		if ( explicit_gzip != 2 && HTTPUtils.useCompressionForFileType(response_type)){

			Map headers = request.getHeaders();

			String	accept_encoding = (String)headers.get("accept-encoding");

			if ( HTTPUtils.canGZIP(accept_encoding)){

				is_gzipped = true;

				os = new GZIPOutputStream(os);

				header_map.put("Content-Encoding", "gzip");
			}
		}

		setContentType( response_type );

		byte[]	buffer = new byte[4096];

		while(true){

			int	len = input_stream.read(buffer);

			if ( len <= 0 ){

				break;
			}

			os.write( buffer, 0, len );
		}

		if ( os instanceof GZIPOutputStream ){

			((GZIPOutputStream)os).finish();
		}
	}

	@Override
	public void
	writeTorrent(
		TrackerTorrent	tracker_torrent )

		throws IOException
	{
		try{

			TRHostTorrent	host_torrent = ((TrackerTorrentImpl)tracker_torrent).getHostTorrent();

			TOTorrent	torrent = host_torrent.getTorrent();

			// make a copy of the torrent

			TOTorrent	torrent_to_send = TOTorrentFactory.deserialiseFromMap(torrent.serialiseToMap());

			// remove any non-standard stuff (e.g. resume data)

			torrent_to_send.removeAdditionalProperties();

			if ( !TorrentUtils.isDecentralised( torrent_to_send )){

				URL[][]	url_sets = TRTrackerUtils.getAnnounceURLs();

					// if tracker ip not set then assume they know what they're doing

				if ( host_torrent.getStatus() != TRHostTorrent.TS_PUBLISHED && url_sets.length > 0 ){

						// if the user has disabled the mangling of urls when hosting then don't do it here
						// either

					if ( COConfigurationManager.getBooleanParameter("Tracker Host Add Our Announce URLs")){

						String protocol = torrent_to_send.getAnnounceURL().getProtocol();

						for (int i=0;i<url_sets.length;i++){

							URL[]	urls = url_sets[i];

							if ( urls[0].getProtocol().equalsIgnoreCase( protocol )){

								torrent_to_send.setAnnounceURL( urls[0] );

								torrent_to_send.getAnnounceURLGroup().setAnnounceURLSets( new TOTorrentAnnounceURLSet[0]);

								for (int j=1;j<urls.length;j++){

									TorrentUtils.announceGroupsInsertLast( torrent_to_send, new URL[]{ urls[j] });
								}

								break;
							}
						}
					}
				}
			}

			baos.write( BEncoder.encode( torrent_to_send.serialiseToMap()));

			setContentType( "application/x-bittorrent" );

		}catch( TOTorrentException e ){

			Debug.printStackTrace( e );

			throw( new IOException( e.toString()));
		}
	}

	@Override
	public void
	setAsynchronous(
		boolean a )

		throws IOException
	{
		AsyncController async_control = request.getAsyncController();

		if ( async_control == null ){

			throw( new IOException( "Request is not non-blocking" ));
		}

		if ( a ){

			is_async	= true;

			async_control.setAsyncStart();

		}else{

			is_async	= false;

			complete();

			async_control.setAsyncComplete();
		}
	}

	@Override
	public boolean
	getAsynchronous()
	{
		return( is_async );
	}
}
