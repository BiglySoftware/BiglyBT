/*
 * Created on 18-Feb-2005
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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ddb.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.dht.*;

/**
 * @author parg
 *
 */

public class
DDBaseImpl
	implements DistributedDatabase
{
	private static DDBaseImpl	singleton;

	protected static AEMonitor		class_mon	= new AEMonitor( "DDBaseImpl:class");

	private Map<HashWrapper,DistributedDatabaseTransferHandler>			transfer_map = new HashMap<>();

	private static boolean prefer_i2p;
	
	static{	
		COConfigurationManager.addAndFireParameterListener(
			"Plugin.DHT.dht.prefer.i2p",
			new ParameterListener(){
				
				@Override
				public void parameterChanged(String name){
				
					prefer_i2p = COConfigurationManager.getBooleanParameter( name, false );
				}
			});
	}
	
	public static DDBaseImpl
	getSingleton(
		Core core )
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton = new DDBaseImpl( core );
			}
		}finally{

			class_mon.exit();
		}

		return( singleton );
	}

	private static Map<DHTPluginInterface,DistributedDatabase> dht_pi_map = new HashMap<>();

	public static List<DistributedDatabase>
	getDDBs(
		Download		download )
	{
		List<DistributedDatabase>	result = new ArrayList<>();

		String[] networks = PluginCoreUtils.unwrap( download ).getDownloadState().getNetworks();

		for ( String net: networks ){

			if ( net == AENetworkClassifier.AT_PUBLIC ){

				DistributedDatabase ddb = getSingleton( CoreFactory.getSingleton());

				if ( ddb.isAvailable()){

					result.add( ddb );
				}

			}else{

				Map<String,Object>	options = new HashMap<>();

				options.put( AEProxyFactory.DP_DOWNLOAD, download );

				DHTPluginInterface dpi = AEProxyFactory.getPluginDHTProxy( "ddb", net, options );

				if ( dpi != null ){

					DistributedDatabase ddb;

					synchronized( dht_pi_map ){

						ddb = dht_pi_map.get( dpi );

						if ( ddb == null ){

							ddb = new DDBaseImpl( net, dpi );

							dht_pi_map.put( dpi, ddb );
						}
					}

					if ( ddb.isAvailable()){

						result.add( ddb );
					}
				}
			}
		}
		
		if ( prefer_i2p && result.size() > 1 ){
			
			Iterator<DistributedDatabase> it = result.iterator();
				
			while( it.hasNext()){
				
				if ( it.next().getNetwork() == AENetworkClassifier.AT_PUBLIC ){
					
					it.remove();
				}
			}
		}

		return( result );
	}

	public static List<DistributedDatabase>
	getDDBs(
		String[]			networks )
	{
		return( getDDBs( networks, null ));
	}

	public static List<DistributedDatabase>
	getDDBs(
		String[]			networks,
		Map<String,Object>	_options )
	{
		List<DistributedDatabase>	result = new ArrayList<>();

		for ( String net: networks ){

			if ( net == AENetworkClassifier.AT_PUBLIC ){

				DistributedDatabase ddb = getSingleton( CoreFactory.getSingleton());

				if ( ddb.isAvailable()){

					result.add( ddb );
				}

			}else{

				Map<String,Object>	options = new HashMap<>();

				options.put( AEProxyFactory.DP_NETWORKS, networks );

				if ( _options != null ){

					options.putAll( _options );
				}

				DHTPluginInterface dpi = AEProxyFactory.getPluginDHTProxy( "ddb", net, options );

				if ( dpi != null ){

					DistributedDatabase ddb;

					synchronized( dht_pi_map ){

						ddb = dht_pi_map.get( dpi );

						if ( ddb == null ){

							ddb = new DDBaseImpl( net, dpi );

							dht_pi_map.put( dpi, ddb );
						}
					}

					if ( ddb.isAvailable()){

						result.add( ddb );
					}
				}
			}
		}

		if ( prefer_i2p && result.size() > 1 ){
			
			Iterator<DistributedDatabase> it = result.iterator();
				
			while( it.hasNext()){
				
				if ( it.next().getNetwork() == AENetworkClassifier.AT_PUBLIC ){
					
					it.remove();
				}
			}
		}
		
		return( result );
	}

	final private Core core;
	final private DDBaseTTTorrent			torrent_transfer;
	final private String					network;

	private DHTPluginInterface				dht_use_accessor;


	private CopyOnWriteList<DistributedDatabaseListener>	listeners = new CopyOnWriteList<>();

	protected
	DDBaseImpl(
		final Core _core )
	{
		core	= _core;

		torrent_transfer =  new DDBaseTTTorrent( this );

		network	= AENetworkClassifier.AT_PUBLIC;

		grabDHT();
	}

	protected
	DDBaseImpl(
		String					_net,
		DHTPluginInterface		_dht )
	{
		network				= _net;
		dht_use_accessor	= _dht;

		core		= null;
		torrent_transfer 	=  new DDBaseTTTorrent( this );
	}

	@Override
	public String
	getNetwork()
	{
		return( network );
	}

	@Override
	public DHTPluginInterface
	getDHTPlugin()
	{
		return( dht_use_accessor );
	}

	public DDBaseTTTorrent
	getTTTorrent()
	{
		return( torrent_transfer );
	}

	protected DHTPluginInterface
	grabDHT()
	{
		if ( dht_use_accessor != null ){

			return( dht_use_accessor );
		}

		try{
			class_mon.enter();

			if ( dht_use_accessor == null ){

				PluginInterface dht_pi =
					core.getPluginManager().getPluginInterfaceByClass(
								DHTPlugin.class );

				if ( dht_pi != null ){

					dht_use_accessor = (DHTPluginInterface)dht_pi.getPlugin();

					if ( dht_use_accessor.isEnabled()){

						dht_use_accessor.addListener(
							new DHTPluginListener()
							{
								@Override
								public void
								localAddressChanged(
									DHTPluginContact	local_contact )
								{
									List<DistributedDatabaseListener> list = listeners.getList();

									dbEvent ev = new dbEvent( DistributedDatabaseEvent.ET_LOCAL_CONTACT_CHANGED );

									for ( DistributedDatabaseListener l: list){

										try{
											l.event( ev );

										}catch( Throwable e ){

											Debug.out( e );
										}
									}
								}
							});

						try{
							addTransferHandler(	torrent_transfer, torrent_transfer );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}
			}
		}finally{

			class_mon.exit();
		}

		return( dht_use_accessor );
	}

	@Override
	public boolean
	isAvailable()
	{
		DHTPluginInterface	dht = grabDHT();

		if ( dht == null ){

			return( false );
		}

		return( dht.isEnabled());
	}

	@Override
	public boolean
	isInitialized()
	{
		DHTPluginInterface	dht = grabDHT();

		if ( dht == null ){

			return( false );
		}

		return( !dht.isInitialising());
	}


	@Override
	public boolean
	isExtendedUseAllowed()
	{
		DHTPluginInterface	dht = grabDHT();

		if ( dht == null ){

			return( false );
		}

		return( dht.isExtendedUseAllowed());
	}

	protected boolean
	isTorrentXferEnabled()
	{
			// must use the public DHTPlugin to grab parameter setting
		
		PluginInterface dht_pi =
				core.getPluginManager().getPluginInterfaceByClass(
							DHTPlugin.class );
	
		if ( dht_pi == null ){
			
			return( false );
		}
		
		return(((DHTPlugin)dht_pi.getPlugin()).isTorrentXferEnabled());
	}
	
	@Override
	public DistributedDatabaseContact
	getLocalContact()
	{
		DHTPluginInterface	dht = grabDHT();

		if ( dht == null ){

			return( null );
		}

		return( new DDBaseContactImpl( this, dht.getLocalAddress()));
	}

	protected void
	throwIfNotAvailable()

		throws DistributedDatabaseException
	{
		if ( !isAvailable()){

			throw( new DistributedDatabaseException( "DHT not available" ));
		}
	}

	protected DHTPluginInterface
	getDHT()

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		return( grabDHT());
	}

	protected void
	log(
		String	str )
	{
		DHTPluginInterface	dht = grabDHT();

		if ( dht != null ){

			dht.log( str );
		}
	}

	@Override
	public DistributedDatabaseKey
	createKey(
		Object			key )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		return( new DDBaseKeyImpl( key ));
	}

	@Override
	public DistributedDatabaseKey
	createKey(
		Object			key,
		String			description )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		return( new DDBaseKeyImpl( key, description ));
	}

	@Override
	public DistributedDatabaseValue
	createValue(
		Object			value )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		return( new DDBaseValueImpl( new DDBaseContactImpl( this, getDHT().getLocalAddress()), value, SystemTime.getCurrentTime(), -1));
	}

	@Override
	public DistributedDatabaseContact
	importContact(
		InetSocketAddress				address )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		DHTPluginContact	contact = getDHT().importContact( address );

		if ( contact == null ){

			throw( new DistributedDatabaseException( "import of '" + address + "' failed" ));
		}

		return( new DDBaseContactImpl( this, contact));
	}

	@Override
	public DistributedDatabaseContact
	importContact(
		InetSocketAddress				address,
		byte							version )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		DHTPluginContact	contact = getDHT().importContact( address, version );

		if ( contact == null ){

			throw( new DistributedDatabaseException( "import of '" + address + "' failed" ));
		}

		return( new DDBaseContactImpl( this, contact));
	}

	@Override
	public DistributedDatabaseContact
	importContact(
		InetSocketAddress				address,
		byte							version,
		int								preferred_dht )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		/*
		if ( preferred_dht != DistributedDatabase.DHT_MAIN ){
			Debug.outNoStack( "DDB: Importing CVS contact" );
		}
		*/

		DHTPluginContact	contact = 
				getDHT().importContact( address, version, preferred_dht==DistributedDatabase.DHT_CVS );

		if ( contact == null ){

			throw( new DistributedDatabaseException( "import of '" + address + "' failed" ));
		}

		return( new DDBaseContactImpl( this, contact ));
	}

	@Override
	public DistributedDatabaseContact
	importContact(
		Map<String,Object>			map )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		DHTPluginContact	contact = getDHT().importContact( map );

		if ( contact == null ){

			throw( new DistributedDatabaseException( "import of '" + map + "' failed" ));
		}

		return( new DDBaseContactImpl( this, contact));
	}

	@Override
	public void
	write(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		DistributedDatabaseValue		value )

		throws DistributedDatabaseException
	{
		write( listener, key, new DistributedDatabaseValue[]{ value } );
	}

	@Override
	public void
	write(
		final DistributedDatabaseListener	listener,
		final DistributedDatabaseKey		key,
		final DistributedDatabaseValue		values[] )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		for (int i=0;i<values.length;i++){

			if (((DDBaseValueImpl)values[i]).getBytes().length > DDBaseValueImpl.MAX_VALUE_SIZE ){

				throw( new DistributedDatabaseException("Value size limited to " + DDBaseValueImpl.MAX_VALUE_SIZE + " bytes" ));
			}
		}

		byte	extra_flags = 0;

		int key_flags = key.getFlags();

		if ((key_flags & DistributedDatabaseKey.FL_ANON ) != 0 ){

			extra_flags |= DHT.FLAG_ANON;
		}

		if ((key_flags & DistributedDatabaseKey.FL_BRIDGED ) != 0 ){

			extra_flags |= DHT.FLAG_BRIDGED;
		}

		if ( values.length == 0 ){

			delete( listener, key );

		}else if ( values.length == 1 ){

			getDHT().put(
					((DDBaseKeyImpl)key).getBytes(),
					key.getDescription(),
					((DDBaseValueImpl)values[0]).getBytes(),
					(byte)( DHTPluginInterface.FLAG_SINGLE_VALUE | extra_flags ),
					new listenerMapper( listener, DistributedDatabaseEvent.ET_VALUE_WRITTEN, key, 0, false, false ));
		}else{


			// TODO: optimise re-publishing to avoid republishing everything each time
			/*
			DHTPluginValue	old_value = dht.getLocalValue( ((DDBaseKeyImpl)key).getBytes());

			List	old_values = new ArrayList();

			if ( old_value != null ){

				if (( old_value.getFlags() & DHTPlugin.FLAG_MULTI_VALUE ) == 0 ){

					old_values.add( old_value.getValue());

				}else{

					byte[]	encoded = old_value.getValue();


				}
			}
			*/

			byte[]	current_key = ((DDBaseKeyImpl)key).getBytes();

				// format is: <continuation> <len><len><data>

			byte[]	payload			= new byte[DHTPluginInterface.MAX_VALUE_SIZE];
			int		payload_length	= 1;

			int	pos = 0;

			while( pos < values.length ){

				DDBaseValueImpl	value = (DDBaseValueImpl)values[pos];

				byte[]	bytes = value.getBytes();

				int		len = bytes.length;

				if ( payload_length + len < payload.length - 2 ){

					payload[payload_length++] = (byte)(( len & 0x0000ff00 ) >> 8);
					payload[payload_length++] = (byte) ( len & 0x000000ff );

					System.arraycopy( bytes, 0, payload, payload_length, len );

					payload_length	+= len;

					pos++;

				}else{

					payload[0]	= 1;

					final byte[]	copy = new byte[payload_length];

					System.arraycopy( payload, 0, copy, 0, copy.length );

					final byte[]					f_current_key	= current_key;

					getDHT().put(
							f_current_key,
							key.getDescription(),
							copy,
							(byte)( DHTPluginInterface.FLAG_MULTI_VALUE | extra_flags ),
							new listenerMapper( listener, DistributedDatabaseEvent.ET_VALUE_WRITTEN, key, 0, false, false ));

					payload_length	= 1;

					current_key = new SHA1Simple().calculateHash( current_key );
				}
			}

			if ( payload_length > 1 ){

				payload[0]	= 0;

				final byte[]	copy = new byte[payload_length];

				System.arraycopy( payload, 0, copy, 0, copy.length );

				final byte[]					f_current_key	= current_key;

				getDHT().put(
						f_current_key,
						key.getDescription(),
						copy,
						(byte)( DHTPluginInterface.FLAG_MULTI_VALUE | extra_flags ),
						new listenerMapper( listener, DistributedDatabaseEvent.ET_VALUE_WRITTEN, key, 0, false, false ));
			}
		}
	}

	@Override
	public void
	read(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		long							timeout )

		throws DistributedDatabaseException
	{
		read( listener, key, timeout, OP_NONE );
	}

	@Override
	public void
	read(
		final DistributedDatabaseListener		listener,
		final DistributedDatabaseKey			key,
		final long								timeout,
		int										options )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		boolean	exhaustive  	= (options&OP_EXHAUSTIVE_READ)!=0;
		boolean	high_priority  	= (options&OP_PRIORITY_HIGH)!=0;

			// TODO: max values?

		getDHT().get(
			((DDBaseKeyImpl)key).getBytes(),
			key.getDescription(),
			(byte)0,
			256,
			timeout,
			exhaustive,
			high_priority,
			new listenerMapper( listener, DistributedDatabaseEvent.ET_VALUE_READ, key, timeout, exhaustive, high_priority ));
	}

	@Override
	public void
	readKeyStats(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		long							timeout )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		getDHT().get(
			((DDBaseKeyImpl)key).getBytes(),
			key.getDescription(),
			DHTPluginInterface.FLAG_STATS,
			256,
			timeout,
			false,
			false,
			new listenerMapper( listener, DistributedDatabaseEvent.ET_KEY_STATS_READ, key, timeout, false, false ));

	}

	@Override
	public List<DistributedDatabaseValue>
	getValues(
		DistributedDatabaseKey			key )

		throws DistributedDatabaseException
	{
		List<DHTPluginValue> values = getDHT().getValues(((DDBaseKeyImpl)key).getBytes());

		List<DistributedDatabaseValue>	result = new ArrayList<>(values.size());

		for ( DHTPluginValue v: values ){

			DDBaseContactImpl originator = null;	// currently don't have access to this...

			DDBaseValueImpl value	= new DDBaseValueImpl( originator, v.getValue(), v.getCreationTime(), v.getVersion());

			result.add( value );
		}

		return( result );
	}


	@Override
	public void
	delete(
		final DistributedDatabaseListener		listener,
		final DistributedDatabaseKey			key )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		getDHT().remove(
					((DDBaseKeyImpl)key).getBytes(),
					key.getDescription(),
					new listenerMapper( listener, DistributedDatabaseEvent.ET_VALUE_DELETED, key, 0, false, false ));
	}

	@Override
	public void
	delete(
		DistributedDatabaseListener		listener,
		DistributedDatabaseKey			key,
		DistributedDatabaseContact[]	targets )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		DHTPluginContact[]	plugin_targets = new DHTPluginContact[ targets.length ];

		for (int i=0;i<targets.length;i++){

			plugin_targets[i] = ((DDBaseContactImpl)targets[i]).getContact();
		}

		getDHT().remove(
					plugin_targets,
					((DDBaseKeyImpl)key).getBytes(),
					key.getDescription(),
					new listenerMapper( listener, DistributedDatabaseEvent.ET_VALUE_DELETED, key, 0, false, false ));
	}

	@Override
	public void
	addTransferHandler(
		final DistributedDatabaseTransferType		type,
		final DistributedDatabaseTransferHandler	handler )

		throws DistributedDatabaseException
	{
		throwIfNotAvailable();

		final HashWrapper	type_key = DDBaseHelpers.getKey( type );

		if ( transfer_map.get( type_key ) != null ){

			throw( new DistributedDatabaseException( "Handler for class '" + type.getClass().getName() + "' already defined" ));
		}

		transfer_map.put( type_key, handler );

		final String	handler_name;

		if ( type == torrent_transfer ){

			handler_name = "Torrent Transfer";

		}else{

			String class_name = type.getClass().getName();

			int	pos = class_name.indexOf( '$' );

			if ( pos != -1 ){

				class_name = class_name.substring( pos+1 );

			}else{

				pos = class_name.lastIndexOf( '.' );

				if ( pos != -1 ){

					class_name = class_name.substring( pos+1 );
				}
			}

			handler_name = "Plugin Defined (" + class_name + ")";
		}

		getDHT().registerHandler(
			type_key.getHash(),
			new DHTPluginTransferHandler()
			{
				@Override
				public String
				getName()
				{
					return( handler_name );
				}

				@Override
				public byte[]
				handleRead(
					DHTPluginContact	originator,
					byte[]				xfer_key )
				{
					try{
						DDBaseValueImpl	res = (DDBaseValueImpl)
							handler.read(
									new DDBaseContactImpl( DDBaseImpl.this, originator ),
									type,
									new DDBaseKeyImpl( xfer_key ));

						if ( res == null ){

							return( null );
						}

						return( res.getBytes());

					}catch( Throwable e ){

						Debug.printStackTrace(e);

						return( null );
					}
				}

				@Override
				public byte[]
				handleWrite(
					DHTPluginContact	originator,
					byte[]				xfer_key,
					byte[]				value )
				{
					try{
						DDBaseContactImpl	contact = new DDBaseContactImpl( DDBaseImpl.this, originator );

						DDBaseValueImpl	res = (DDBaseValueImpl)
							handler.write(
								contact,
								type,
								new DDBaseKeyImpl( xfer_key ),
								new DDBaseValueImpl( contact, value, SystemTime.getCurrentTime(), -1));

						if ( res == null ){

							return( null );
						}

						return( res.getBytes());

					}catch( Throwable e ){

						Debug.printStackTrace(e);

						return( null );
					}
				}
			}, null );
	}

	@Override
	public DistributedDatabaseTransferType
	getStandardTransferType(
		int		standard_type )

		throws DistributedDatabaseException
	{
		if ( standard_type == DistributedDatabaseTransferType.ST_TORRENT ){

			return( torrent_transfer );
		}

		throw( new DistributedDatabaseException( "unknown type" ));
	}

	protected DistributedDatabaseValue
	read(
		DDBaseContactImpl							contact,
		final DistributedDatabaseProgressListener	listener,
		DistributedDatabaseTransferType				type,
		DistributedDatabaseKey						key,
		long										timeout )

		throws DistributedDatabaseException
	{
		if ( type == torrent_transfer ){

			return( torrent_transfer.read( contact, listener, type, key, timeout ));

		}else{

			DHTPluginContact plugin_contact = contact.getContact();

			byte[]	data = plugin_contact.read(
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
								((DDBaseKeyImpl)key).getBytes(),
								timeout );

			if ( data == null ){

				return( null );
			}

			return( new DDBaseValueImpl( contact, data, SystemTime.getCurrentTime(), -1));
		}
	}

	protected void
	write(
		DDBaseContactImpl							contact,
		final DistributedDatabaseProgressListener	listener,
		DistributedDatabaseTransferType				type,
		DistributedDatabaseKey						key,
		DistributedDatabaseValue					value,
		long										timeout )

		throws DistributedDatabaseException
	{
		DHTPluginContact plugin_contact = contact.getContact();

		plugin_contact.write(
			listener == null ? null :
			new DHTPluginProgressListener()
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
			((DDBaseKeyImpl)key).getBytes(),
			((DDBaseValueImpl)value).getBytes(),
			timeout );
	}

	protected DistributedDatabaseValue
	call(
		DDBaseContactImpl							contact,
		final DistributedDatabaseProgressListener	listener,
		DistributedDatabaseTransferType				type,
		DistributedDatabaseValue					value,
		long										timeout )

		throws DistributedDatabaseException
	{
		DHTPluginContact plugin_contact = contact.getContact();

		byte[]	data = plugin_contact.call(
				listener == null ? null :
							new DHTPluginProgressListener()
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
							((DDBaseValueImpl)value).getBytes(),
							timeout );

		if ( data == null ){

			return( null );
		}

		return( new DDBaseValueImpl( contact, data, SystemTime.getCurrentTime(), -1));
	}

	@Override
	public void
	addListener(
		DistributedDatabaseListener l )
	{
		listeners.add( l );
	}

	@Override
	public void
	removeListener(
		DistributedDatabaseListener l )
	{
		listeners.remove( l );
	}

	protected class
	listenerMapper
		implements DHTPluginOperationListener
	{
		private DistributedDatabaseListener	listener;
		private int							type;
		private DistributedDatabaseKey		key;
		private byte[]						key_bytes;
		private long						timeout;
		private boolean						complete_disabled;
		private boolean						exhaustive;
		private boolean						high_priority;

		private int							continuation_num;

		protected
		listenerMapper(
			DistributedDatabaseListener	_listener,
			int							_type,
			DistributedDatabaseKey		_key,
			long						_timeout,
			boolean						_exhaustive,
			boolean						_high_priority )
		{
			listener	= _listener;
			type		= _type;
			key			= _key;
			key_bytes	= ((DDBaseKeyImpl)key).getBytes();
			timeout		= _timeout;
			exhaustive	= _exhaustive;
			high_priority	= _high_priority;

			continuation_num	= 1;
		}

		private
		listenerMapper(
			DistributedDatabaseListener	_listener,
			int							_type,
			DistributedDatabaseKey		_key,
			byte[]						_key_bytes,
			long						_timeout,
			int							_continuation_num )
		{
			listener	= _listener;
			type		= _type;
			key			= _key;
			key_bytes	= _key_bytes;
			timeout		= _timeout;

			continuation_num	= _continuation_num;
		}

		@Override
		public boolean
		diversified()
		{
			listener.event( new dbEvent( DistributedDatabaseEvent.ET_DIVERSIFIED, key ));
			
			return( true );
		}

		@Override
		public void
		starts(
			byte[] 	_key )
		{
			listener.event( new dbEvent( DistributedDatabaseEvent.ET_OPERATION_STARTS, key ));
		}

		@Override
		public void
		valueRead(
			DHTPluginContact	originator,
			DHTPluginValue		_value )
		{
			if ( type == DistributedDatabaseEvent.ET_KEY_STATS_READ ){

				if (( _value.getFlags() & DHTPluginInterface.FLAG_STATS ) == 0 ){

						// skip, old impl

					return;
				}

				try{
					final DHTPluginKeyStats	stats = getDHT().decodeStats( _value );

					if ( stats != null ){

						DistributedDatabaseKeyStats ddb_stats = new
							DistributedDatabaseKeyStats()
							{
								@Override
								public int
								getEntryCount()
								{
									return( stats.getEntryCount());
								}

								@Override
								public int
								getSize()
								{
									return( stats.getSize());
								}

								@Override
								public int
								getReadsPerMinute()
								{
									return( stats.getReadsPerMinute());
								}

								@Override
								public byte
								getDiversification()
								{
									return( stats.getDiversification());
								}
							};

						listener.event( new dbEvent( type, key, originator, ddb_stats ));
					}
				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}else{
				byte[]	value = _value.getValue();

				if ( _value.getFlags() == DHTPluginInterface.FLAG_MULTI_VALUE ){

					int	pos = 1;

					while( pos < value.length ){

						int	len = (	( value[pos++]<<8 ) & 0x0000ff00 )+
								 	( value[pos++] & 0x000000ff );

						if ( len > value.length - pos ){

							Debug.out( "Invalid length: len = " + len + ", remaining = " + (value.length - pos ));

							break;
						}

						byte[]	d = new byte[len];

						System.arraycopy( value, pos, d, 0, len );

						listener.event( new dbEvent( type, key, originator, d, _value.getCreationTime(), _value.getVersion()));

						pos += len;
					}

					if ( value[0] == 1 ){

							// continuation exists

						final	byte[]	next_key_bytes = new SHA1Simple().calculateHash( key_bytes );

						complete_disabled	= true;

						grabDHT().get(
							next_key_bytes,
							key.getDescription() + " [continuation " + continuation_num + "]",
							(byte)0,
							256,
							timeout,
							exhaustive,
							high_priority,
							new listenerMapper( listener, DistributedDatabaseEvent.ET_VALUE_READ, key, next_key_bytes, timeout, continuation_num+1 ));
					}
				}else{

					listener.event( new dbEvent( type, key, originator, _value ));
				}
			}
		}

		@Override
		public void
		valueWritten(
			DHTPluginContact	target,
			DHTPluginValue		value )
		{
			listener.event( new dbEvent( type, key, target, value ));
		}

		@Override
		public void
		complete(
			byte[]	timeout_key,
			boolean	timeout_occurred )
		{
			if ( !complete_disabled ){

				listener.event(
					new dbEvent(
						timeout_occurred?DistributedDatabaseEvent.ET_OPERATION_TIMEOUT:DistributedDatabaseEvent.ET_OPERATION_COMPLETE,
						key ));
			}
		}
	}

	protected class
	dbEvent
		implements DistributedDatabaseEvent
	{
		private int							type;
		private DistributedDatabaseKey		key;
		private DistributedDatabaseKeyStats	key_stats;
		private DistributedDatabaseValue	value;
		private DDBaseContactImpl			contact;

		protected
		dbEvent(
			int						_type )
		{
			type	= _type;
		}

		protected
		dbEvent(
			int						_type,
			DistributedDatabaseKey	_key )
		{
			type	= _type;
			key		= _key;
		}

		protected
		dbEvent(
			int						_type,
			DistributedDatabaseKey	_key,
			DHTPluginContact		_contact,
			DHTPluginValue			_value )
		{
			type		= _type;
			key			= _key;

			contact	= new DDBaseContactImpl( DDBaseImpl.this, _contact );

			value	= new DDBaseValueImpl( contact, _value.getValue(), _value.getCreationTime(), _value.getVersion());
		}

		protected
		dbEvent(
			int								_type,
			DistributedDatabaseKey			_key,
			DHTPluginContact				_contact,
			DistributedDatabaseKeyStats		_key_stats )
		{
			type		= _type;
			key			= _key;

			contact	= new DDBaseContactImpl( DDBaseImpl.this, _contact );

			key_stats	= _key_stats;
		}

		protected
		dbEvent(
			int						_type,
			DistributedDatabaseKey	_key,
			DHTPluginContact		_contact,
			byte[]					_value,
			long					_ct,
			long					_v )
		{
			type		= _type;
			key			= _key;

			contact	= new DDBaseContactImpl( DDBaseImpl.this, _contact );

			value	= new DDBaseValueImpl( contact, _value, _ct, _v );
		}

		@Override
		public int
		getType()
		{
			return( type );
		}

		@Override
		public DistributedDatabaseKey
		getKey()
		{
			return( key );
		}

		@Override
		public DistributedDatabaseKeyStats
		getKeyStats()
		{
			return( key_stats );
		}

		@Override
		public DistributedDatabaseValue
		getValue()
		{
			return( value );
		}

		@Override
		public DistributedDatabaseContact
		getContact()
		{
			return( contact );
		}
	}
}