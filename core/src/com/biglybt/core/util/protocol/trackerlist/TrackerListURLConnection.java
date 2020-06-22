/* *
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.util.protocol.trackerlist;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;


/**
 * @author parg
 *
 */

public class
TrackerListURLConnection
	extends HttpURLConnection
{
	private static final String	NL			= "\r\n";

	private final String	list_url;
	
	private boolean		loaded;
	private String		list;
	private IOException	error;
	
	public
	TrackerListURLConnection(
		URL		_url )
	{
		super( _url );
		
		list_url = _url.toExternalForm().substring( 12 );
	}

	@Override
	public void
	connect()

		throws IOException
	{
		synchronized( this ){
	
			if ( !loaded ){
				
				loaded = true;
				
				try{
					list	= readList( list_url );
					
				}catch( IOException e ){
					
					error = e;
				}
			}
		}
		
		if ( error != null ){
			
			throw( error );
		}
	}

	@Override
	public InputStream
	getInputStream()

		throws IOException
	{
		connect();
		
		return( new ByteArrayInputStream( list.getBytes( "UTF-8" )));
	}

	@Override
	public int
	getResponseCode()
	{
		try{
			connect();
			
		}catch( Throwable e ){
			
		}
		
		return( list==null?500: HTTP_OK ); 
	}

	@Override
	public String 
	getResponseMessage() 
		throws IOException
	{
		try{
			connect();
			
		}catch( Throwable e ){
			
		}
		
		return( error==null?"OK":Debug.getNestedExceptionMessage( error )); 
	}

	@Override
	public boolean
	usingProxy()
	{
		return( false );
	}

	@Override
	public void
	disconnect()
	{
	}
	
	private static Map<String,Long>	last_downloads = new HashMap<>();
	
	private static synchronized String
	readList(
		String		url_str )
	
		throws IOException
	{
		if ( url_str.contains( "info_hash=" )){
			
			throw( new IOException( "Tracker list URLs can't be directly used as announce URLs" ));
		}
		
		String key = "tl_" + Base32.encode( url_str.getBytes( "UTF-8" )) + ".txt";
		
		long	now = SystemTime.getMonotonousTime();
		
		URL url = new URL( url_str );
		
		boolean do_cache = !url.getProtocol().equals( "file" );
		
		File cache_dir = FileUtil.newFile( SystemProperties.getUserPath(), "cache" );

		if ( !cache_dir.exists()){
			
			cache_dir.mkdirs();
		}
		
		File cache_file = FileUtil.newFile( cache_dir, key );
		
		if ( do_cache ){
			
			long cache_time = cache_file.exists()?60*60*1000:5*60*1000;
			
			Long last = last_downloads.get( key );

			if ( last != null && now - last < cache_time ){

				if ( cache_file.exists()){
						
					try{
						String result = FileUtil.readFileAsString( cache_file, 32*1024, "UTF-8" );
							
						return( result );
						
					}catch( Throwable e ){
						
						cache_file.delete();
					}
				}else{
					
					return( "" );
				}
			}
			
			
			last_downloads.put( key, now );
		}
			
		try{
			ResourceDownloader rd = ResourceDownloaderFactoryImpl.getSingleton().create( url );
					
			rd.setProperty( "URL_Connect_Timeout", 20*1000 );

			rd.setProperty( "URL_Read_Timeout", 10*1000 );

			InputStream is = rd.download();

			try{
				String result = FileUtil.readInputStreamAsString( is, 32*1024, "UTF-8" );
				
					// in the future mebe deal with various formats here
				
				if ( do_cache ){
					
					FileUtil.writeStringAsFile( cache_file, result );
				}
				
				return( result );
				
			}finally{
							
				is.close();
			}
			
		}catch( Throwable e ){
		
			Logger.log( new LogAlert(true,LogAlert.AT_ERROR, "Failed to load Tracker List from  '" + url_str + "'", e ));
			
			if ( do_cache && cache_file.exists()){
				
				try{
					String result = FileUtil.readFileAsString( cache_file, 32*1024, "UTF-8" );
						
					return( result );
					
				}catch( Throwable f ){
					
					cache_file.delete();
				}
			}
			
			if ( e instanceof IOException ){
				
				throw((IOException)e);
				
			}else{
		
				throw( new IOException( Debug.getNestedExceptionMessage( e )));
			}
		}
	}
}
