/*
 * Created on 03-Mar-2005
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

package com.biglybt.pifimpl.local.ddb;


import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ddb.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.plugin.dht.DHTPluginProgressListener;

/**
 * @author parg
 *
 */

public class
DDBaseTTTorrent
	implements DistributedDatabaseTransferType, DistributedDatabaseTransferHandler
{
	private static final boolean	TRACE			= false;

	private static final byte	CRYPTO_VERSION	= 1;

	static{
		if ( TRACE ){
			System.out.println( "**** Torrent xfer tracing on ****" );
		}
	}

	private DDBaseImpl		ddb;

	private TorrentAttribute	ta_sha1;

	private boolean				crypto_tested;
	private boolean				crypto_available;

	private List				external_downloads;

	private Map	data_cache =
		new LinkedHashMap(5,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry eldest)
			{
				return size() > 5;
			}
		};

	protected
	DDBaseTTTorrent(
		DDBaseImpl		_ddb )
	{
		ddb					= _ddb;
	}

	public void
	addDownload(
		Download		download )
	{
		synchronized( this ){

			if ( external_downloads == null ){

				external_downloads = new ArrayList();
			}

			external_downloads.add( download );
		}
	}

	public void
	removeDownload(
		Download		download )
	{
		synchronized( this ){

			if ( external_downloads != null ){

				external_downloads.remove( download );

				if ( external_downloads.size() == 0 ){

					external_downloads = null;
				}
			}
		}
	}

		// server side read

	@Override
	public DistributedDatabaseValue
	read(
		DistributedDatabaseContact			contact,
		DistributedDatabaseTransferType		type,
		DistributedDatabaseKey				key )

		throws DistributedDatabaseException
	{
		
			// We use sha1(hash) as the key for torrent downloads
			// and encrypt the torrent content using the hash as the basis for a key. This
			// prevents someone without the hash from downloading the torrent

		try{
			byte[]	search_key = ((DDBaseKeyImpl)key).getBytes();

			Download 	download = null;

			PluginInterface pi = PluginInitializer.getDefaultInterface();

			String	search_sha1 = pi.getUtilities().getFormatters().encodeBytesToString( search_key );

			if ( ta_sha1 == null ){

				ta_sha1 = pi.getTorrentManager().getPluginAttribute( "DDBaseTTTorrent::sha1");
			}

				// gotta look for the sha1(hash)

			Download[]	downloads = pi.getDownloadManager().getDownloads();

			for (int i=0;i<downloads.length;i++){

				Download	dl = downloads[i];

				if ( dl.getTorrent() == null ){

					continue;
				}

				if ( dl.getFlag( Download.FLAG_METADATA_DOWNLOAD )){
					
					continue;
				}
				
				String	sha1 = dl.getAttribute( ta_sha1 );

				if ( sha1 == null ){

					sha1 = pi.getUtilities().getFormatters().encodeBytesToString(
								new SHA1Simple().calculateHash( dl.getTorrent().getHash()));

					dl.setAttribute( ta_sha1, sha1 );
				}

				if ( sha1.equals( search_sha1 )){

					download	= dl;

					break;
				}
			}

			if ( download == null ){

				synchronized( this ){

					if ( external_downloads != null ){

						for (int i=0;i<external_downloads.size();i++){

							Download	dl = (Download)external_downloads.get(i);

							if ( dl.getTorrent() == null ){

								continue;
							}

							String	sha1 = dl.getAttribute( ta_sha1 );

							if ( sha1 == null ){

								sha1 = pi.getUtilities().getFormatters().encodeBytesToString(
											new SHA1Simple().calculateHash( dl.getTorrent().getHash()));

								dl.setAttribute( ta_sha1, sha1 );
							}

							if ( sha1.equals( search_sha1 )){

								download	= dl;

								break;
							}
						}
					}
				}
			}

			String	originator = contact.getName() + " (" + contact.getAddress() + ")";

			if ( download == null ){

				String msg = "TorrentDownload: request from " + originator + " for '" + pi.getUtilities().getFormatters().encodeBytesToString( search_key ) + "' not found";

				if ( TRACE ){

					System.out.println( msg );
				}

				ddb.log( msg );

					// torrent not found - probably been removed whilst info still published in DHT

				return( null );

			}

			if ( !ddb.isTorrentXferEnabled()){
				

				ddb.log( "TorrentDownload: request from " + originator + "  for '" + download.getName() + "' denied as torrent transfer is disabled" );

				return( null );
			}

			Torrent	torrent = download.getTorrent();

			if ( torrent.isPrivate()){

				Debug.out( "Attempt to download private torrent" );

				ddb.log( "TorrentDownload: request from " + originator + "  for '" + download.getName() + "' denied as it is private" );

					// should never happen as private torrents are not tracked so they can't be found for
					// download

				return( null );
			}

			try{
					// apparently there are some trackers using non-private torrents with passkeys. Crazy, however to give users at
					// least the opportunity to prevent .torrent transfer for these torrents we deny this if the DHT peer source has
					// been disabled by the user

				DownloadManager dm = PluginCoreUtils.unwrapIfPossible( download );

				if ( dm != null ){
					
					DownloadManagerState dms = dm.getDownloadState();
					
					if ( !dms.isPeerSourceEnabled( PEPeerSource.PS_DHT )){
	
						ddb.log( "TorrentDownload: request from " + originator + "  for '" + download.getName() + "' denied as DHT peer source disabled" );
	
						return( null );
					}
					
					if ( dms.getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
						
						return( null );
					}
				}
			}catch( Throwable e ){

				Debug.out( e );
			}

			String	msg = "TorrentDownload: request from " + originator + "  for '" + download.getName() + "' OK";

			if ( TRACE ){

				System.out.println( msg );
			}

			ddb.log( msg );

			HashWrapper	hw = new HashWrapper( torrent.getHash());

			synchronized( data_cache ){

				Object[]	data = (Object[])data_cache.get( hw );

				if ( data != null ){

					data[1] = new Long( SystemTime.getCurrentTime());

					return( ddb.createValue((byte[])data[0]));
				}
			}


			torrent = torrent.removeAdditionalProperties();

				// when clients get a torrent from the DHT they take on
				// responsibility for tracking it too

			torrent.setDecentralisedBackupRequested( true );

			byte[] data = torrent.writeToBEncodedData();

			data = encrypt( torrent.getHash(), data );

			if ( data == null ){

				return( null );
			}

			synchronized( data_cache ){

				if ( data_cache.size() == 0 ){

					final TimerEventPeriodic[]pe = { null };

					pe[0] = SimpleTimer.addPeriodicEvent(
						"DDBTorrent:timeout",
						30*1000,
						new TimerEventPerformer()
						{
							@Override
							public void
							perform(
								TimerEvent	event )
							{
								long now = SystemTime.getCurrentTime();

								synchronized( data_cache ){

									Iterator	it = data_cache.values().iterator();

									while( it.hasNext()){

										long	time = ((Long)((Object[])it.next())[1]).longValue();

										if ( now < time || now - time > 120*1000 ){

											it.remove();
										}
									}

									if ( data_cache.size() == 0 ){

										pe[0].cancel();
									}
								}
							}
						});
				}

				data_cache.put( hw, new Object[]{ data, new Long( SystemTime.getCurrentTime())});
			}

			return( ddb.createValue( data ));

		}catch( Throwable e ){

			throw( new DistributedDatabaseException("Torrent write fails", e ));
		}
	}

		// server side write

	@Override
	public DistributedDatabaseValue
	write(
		DistributedDatabaseContact			contact,
		DistributedDatabaseTransferType		type,
		DistributedDatabaseKey				key,
		DistributedDatabaseValue			value )

		throws DistributedDatabaseException
	{
		throw( new DistributedDatabaseException( "not supported" ));
	}

		// client side read

	protected DistributedDatabaseValue
	read(
		DDBaseContactImpl							contact,
		final DistributedDatabaseProgressListener	listener,
		DistributedDatabaseTransferType				type,
		DistributedDatabaseKey						key,
		long										timeout )

		throws DistributedDatabaseException
	{
		byte[]	torrent_hash	= ((DDBaseKeyImpl)key).getBytes();

		byte[]	lookup_key	= new SHA1Simple().calculateHash( torrent_hash );

		if ( TRACE ){
			System.out.println( "TorrentXfer: sending via sha1(hash)" );
		}

		byte[]	data = contact.getContact().read(
							listener == null ? null : new DHTPluginProgressListener()
							{
								@Override
								public void
								reportSize(
									long	size )
								{
									listener.reportSize( size );
								}

								@Override
								public void
								reportActivity(
									String	str )
								{
									listener.reportActivity( str );
								}

								@Override
								public void
								reportCompleteness(
									int		percent )
								{
									listener.reportCompleteness( percent );
								}
							},
							DDBaseHelpers.getKey(type).getHash(),
							lookup_key,
							timeout );

		if ( data == null ){

			return( null );
		}

		data = decrypt( torrent_hash, data );

		if ( data == null ){

			return( null );
		}

		return( new DDBaseValueImpl( contact, data, SystemTime.getCurrentTime(), -1));
	}

	protected byte[]
   	encrypt(
   		byte[]		hash,
   		byte[]		data )
   	{
		if ( !testCrypto()){

			return( null );
		}

   		byte[]	enc = doCrypt( Cipher.ENCRYPT_MODE, hash, data, 0 );

		if ( enc == null ){

			if ( TRACE ){

				System.out.println( "TorrentXfer: encryption failed, using plain" );
			}

			byte[]	res = new byte[data.length+2];

			res[0] = CRYPTO_VERSION;
			res[1] = 0;	// not encrypted

			System.arraycopy( data, 0, res, 2, data.length );

			return( res );

		}else{

			if ( TRACE ){

				System.out.println( "TorrentXfer: encryption ok" );
			}

			byte[]	res = new byte[enc.length+2];

			res[0] = CRYPTO_VERSION;
			res[1] = 1;	// encrypted

			System.arraycopy( enc, 0, res, 2, enc.length );

			return( res );
		}
   	}

	protected byte[]
  	decrypt(
  		byte[]		hash,
  		byte[]		data )
  	{
		if ( !testCrypto()){

			return( null );
		}

		if ( data[0] != CRYPTO_VERSION ){

			Debug.out( "Invalid crypto version received" );

			return( data );
		}

		if ( data[1] == 0 ){

				// encryption failed, in plain

			if ( TRACE ){
				System.out.println( "TorrentXfer: encryption failed, retrieving plain" );
			}

			byte[]	res = new byte[data.length-2];

			System.arraycopy( data, 2, res, 0, res.length );

			return( res );

		}else{

			if ( TRACE ){
				System.out.println( "TorrentXfer: encryption ok, decrypting" );
			}

			byte[]	res =  doCrypt( Cipher.DECRYPT_MODE, hash, data, 2 );

			return( res );
		}
  	}

	protected byte[]
	doCrypt(
		int			mode,
		byte[]		hash,
		byte[]		data,
		int			data_offset )
	{
		try{
			byte[]	key_data = new byte[24];

				// hash is 20 bytes so we've got 4 zeros at the end. tough

			System.arraycopy( hash, 0, key_data, 0, hash.length );

			SecretKey tdes_key = new SecretKeySpec( key_data, "DESede" );

			Cipher cipher = Cipher.getInstance("DESede");  // Triple-DES encryption

			cipher.init(mode, tdes_key );

			return( cipher.doFinal(data, data_offset, data.length - data_offset ));

		}catch( Throwable e ){

			Debug.out( e );

			return( null );
		}
	}

	protected boolean
	testCrypto()
	{
		if ( !crypto_tested ){

			crypto_tested	= true;

			try{
				Cipher.getInstance("DESede");  // Triple-DES encryption

				crypto_available	= true;

			}catch( Throwable e ){

				Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
						"Unable to initialise cryptographic framework for magnet-based "
								+ "torrent downloads, please re-install Java", e));
			}
		}

		return( crypto_available );
	}
}
