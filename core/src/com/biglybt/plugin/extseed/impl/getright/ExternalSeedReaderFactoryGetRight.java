/*
 * Created on 15-Dec-2005
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

package com.biglybt.plugin.extseed.impl.getright;

import java.net.URL;
import java.util.*;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.plugin.extseed.ExternalSeedPlugin;
import com.biglybt.plugin.extseed.ExternalSeedReader;
import com.biglybt.plugin.extseed.ExternalSeedReaderFactory;

public class
ExternalSeedReaderFactoryGetRight
	implements ExternalSeedReaderFactory
{
	public
	ExternalSeedReaderFactoryGetRight()
	{
	}

	@Override
	public ExternalSeedReader[]
	getSeedReaders(
		ExternalSeedPlugin plugin,
		Download				download )
	{
		return( getSeedReaders( plugin, download.getName(), download.getTorrent()));
	}

	@Override
	public ExternalSeedReader[]
   	getSeedReaders(
   		ExternalSeedPlugin		plugin,
   		Torrent					torrent )
  	{
		return( getSeedReaders( plugin, torrent.getName(), torrent ));
  	}

	private ExternalSeedReader[]
   	getSeedReaders(
   		ExternalSeedPlugin		plugin,
   		String					name,
   		Torrent					torrent )
  	{
  		try{
  			Map	config = new HashMap();

  			Object	obj = torrent.getAdditionalProperty( "url-list" );

  			if ( obj != null ){

  				config.put( "url-list", obj );
  			}

 			obj = torrent.getAdditionalProperty( "url-list-params" );

  			if ( obj != null ){

  				config.put( "url-list-params", obj );
  			}

			obj = torrent.getAdditionalProperty( "url-list-params2" );

  			if ( obj != null ){

  				config.put( "url-list-params2", obj );
  			}

  			return( getSeedReaders( plugin, name, torrent, config ));

  		}catch( Throwable e ){

			e.printStackTrace();
		}

		return( new ExternalSeedReader[0] );
	}

	@Override
	public ExternalSeedReader[]
  	getSeedReaders(
  		ExternalSeedPlugin		plugin,
  		Download				download,
  		Map						config )
  	{
		return( getSeedReaders( plugin, download.getName(), download.getTorrent(), config ));
  	}

	private ExternalSeedReader[]
  	getSeedReaders(
  		ExternalSeedPlugin		plugin,
  		String					name,
  		Torrent					torrent,
  		Map						config )
	{
		try{
			Object	obj = config.get( "url-list" );

            /* resolve url-list according to specification
             * (http://www.getright.com/seedtorrent.html)
             */
            if ( obj instanceof byte[] ){
                List l = new ArrayList();
                l.add(obj);
                obj = l;
            }

			if ( obj instanceof List ){

				List	urls = (List)obj;

				List	readers = new ArrayList();

				Object	_global_params 		= config.get( "url-list-params" );
				Object	_specific_params 	= config.get( "url-list-params2" );

				Map		global_params 		= _global_params instanceof Map?(Map)_global_params:new HashMap();
				List	specific_params 	= _specific_params instanceof List?(List)_specific_params:new ArrayList();

				Collections.shuffle( urls );

				for (int i=0;i<urls.size();i++){

					if ( readers.size() > 10 ){

						break;
					}

					Map my_params = global_params;

					if ( i < specific_params.size()){

						Object o = specific_params.get(i);

						if ( o instanceof Map ){

							my_params = (Map)o;
						}
					}

					try{
						String	url_str = new String((byte[])urls.get(i), "UTF-8" );

							// avoid java encoding ' ' as '+' as this is not conformant with Apache (for example)

						url_str = url_str.replaceAll( " ", "%20");

						if ( url_str.length() > 0 ){

								// internet-archive torrents have started embedding 'relative' urls which we don't
								// support
							
							if ( url_str.startsWith( "/" )){
								
								continue;
							}
							
							URL	url = new URL( url_str );

							String	protocol = url.getProtocol().toLowerCase();

							if ( protocol.startsWith( "http" )){

								readers.add( new ExternalSeedReaderGetRight(plugin, torrent, url, my_params ));

							}else{

								plugin.log( name + ": GR unsupported protocol: " + url );
							}
						}
					}catch( Throwable e ){

						Object o = urls.get(i);

						String str = (o instanceof byte[])?new String((byte[])o):String.valueOf(o);

						Debug.out( "GR seed invalid: " + str, e );
					}
				}

				ExternalSeedReader[]	res = new ExternalSeedReader[ readers.size() ];

				readers.toArray( res );

				return( res );
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}

		return( new ExternalSeedReader[0] );
	}
}
