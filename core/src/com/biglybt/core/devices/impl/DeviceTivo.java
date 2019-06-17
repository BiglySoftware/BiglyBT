/*
 * Created on Jul 24, 2009
 * Created by Paul Gardner
 *
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


package com.biglybt.core.devices.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;

import com.biglybt.core.devices.TranscodeFile;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.core.xml.util.XUXmlWriter;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pifimpl.local.utils.FormattersImpl;

public class
DeviceTivo
	extends DeviceMediaRendererImpl
{
	private static final boolean	TRACE	= false;

	private static final String		NL				= "\r\n";

	private static Map<String,Comparator<ItemInfo>>	sort_comparators = new HashMap<>();

	static {
		sort_comparators.put(
			"Type",
			new Comparator<ItemInfo>()
			{
				@Override
				public int
				compare(
					ItemInfo o1, ItemInfo o2 )
				{
					if ( o1.isContainer() == o2.isContainer()){

						return( 0 );
					}

					if ( o1.isContainer()){

						return( -1 );
					}

					return( 1 );
				}
			});

		sort_comparators.put(
			"Title",
			new Comparator<ItemInfo>()
			{
				Comparator<String> c = new FormattersImpl().getAlphanumericComparator( true );

				@Override
				public int
				compare(
					ItemInfo o1, ItemInfo o2 )
				{
					return( c.compare( o1.getName(), o2.getName()));
				}
			});

		sort_comparators.put(
			"CreationDate",
			new Comparator<ItemInfo>()
			{
				@Override
				public int
				compare(
					ItemInfo o1, ItemInfo o2 )
				{
					long	res = o1.getCreationMillis() - o2.getCreationMillis();

					if ( res < 0 ){
						return( -1 );
					}else if ( res > 0 ){
						return( 1 );
					}else{
						return( 0 );
					}
				}
			});

		sort_comparators.put( "LastChangeDate", sort_comparators.get( "CreationDate" ));
		sort_comparators.put( "CaptureDate", sort_comparators.get( "CreationDate" ));
	}

	private String		server_name;
	private boolean		tried_tcp_beacon;

	protected
	DeviceTivo(
		DeviceManagerImpl	_manager,
		String				_uid,
		String				_classification )
	{
		super( _manager, _uid, _classification, false );

		setName( "TiVo", true );
	}

	protected
	DeviceTivo(
		DeviceManagerImpl	_manager,
		Map					_map )

		throws IOException
	{
		super( _manager, _map );
	}

	@Override
	protected boolean
	updateFrom(
		DeviceImpl		_other,
		boolean			_is_alive )
	{
		if ( !super.updateFrom( _other, _is_alive )){

			return( false );
		}

		if ( !( _other instanceof DeviceTivo )){

			Debug.out( "Inconsistent" );

			return( false );
		}

		DeviceTivo other = (DeviceTivo)_other;

		return( true );
	}

	@Override
	protected void
	initialise()
	{
		super.initialise();
	}

	@Override
	public boolean
	canFilterFilesView()
	{
		return( false );
	}

	@Override
	public boolean
	canAssociate()
	{
		return( true );
	}

	@Override
	public boolean
	canRestrictAccess()
	{
			// guess we could implement this one day

		return( false );
	}

	@Override
	public boolean
	canShowCategories()
	{
		return( true );
	}

	@Override
	protected boolean
	getShowCategoriesDefault()
	{
		return( true );
	}

	protected String
	getMachineName()
	{
		return( getPersistentStringProperty( PP_TIVO_MACHINE, null ));
	}

	protected void
	found(
		DeviceTivoManager	_tivo_manager,
		InetAddress			_address,
		String				_server_name,
		String				_machine )
	{
		boolean	first_time = false;

		synchronized( this ){

			if ( server_name == null ){

				server_name	= _server_name;

				first_time = true;
			}
		}

		if ( _machine == null && !tried_tcp_beacon ){

			try{
				Socket socket = new Socket();

				try{
					socket.connect( new InetSocketAddress( _address, 2190 ), 5000 );

					socket.setSoTimeout( 5000 );

					DataOutputStream dos = new DataOutputStream( socket.getOutputStream());

					byte[]	beacon_out = _tivo_manager.encodeBeacon( false, 0 );

					dos.writeInt( beacon_out.length );

					dos.write( beacon_out );

					DataInputStream dis = new DataInputStream( socket.getInputStream());

					int len = dis.readInt();

					if ( len < 65536 ){

						byte[] bytes = new byte[len];

						int	pos = 0;

						while( pos < len ){

							int read = dis.read( bytes, pos, len-pos );

							pos += read;
						}

						Map<String,String> beacon_in = _tivo_manager.decodeBeacon( bytes, len );

						_machine = beacon_in.get( "machine" );
					}
				}finally{

					socket.close();
				}
			}catch( Throwable e ){

			}finally{

				tried_tcp_beacon = true;
			}
		}

		if ( _machine != null ){

			String existing = getMachineName();

			if ( existing == null || !existing.equals( _machine )){

				setPersistentStringProperty( PP_TIVO_MACHINE, _machine );
			}
		}

		setAddress( _address );

		alive();

		if ( first_time ){

			browseReceived();
		}
	}


	protected boolean
	generate(
		TrackerWebPageRequest 	request,
		TrackerWebPageResponse 	response )

		throws IOException
	{
		InetSocketAddress	local_address = request.getLocalAddress();

		if ( local_address == null ){

			return( false );
		}

		String	host = local_address.getAddress().getHostAddress();

		String	url = request.getURL();

		if ( TRACE ){
			System.out.println( "url: " + url );
		}

		if ( !url.startsWith( "/TiVoConnect?" )){

			return( false );
		}

		int pos = url.indexOf( '?' );

		if ( pos == -1 ){

			return(false );
		}

		String[]	bits = url.substring( pos+1 ).split( "&" );

		Map<String,String>	args = new HashMap<>();

		for ( String bit: bits ){

			String[] x = bit.split( "=" );

			args.put( x[0], URLDecoder.decode( x[1], "UTF-8" ));
		}

		if ( TRACE ){
			System.out.println( "args: " + args );
		}

			// root folder /TiVoConnect?Command=QueryContainer&Container=%2F

		String	command = args.get( "Command" );

		if ( command == null ){

			return( false );
		}

		String reply = null;

		if ( command.equals( "QueryContainer" )){

			String	container = args.get( "Container" );

			if ( container == null ){

				return( false );
			}

			if ( container.equals( "/" )){

				reply =
				"<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + NL +
				"<TiVoContainer>" + NL +
				"    <Details>" + NL +
				"        <Title>" + server_name + "</Title>" + NL +
				"        <ContentType>x-container/tivo-server</ContentType>" + NL +
				"        <SourceFormat>x-container/folder</SourceFormat>" + NL +
				"        <TotalItems>1</TotalItems>" + NL +
				"    </Details>" + NL +
				"    <Item>" + NL +
				"        <Details>" + NL +
				"            <Title>" + server_name + "</Title>" + NL +
				"            <ContentType>x-container/tivo-videos</ContentType>" + NL +
				"            <SourceFormat>x-container/folder</SourceFormat>" + NL +
				"        </Details>" + NL +
				"        <Links>" + NL +
				"            <Content>" + NL +
				"                <Url>/TiVoConnect?Command=QueryContainer&amp;Container=" + urlencode( "/Content" ) + "</Url>" + NL +
				"                <ContentType>x-container/tivo-videos</ContentType>" + NL +
				"            </Content>" + NL +
				"        </Links>" + NL +
				"    </Item>" + NL +
				"    <ItemStart>0</ItemStart>" + NL +
				"    <ItemCount>1</ItemCount>" + NL +
				"</TiVoContainer>";

			}else if ( container.startsWith( "/Content" )){

				boolean	show_categories = getShowCategories();

				String	recurse = args.get( "Recurse" );

				if ( recurse != null && recurse.equals( "Yes" )){

					show_categories = false;
				}

				TranscodeFileImpl[] tfs = getFiles();

				String	category_or_tag = null;

				Map<String,ContainerInfo>	categories_or_tags = null;

				if ( show_categories ){

					if ( container.startsWith( "/Content/" )){

						category_or_tag = container.substring( container.lastIndexOf( '/' ) + 1 );

					}else{

						categories_or_tags = new HashMap<>();
					}
				}

					// build list of applicable items

				List<ItemInfo> items = new ArrayList<>(tfs.length);

				for ( TranscodeFileImpl file: tfs ){

					if ( !file.isComplete()){

							// see if we can set up a stream xcode for this but only if we
							// know the duration and the transcode is in progress (done in setup)

						if ( !setupStreamXCode( file )){

							continue;
						}
					}

					if ( category_or_tag != null ){

						boolean	hit = false;

						String[]	cats 	= file.getCategories();
						String[]	tags	= file.getTags( true );

						for ( String[] strs: new String[][]{ cats,tags }){

							for ( String c: strs ){

								if ( c.equals( category_or_tag )){

									hit = true;
								}
							}
						}

						if ( !hit ){

							continue;
						}
					}

					FileInfo	info = new FileInfo( file, host );

					if ( info.isOK()){

						boolean	skip = false;

						if ( categories_or_tags != null ){

							String[]	cats 	= file.getCategories();
							String[]	tags	= file.getTags( true );

							if ( cats.length > 0 || tags.length > 0 ){

								skip = true;

								for ( String[] strs: new String[][]{ cats,tags }){

									for ( String s: strs ){

										ContainerInfo cont = categories_or_tags.get( s );

										if ( cont == null ){

											items.add( cont = new ContainerInfo( s ));

											categories_or_tags.put( s, cont );
										}

										cont.addChild();
									}
								}
							}
						}

						if ( !skip ){

							items.add( info );
						}
					}
				}

					// sort

				String	sort_order = args.get( "SortOrder" );

				if ( sort_order != null ){

					String[] keys = RegExUtil.PAT_SPLIT_COMMA.split(sort_order);

					final List<Comparator<ItemInfo>> 	comparators = new ArrayList<>();
					final List<Boolean>					reverses	= new ArrayList<>();

					for ( String key: keys ){

						boolean	reverse = false;

						if ( key.startsWith( "!" )){

							reverse = true;

							key = key.substring(1);
						}

						Comparator<ItemInfo> comp = sort_comparators.get( key );

						if ( comp != null ){

							comparators.add( comp );
							reverses.add( reverse );
						}
					}

					if ( comparators.size() > 0 ){

						Collections.sort(
							items,
							new Comparator<ItemInfo>()
							{
								@Override
								public int
								compare(
									ItemInfo i1,
									ItemInfo i2)
								{
									for ( int i=0;i<comparators.size();i++){

										Comparator<ItemInfo> comp = comparators.get(i);

										int res = comp.compare( i1, i2 );

										if ( res != 0 ){

											if ( reverses.get(i)){

												if ( res < 0 ){

													res = 1;

												}else{

													res = -1;
												}
											}

											return( res );
										}
									}

									return( 0 );
								}
							});
					}
				}

					// select items to return

				String	item_count		= args.get( "ItemCount" );
				String	anchor_offset 	= args.get( "AnchorOffset" );
				String	anchor 			= args.get( "AnchorItem" );

				int	num_items;

				if ( item_count == null ){

					num_items = items.size();

				}else{

						// can be negative if X items from end

					num_items = Integer.parseInt( item_count );
				}

				int	anchor_index;	// either one before or one after item to be returned depending on count +ve/-ve

				if ( num_items < 0 ){

					anchor_index = items.size();

				}else{

					anchor_index = -1;
				}

				if ( anchor != null ){

					for (int i=0;i<items.size();i++){

						ItemInfo info = items.get(i);

						if ( anchor.equals( info.getLinkURL())){

							anchor_index = i;
						}
					}
				}

				if ( anchor_offset != null ){

					anchor_index += Integer.parseInt( anchor_offset );

					if ( anchor_index < -1 ){

						anchor_index = -1;

					}else if ( anchor_index > items.size()){

						anchor_index = items.size();
					}
				}

				int	start_index;
				int end_index;

				if ( num_items > 0 ){

					start_index = anchor_index + 1;

					end_index	= anchor_index + num_items;

				}else{

					start_index = anchor_index + num_items;

					end_index	= anchor_index - 1;
				}

				if ( start_index < 0 ){

					start_index = 0;
				}

				if ( end_index >= items.size()){

					end_index = items.size() - 1;
				}

				int	num_to_return = end_index - start_index + 1;

				if ( num_to_return < 0 ){

					num_to_return = 0;
				}

				String machine = getMachineName();

				if ( machine == null ){

						// default until we find out what it is - can't see any way to get it apart from wait for broadcast

					machine = "TivoHDDVR";
				}

				String	header =
				"<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + NL +
				"<TiVoContainer>" + NL +
				"    <Tivos>" + NL +
				"      <Tivo>" + machine + "</Tivo>" + NL +
				"    </Tivos>" + NL +
				"    <ItemStart>" + start_index + "</ItemStart>" + NL +
				"    <ItemCount>" + num_to_return + "</ItemCount>" + NL +
				"    <Details>" + NL +
				"        <Title>" + escape( container ) + "</Title>" + NL +
				"        <ContentType>x-container/tivo-videos</ContentType>" + NL +
				"        <SourceFormat>x-container/folder</SourceFormat>" + NL +
				"        <TotalItems>" + items.size() + "</TotalItems>" + NL +
				"    </Details>" + NL;

				reply = header;

				for ( int i=start_index;i<=end_index;i++ ){

					ItemInfo	item = items.get(i);

					if ( item instanceof FileInfo ){

						FileInfo file = (FileInfo)item;

						long	file_size = file.getTargetSize();

						String	title 	= escape( file.getName());
						String	desc	= title;

						int MAX_TITLE_LENGTH = 30;

						if ( title.length() > MAX_TITLE_LENGTH ){

								// TiVo has problems displaying a truncated title if it has
								// no spaces in it

							String 	temp = "";

							for ( int j=0;j<title.length();j++){

								char c = title.charAt( j );

								if ( Character.isLetterOrDigit( c )){

									temp += c;
								}else{

									temp += ' ';
								}
							}

							int space_pos = temp.indexOf( ' ' );

							if ( space_pos == -1 || space_pos > MAX_TITLE_LENGTH ){

								temp = temp.substring( 0, 30 ) + "...";
							}

							title = temp;
						}

						reply +=

						"    <Item>" + NL +
						"        <Details>" + NL +
						"            <Title>" + title + "</Title>" + NL +
						"            <ContentType>video/x-tivo-mpeg</ContentType>" + NL +
						"            <SourceFormat>video/x-ms-wmv</SourceFormat>" + NL;

						if ( file_size > 0 ){
							reply +=
								"            <SourceSize>" + file_size + "</SourceSize>" + NL;
						}else{
							long est_size = file.getEstimatedTargetSize();

							if ( est_size > 0 ){
								reply +=
									"            <SourceSize>" + est_size + "</SourceSize>" + NL;
							}
						}

						reply +=
						"            <Duration>" + file.getDurationMillis() + "</Duration>" + NL +
						"            <Description>" + desc + "</Description>" + NL +
						"            <SourceChannel>0</SourceChannel>" + NL +
						"            <SourceStation></SourceStation>" + NL +
						"            <SeriesId></SeriesId>" + NL +
						"            <CaptureDate>" + file.getCaptureDate() + "</CaptureDate>" + NL +
						"        </Details>" + NL +
						"        <Links>" + NL +
						"            <Content>" + NL +
						"                <ContentType>video/x-tivo-mpeg</ContentType>" + NL +
						"                    <AcceptsParams>No</AcceptsParams>" + NL +
						"                    <Url>" + file.getLinkURL() + "</Url>" + NL +
						"                </Content>" + NL +
						"                <CustomIcon>" + NL +
						"                    <ContentType>video/*</ContentType>" + NL +
						"                    <AcceptsParams>No</AcceptsParams>" + NL +
						"                    <Url>urn:tivo:image:save-until-i-delete-recording</Url>" + NL +
						"                </CustomIcon>" + NL +
						"        </Links>" + NL +
						"    </Item>" + NL;

					}else{

						ContainerInfo cont = (ContainerInfo)item;

						reply +=
						"    <Item>" + NL +
						"        <Details>" + NL +
						"            <Title>" + cont.getName() + "</Title>" + NL +
						"            <ContentType>x-container/tivo-videos</ContentType>" + NL +
						"            <SourceFormat>x-container/folder</SourceFormat>" + NL +
						"            <TotalItems>" + cont.getChildCount() + "</TotalItems>" + NL +
						"        </Details>" + NL +
						"        <Links>" + NL +
						"            <Content>" + NL +
						"                <Url>" + cont.getLinkURL() + "</Url>" + NL +
						"                <ContentType>x-container/tivo-videos</ContentType>" + NL +
						"            </Content>" + NL +
						"        </Links>" + NL +
						"    </Item>" + NL;
					}
				}

				String footer =
				"</TiVoContainer>";

				reply += footer;
			}


		}else if ( command.equals( "QueryFormats")){

			String source_format = args.get( "SourceFormat" );

			if ( source_format != null && source_format.startsWith( "video" )){

					// /TiVoConnect?Command=QueryFormats&SourceFormat=video%2Fx-tivo-mpeg

				reply =
				"<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NL +
				"<TiVoFormats><Format>" + NL +
				"<ContentType>video/x-tivo-mpeg</ContentType><Description/>" + NL +
				"</Format></TiVoFormats>";
			}
		}

		if ( reply == null ){

			return( false );
		}

		if ( TRACE ){
			System.out.println( "->" + reply );
		}

		response.setContentType( "text/xml" );

		response.getOutputStream().write( reply.getBytes( "UTF-8" ));

		return( true );
	}

	protected static String
	urlencode(
		String	str )
	{
		try{
			return( URLEncoder.encode( str, "UTF-8" ));

		}catch( Throwable e ){

			Debug.out( e );

			return( str );
		}
	}

	protected static String
	escape(
		String	str )
	{
		return( XUXmlWriter.escapeXML( str ));
	}

	@Override
	protected void
	getDisplayProperties(
		List<String[]>	dp )
	{
		super.getDisplayProperties( dp );

		addDP( dp, "devices.tivo.machine", getMachineName());
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		super.generate( writer );

		try{
			writer.indent();

			writer.println( "tico_machine=" + getMachineName());

		}finally{

			writer.exdent();
		}
	}

	protected abstract static class
	ItemInfo
	{
		protected abstract String
		getName();

		protected abstract String
		getLinkURL();

		protected abstract boolean
		isContainer();

		public abstract long
		getCreationMillis();

	}

	protected static class
	ContainerInfo
		extends ItemInfo
	{
		private String		name;

		private int		child_count;

		protected
		ContainerInfo(
			String	_name )
		{
			name	= _name;
		}

		@Override
		protected String
		getName()
		{
			return( name );
		}

		@Override
		protected String
		getLinkURL()
		{
			return( "/TiVoConnect?Command=QueryContainer&amp;Container=" + urlencode( "/Content/" + name ));
		}

		protected void
		addChild()
		{
			child_count++;
		}

		protected int
		getChildCount()
		{
			return( child_count );
		}

		@Override
		public long
		getCreationMillis()
		{
			return( 0 );
		}

		@Override
		protected boolean
		isContainer()
		{
			return( true );
		}
	}

	protected static class
	FileInfo
		extends ItemInfo
	{
		private TranscodeFile	file;
		private String			stream_url;
		private long			target_size;
		private long			creation_millis;

		boolean	ok;

		protected
		FileInfo(
			TranscodeFile		_file,
			String				_host )
		{
			file	= _file;

			try{
				URL url = file.getStreamURL( _host );

				if ( url == null ){

					return;
				}

				stream_url = url.toExternalForm();

				try{
					if ( file.isComplete()){

						target_size = file.getTargetFile().getLength();
					}
				}catch( Throwable e ){
				}

				creation_millis =  file.getCreationDateMillis();

				ok = true;

			}catch( Throwable e ){

			}
		}

		protected boolean
		isOK()
		{
			return( ok );
		}

		@Override
		protected String
		getName()
		{
			return( file.getName());
		}

		@Override
		protected String
		getLinkURL()
		{
			return( stream_url );
		}

		protected long
		getTargetSize()
		{
			return( target_size );
		}

		protected long
		getEstimatedTargetSize()
		{
				// TODO: we need access to max bitrate info... and then use duration and increase by, say, 5%

			try{
				long	duration_secs = getDurationMillis()/1000;

				if ( duration_secs == 0 ){

					long length = file.getSourceFile().getLength();

					return( length * 10 );

				}else{
					long mb_per_sec = 3;	// upper limit of 3 MB a sec assumed

					return( duration_secs * mb_per_sec*1024*1024L );
				}
			}catch( Throwable e ){
			}

			return( 0 );
		}

		protected long
		getDurationMillis()
		{
			return( file.getDurationMillis());
		}

		@Override
		public long
		getCreationMillis()
		{
			return( creation_millis );
		}

		protected String
		getCaptureDate()
		{
			return( "0x" + Long.toString( creation_millis/1000, 16 ));
		}

		@Override
		protected boolean
		isContainer()
		{
			return( false );
		}
	}
}
