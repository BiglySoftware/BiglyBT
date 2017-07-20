/*
 * Created on 27-Apr-2004
 * Created by Paul Gardner
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

package com.biglybt.core.html.impl;

/**
 * @author parg
 *
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import com.biglybt.core.html.HTMLException;
import com.biglybt.core.html.HTMLPage;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;

public class
HTMLPageImpl
	extends HTMLChunkImpl
	implements HTMLPage
{
	public
	HTMLPageImpl(
		InputStream		is,
		String			charset,
		boolean			close_file )

		throws HTMLException
	{
		BufferedReader	br = null;

		StringBuilder res = new StringBuilder(1024);

		try{

			if ( charset == null ){

				br = new BufferedReader( new InputStreamReader(is));
			}else{

				br = new BufferedReader( new InputStreamReader(is, charset));
			}
			while(true){

				String	line = br.readLine();

				if ( line == null ){

					break;
				}

				res.append( line );
			}

			setContent( res.toString());

		}catch( IOException e ){

			throw( new HTMLException( "Error reading HTML page", e ));

		}finally{

			if ( br != null && close_file ){

				try{

					br.close();

				}catch( IOException e ){

					Debug.printStackTrace( e );
				}
			}
		}
	}
	@Override
	public URL
	getMetaRefreshURL()
	{
		return( getMetaRefreshURL( null ));
	}

	@Override
	public URL
	getMetaRefreshURL(
		URL		base_url )
	{
	       // <META HTTP-EQUIV="refresh" content="5; URL=xxxxxxx">;

		String[]	tags = getTags( "META" );

		for (int i=0;i<tags.length;i++){

			String	tag 	= tags[i];

			String	lc_tag	= tag.toLowerCase( MessageText.LOCALE_ENGLISH );

			int pos = lc_tag.indexOf("http-equiv=\"refresh\"");

			int	url_start = lc_tag.indexOf( "url=" );

			if ( pos != -1 && url_start != -1 ){

				url_start += 4;

				int	e1 = lc_tag.indexOf( "\"", url_start );

				if ( e1 != -1 ){

					try{
						String mr_url = tag.substring(url_start, e1).trim();

						String lc = mr_url.toLowerCase();

						if ( ! ( lc.startsWith( "http:" ) || lc.startsWith( "https:" ))){

							if ( base_url != null ){

								String s = base_url.toExternalForm();

								int p = s.indexOf( '?' );

								if ( p != -1 ){

									s = s.substring( 0, p );
								}

								if ( s.endsWith( "/" ) && mr_url.startsWith( "/" )){

									mr_url = mr_url.substring( 1 );
								}

								mr_url = s + mr_url;
							}
						}

						return( new URL( mr_url ));

					}catch( MalformedURLException e ){

						Debug.printStackTrace( e );
					}
				}
			}
		}

		return( null );
	}
}
