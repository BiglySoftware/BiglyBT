/*
 * Created on 16-Mar-2006
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

package com.biglybt.core.speedmanager.impl;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.dht.speed.DHTSpeedTester;
import com.biglybt.core.dht.speed.DHTSpeedTesterContact;
import com.biglybt.core.dht.speed.DHTSpeedTesterContactListener;
import com.biglybt.core.dht.speed.DHTSpeedTesterListener;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminASN;
import com.biglybt.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.biglybt.core.speedmanager.*;
import com.biglybt.core.speedmanager.impl.v1.SpeedManagerAlgorithmProviderV1;
import com.biglybt.core.speedmanager.impl.v2.SpeedManagerAlgorithmProviderV2;
import com.biglybt.core.util.*;


public class
SpeedManagerImpl
	implements SpeedManager, SpeedManagerAlgorithmProviderAdapter, AEDiagnosticsEvidenceGenerator
{
	protected static final int UPDATE_PERIOD_MILLIS	= 3000;

	private static final int CONTACT_NUMBER		= 3;
	private static final int CONTACT_PING_SECS	= UPDATE_PERIOD_MILLIS/1000;


		// keep history for 1 hour

	private static final int LONG_PERIOD_SECS	= 60*60;

	private static final int LONG_PERIOD_TICKS 	= LONG_PERIOD_SECS / CONTACT_PING_SECS;

	private static final int SHORT_ESTIMATE_SECS	= 15;
	private static final int MEDIUM_ESTIMATE_SECS	= 150;

	static final int SHORT_ESTIMATE_SAMPLES		= SHORT_ESTIMATE_SECS/CONTACT_PING_SECS;
	static final int MEDIUM_ESTIMATE_SAMPLES	= MEDIUM_ESTIMATE_SECS/CONTACT_PING_SECS;

	private static final int SAVE_PERIOD_SECS	= 15*60;
	private static final int SAVE_PERIOD_TICKS 	= SAVE_PERIOD_SECS / CONTACT_PING_SECS;

	private static final int AUTO_ADJUST_PERIOD_SECS	= 60;
	private static final int AUTO_ADJUST_PERIOD_TICKS 	= AUTO_ADJUST_PERIOD_SECS / CONTACT_PING_SECS;

	private static final int SPEED_AVERAGE_PERIOD	= 3000;

		// config items start

	private static boolean				DEBUG;

    public  static final String CONFIG_VERSION_STR      = "Auto_Upload_Speed_Version_String"; //Shadow of CONFIG_VERSION for config.
    public  static final String	CONFIG_VERSION			= "Auto Upload Speed Version";
	private static final String	CONFIG_AVAIL			= "AutoSpeed Available";	// informative only

	private static final String	CONFIG_DEBUG			= "Auto Upload Speed Debug Enabled";


	private static final String[]	CONFIG_PARAMS = {
		CONFIG_DEBUG };

	static{
		COConfigurationManager.addAndFireParameterListeners(
				CONFIG_PARAMS,
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String parameterName )
					{
						DEBUG = COConfigurationManager.getBooleanParameter( CONFIG_DEBUG );
					}
				});

	}

	private static boolean	emulated_ping_source;

		// config end


	final Core core;
	private DHTSpeedTester		speed_tester;
	final SpeedManagerAdapter	adapter;

	private SpeedManagerAlgorithmProvider	provider = new nullProvider();


	private	int							provider_version	= -1;
	private boolean						enabled;

	private final static boolean				pm_enabled = true;

	final Map							contacts	= new HashMap();
	private volatile int				total_contacts;
	private pingContact[]				contacts_array	= new pingContact[0];

	private Object	original_limits;

	final AsyncDispatcher	dispatcher = new AsyncDispatcher();

	final SpeedManagerPingMapperImpl		ping_mapper;

	final SpeedManagerPingMapperImpl[] 	ping_mappers;

	private final CopyOnWriteList		transient_mappers = new CopyOnWriteList();

	private final AEDiagnosticsLogger	logger;

	private String		asn;

	private final CopyOnWriteList	listeners = new CopyOnWriteList();

	public
	SpeedManagerImpl(
		Core _core,
		SpeedManagerAdapter	_adapter )
	{
		core			= _core;
		adapter			= _adapter;

		AEDiagnostics.addWeakEvidenceGenerator( this );

		logger = AEDiagnostics.getLogger( "SpeedMan" );

		ping_mapper	= new SpeedManagerPingMapperImpl( this, "Var", LONG_PERIOD_TICKS, true, false );

		if ( Constants.isCVSVersion()){

			SpeedManagerPingMapperImpl		pm2 	= new SpeedManagerPingMapperImpl( this, "Abs", LONG_PERIOD_TICKS, false, false );

			ping_mappers = new SpeedManagerPingMapperImpl[]{ pm2, ping_mapper };

		}else{

			ping_mappers = new SpeedManagerPingMapperImpl[]{ ping_mapper };
		}

		final File	config_dir = new File( SystemProperties.getUserPath(), "net" );

		if ( !config_dir.exists()){

			config_dir.mkdirs();
		}

		NetworkAdmin.getSingleton().addAndFirePropertyChangeListener(
			new NetworkAdminPropertyChangeListener()
			{
				@Override
				public void
				propertyChanged(
					String		property )
				{
					if ( property == NetworkAdmin.PR_AS ){

					    NetworkAdminASN net_asn = NetworkAdmin.getSingleton().getCurrentASN();

						String	as = net_asn.getAS();

						if ( as.length() == 0 ){

							as = "default";
						}

						File history = new File( config_dir, "pm_" + FileUtil.convertOSSpecificChars( as, false ) + ".dat" );

						ping_mapper.loadHistory( history );

						asn = net_asn.getASName();

						if ( asn.length() == 0 ){

							asn = "Unknown";
						}

						informListeners( SpeedManagerListener.PR_ASN );
					}
				}
			});

		core.addLifecycleListener(
			new CoreLifecycleAdapter()
			{
				@Override
				public void
				stopping(
					Core core )
				{
					ping_mapper.saveHistory();
				}
			});

		COConfigurationManager.addAndFireParameterListener(
			CONFIG_VERSION,
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					final String name )
				{
					dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								boolean	do_reset = provider_version == -1;

								int version = COConfigurationManager.getIntParameter( name );

								// Version 3 was "Neural" and has been remove
								if (version == 3) {
									version = 2;
								}

								if ( version != provider_version ){

									provider_version = version;

									if ( isEnabled()){

										setEnabledSupport( false );

										setEnabledSupport( true );
									}
								}

								if ( do_reset ){

									enableOrAlgChanged();
								}
							}
						});
				}
			});

		COConfigurationManager.setParameter( CONFIG_AVAIL, false );

		SimpleTimer.addPeriodicEvent(
			"SpeedManager:timer",
			UPDATE_PERIOD_MILLIS,
			new TimerEventPerformer()
			{
				private int	tick_count;

				@Override
				public void
				perform(
					TimerEvent event )
				{
						// if enabled the ping stream drives the stats update for the ping mappers
						// When not enabled we do it here instead

					if ( !pm_enabled || contacts_array.length == 0 ){

						int	x	= (adapter.getCurrentDataUploadSpeed(SPEED_AVERAGE_PERIOD) + adapter.getCurrentProtocolUploadSpeed(SPEED_AVERAGE_PERIOD));
						int	y 	= (adapter.getCurrentDataDownloadSpeed(SPEED_AVERAGE_PERIOD) + adapter.getCurrentProtocolDownloadSpeed(SPEED_AVERAGE_PERIOD));

						for (int i=0;i<ping_mappers.length;i++){

							ping_mappers[i].addSpeed( x, y );
						}
					}

					tick_count++;

					if ( tick_count % SAVE_PERIOD_TICKS == 0 ){

						ping_mapper.saveHistory();
					}

					if ( tick_count % AUTO_ADJUST_PERIOD_TICKS == 0 ){

						autoAdjust();
					}
				}
			});

		COConfigurationManager.addAndFireParameterListener(
			"Auto Adjust Transfer Defaults",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					autoAdjust();
				}
			});

		emulated_ping_source	= false;

		if ( emulated_ping_source ){

			Debug.out( "Emulated ping source!!!!" );

			setSpeedTester( new TestPingSourceRandom( this ));
		}
	}

	@Override
	public SpeedManager
	getSpeedManager()
	{
		return( this );
	}

	@Override
	public String
	getASN()
	{
		return( asn );
	}

	@Override
	public SpeedManagerLimitEstimate
	getEstimatedUploadCapacityBytesPerSec()
	{
		return( ping_mapper.getEstimatedUploadCapacityBytesPerSec());
	}

	@Override
	public void
	setEstimatedUploadCapacityBytesPerSec(
		int		bytes_per_sec,
		float	metric )
	{
		ping_mapper.setEstimatedUploadCapacityBytesPerSec( bytes_per_sec, metric );
	}

	@Override
	public SpeedManagerLimitEstimate
	getEstimatedDownloadCapacityBytesPerSec()
	{
		return( ping_mapper.getEstimatedDownloadCapacityBytesPerSec());
	}

	@Override
	public void
	setEstimatedDownloadCapacityBytesPerSec(
		int		bytes_per_sec,
		float	metric )
	{
		ping_mapper.setEstimatedDownloadCapacityBytesPerSec( bytes_per_sec, metric );
	}

	@Override
	public void
	reset()
	{
		ping_mapper.reset();
	}

	protected void
	enableOrAlgChanged()
	{
		total_contacts		= 0;

		SpeedManagerAlgorithmProvider	old_provider = provider;

		if ( provider_version == 1 ){

			if ( !( provider instanceof SpeedManagerAlgorithmProviderV1 )){

				provider = new SpeedManagerAlgorithmProviderV1( this );
			}
		}else if ( provider_version == 2 ){

			if ( !( provider instanceof SpeedManagerAlgorithmProviderV2 )){

				provider = new SpeedManagerAlgorithmProviderV2( this );
			}

		}else{

			Debug.out( "Unknown provider version " + provider_version );

			if ( !( provider instanceof nullProvider )){

				provider = new nullProvider();
			}
		}

		if ( old_provider != provider ){

			log( "Algorithm set to " + provider.getClass().getName());
		}

		if ( old_provider != null ){

			old_provider.destroy();
		}

		provider.reset();
	}

	@Override
	public SpeedManagerPingMapper
	createTransientPingMapper()
	{
		SpeedManagerPingMapper res = new SpeedManagerPingMapperImpl( this, "Transient", LONG_PERIOD_TICKS, true, true );

		transient_mappers.add( res );

		if ( transient_mappers.size() > 32 ){

			Debug.out( "Transient mappers are growing too large" );
		}

		return( res );
	}

	protected void
	destroy(
		SpeedManagerPingMapper	mapper )
	{
		transient_mappers.remove( mapper );
	}

	@Override
	public void
	setSpeedTester(
		DHTSpeedTester	_tester )
	{
		if ( _tester == speed_tester ){

			return;
		}

		if ( speed_tester != null ){

			if ( !emulated_ping_source ){

				Debug.out( "speed tester already set!" );
			}

			return;
		}

		COConfigurationManager.setParameter( CONFIG_AVAIL, true );

		speed_tester	= _tester;

		speed_tester.addListener(
				new DHTSpeedTesterListener()
				{
					private DHTSpeedTesterContact[]	last_contact_group = new DHTSpeedTesterContact[0];

					@Override
					public void
					contactAdded(
						DHTSpeedTesterContact contact )
					{
						if ( core.getInstanceManager().isLANAddress(contact.getAddress().getAddress())){

							contact.destroy();

						}else{
							log( "activePing: " + contact.getString());

							contact.setPingPeriod( CONTACT_PING_SECS );

							synchronized( contacts ){

								pingContact	source = new pingContact( contact );

								contacts.put( contact, source );

								contacts_array = new pingContact[ contacts.size() ];

								contacts.values().toArray( contacts_array );

								total_contacts++;

								provider.pingSourceFound( source, total_contacts > CONTACT_NUMBER );
							}

							contact.addListener(
								new DHTSpeedTesterContactListener()
								{
									@Override
									public void
									ping(
										DHTSpeedTesterContact	contact,
										int						round_trip_time )
									{
									}

									@Override
									public void
									pingFailed(
										DHTSpeedTesterContact	contact )
									{
									}

									@Override
									public void
									contactDied(
										DHTSpeedTesterContact	contact )
									{
										log( "deadPing: " + contact.getString());

										synchronized( contacts ){

											pingContact source = (pingContact)contacts.remove( contact );

											if ( source != null ){

												contacts_array = new pingContact[ contacts.size() ];

												contacts.values().toArray( contacts_array );

												provider.pingSourceFailed( source );
											}
										}
									}
								});
						}
					}

					@Override
					public void
					resultGroup(
						DHTSpeedTesterContact[]	st_contacts,
						int[]					round_trip_times )
					{
						if ( !pm_enabled ){

							for (int i=0;i<st_contacts.length;i++){

								st_contacts[i].destroy();
							}

							return;
						}

						boolean	sources_changed = false;

						for (int i=0;i<st_contacts.length;i++){

							boolean	found = false;

							for (int j=0;j<last_contact_group.length;j++){

								if ( st_contacts[i] == last_contact_group[j] ){

									found = true;

									break;
								}
							}

							if ( !found ){

								sources_changed = true;

								break;
							}
						}

						last_contact_group = st_contacts;

						pingContact[]	sources = new pingContact[st_contacts.length];

						boolean	miss = false;

						int	worst_value	= -1;
						int	min_value	= Integer.MAX_VALUE;

						int	num_values	= 0;
						int	total		= 0;

						synchronized( contacts ){

							for (int i=0;i<st_contacts.length;i++){

								pingContact source = sources[i] = (pingContact)contacts.get( st_contacts[i] );

								if ( source != null ){

									int	rtt = round_trip_times[i];

									if ( rtt >= 0 ){

										if ( rtt > worst_value ){

											worst_value = rtt;
										}

										if ( rtt < min_value ){

											min_value = rtt;
										}

										num_values++;

										total += rtt;
									}

									source.setPingTime( rtt );

								}else{

									miss = true;
								}
							}
						}

						if ( miss ){

							// happens, no biggy Debug.out( "Auto-speed: source missing" );

						}else{

							provider.calculate( sources );

								// remove worst value if we have > 1

							if ( num_values > 1 ){

								total -= worst_value;
								num_values--;
							}

							if ( num_values > 0 ){

								int	average = total/num_values;

									// bias towards min

								average = ( average + min_value ) / 2;

								addPingHistory( average, sources_changed );
							}
						}
					}

					@Override
					public void
					destroyed()
					{
						speed_tester = null;
					}
				});

		if ( pm_enabled ){

			speed_tester.setContactNumber( CONTACT_NUMBER );
		}

		SimpleTimer.addPeriodicEvent(
			"SpeedManager:stats",
			SpeedManagerAlgorithmProvider.UPDATE_PERIOD_MILLIS,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent	event )
				{
					if ( enabled ){

						provider.updateStats();
					}
				}
			});
	}



	protected void
	addPingHistory(
		int			rtt,
		boolean		re_base )
	{
		int	x	= (adapter.getCurrentDataUploadSpeed(SPEED_AVERAGE_PERIOD) + adapter.getCurrentProtocolUploadSpeed(SPEED_AVERAGE_PERIOD));
		int	y 	= (adapter.getCurrentDataDownloadSpeed(SPEED_AVERAGE_PERIOD) + adapter.getCurrentProtocolDownloadSpeed(SPEED_AVERAGE_PERIOD));

		for (int i=0;i<ping_mappers.length;i++){

			ping_mappers[i].addPing( x, y, rtt, re_base );
		}

		Iterator it = transient_mappers.iterator();

		while( it.hasNext()){

			((SpeedManagerPingMapperImpl)it.next()).addPing( x, y, rtt, re_base );
		}
	}

	@Override
	public boolean
	isAvailable()
	{
		return( speed_tester != null );
	}

	@Override
	public void
	setEnabled(
		final boolean		_enabled )
	{
			// unfortunately we need this to run synchronously as the caller may be disabling it
			// and then setting speed limits in which case we can't go async and restore the
			// original values below and overwrite the new limit...

		final AESemaphore	sem = new AESemaphore( "SpeedManagerImpl.setEnabled" );

			// single thread enable/disable (and derivative reset) ops

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					try{
						setEnabledSupport( _enabled );

					}finally{

						sem.release();
					}
				}
			});

		if ( !sem.reserve( 10000 )){

			Debug.out( "operation didn't complete in time" );
		}
	}

	protected void
	setEnabledSupport(
		boolean	_enabled )
	{
		if ( enabled != _enabled ){

			log( "Enabled set to " + _enabled );

			if ( _enabled ){

				original_limits	= adapter.getLimits();

			}else{

				ping_mapper.saveHistory();
			}

			enableOrAlgChanged();

			enabled	= _enabled;

			/*
			 * should probably be && pm_enabled but been dead code for a while so I'm just commenting out for the mo
			if ( speed_tester != null && !pm_enabled ){

				speed_tester.setContactNumber( enabled?CONTACT_NUMBER:0);
			}
			*/

			if ( !enabled ){

				adapter.setLimits( original_limits, true, provider.getAdjustsDownloadLimits());
			}
		}
	}

	@Override
	public boolean
	isEnabled()
	{
		return( enabled );
	}

	@Override
	public DHTSpeedTester
	getSpeedTester()
	{
		return( speed_tester );
	}

	@Override
	public SpeedManagerPingSource[]
	getPingSources()
	{
		return( contacts_array );
	}

	@Override
	public SpeedManagerPingMapper
	getActiveMapper()
	{
		return( ping_mapper );
	}

	@Override
	public SpeedManagerPingMapper
	getPingMapper()
	{
		return( getActiveMapper());
	}

	@Override
	public SpeedManagerPingMapper[]
	getMappers()
	{
		return( ping_mappers );
	}

	public int
	getIdlePingMillis()
	{
		return( provider.getIdlePingMillis());
	}

	public int
	getCurrentPingMillis()
	{
		return( provider.getCurrentPingMillis());
	}

	public int
	getMaxPingMillis()
	{
		return( provider.getMaxPingMillis());
	}

		/**
		 * Returns the current view of when choking occurs
		 * @return speed in bytes/sec
		 */

	public int
	getCurrentChokeSpeed()
	{
		return( provider.getCurrentChokeSpeed());
	}

	public int
	getMaxUploadSpeed()
	{
		return( provider.getMaxUploadSpeed());
	}

	@Override
	public int
	getCurrentUploadLimit()
	{
		return( adapter.getCurrentUploadLimit());
	}

	@Override
	public void
	setCurrentUploadLimit(
		int		bytes_per_second )
	{
		if ( enabled ){

			adapter.setCurrentUploadLimit( bytes_per_second );
		}
	}

	@Override
	public int
	getCurrentDownloadLimit()
	{
		return( adapter.getCurrentDownloadLimit());
	}

	@Override
	public void
	setCurrentDownloadLimit(
		int bytes_per_second)
	{
		if ( enabled ){

			adapter.setCurrentDownloadLimit( bytes_per_second );
		}
	}

	@Override
	public int
	getCurrentProtocolUploadSpeed()
	{
		return( adapter.getCurrentProtocolUploadSpeed(-1));
	}

	@Override
	public int
	getCurrentDataUploadSpeed()
	{
		return( adapter.getCurrentDataUploadSpeed(-1));
	}

    @Override
    public int
    getCurrentDataDownloadSpeed()
    {
        return( adapter.getCurrentDataDownloadSpeed(-1) );
    }

    @Override
    public int
    getCurrentProtocolDownloadSpeed()
    {
        return( adapter.getCurrentProtocolDownloadSpeed(-1) );
    }

    private void
    autoAdjust()
    {
    	if ( COConfigurationManager.getBooleanParameter( "Auto Adjust Transfer Defaults" )){

       		int	up_limit_bytes_per_sec 		= getEstimatedUploadCapacityBytesPerSec().getBytesPerSec();
       		int	down_limit_bytes_per_sec 	= getEstimatedDownloadCapacityBytesPerSec().getBytesPerSec();

       		int up_kbs = up_limit_bytes_per_sec/1024;


       		final int[][] settings = {

       				{ 56, 		2, 		20, 	40  },		// 56 k/bit
       				{ 96, 		3, 		30, 	60 },
       				{ 128, 		3, 		40, 	80 },
       				{ 192, 		4, 		50, 	100 },		// currently we don't go lower than this
       				{ 256, 		4, 		60, 	200 },
       				{ 512, 		5, 		70, 	300 },
       				{ 1024,		6, 		80, 	400 },		// 1Mbit
       				{ 2*1024,	8, 		90, 	500 },
       				{ 5*1024,	10, 	100, 	600 },
      				{ 10*1024,	20,		110, 	750 },		// 10Mbit
      				{ 20*1024,	30, 	120, 	900 },
      				{ 50*1024,	40, 	130, 	1100 },
      				{ 100*1024,	50, 	140, 	1300 },
      				{ -1, 		60, 	150, 	1500 },
       		};

       		int[] selected = settings[ settings.length-1 ];

       			// note, we start from 3 to avoid over-restricting things when we don't have
       			// a reasonable speed estimate

       		for ( int i=3;i<settings.length;i++ ){

       			int[]	setting = settings[i];

       			int	line_kilobit_sec = setting[0];

       				// convert to upload kbyte/sec assuming 80% achieved

       			int	limit = (line_kilobit_sec/8)*4/5;

       			if ( up_kbs <= limit ){

       				selected = setting;

       				break;
       			}
       		}

      		int	upload_slots			= selected[1];
       		int	connections_torrent		= selected[2];
       		int connections_global		= selected[3];


      		if ( upload_slots != COConfigurationManager.getIntParameter( "Max Uploads" )){

      			COConfigurationManager.setParameter( "Max Uploads", upload_slots );
      			COConfigurationManager.setParameter( "Max Uploads Seeding", upload_slots );
       		}

      		if ( connections_torrent != COConfigurationManager.getIntParameter( "Max.Peer.Connections.Per.Torrent" )){

    			COConfigurationManager.setParameter( "Max.Peer.Connections.Per.Torrent", connections_torrent );

    			COConfigurationManager.setParameter( "Max.Peer.Connections.Per.Torrent.When.Seeding", connections_torrent / 2 );
       		}

      		if ( connections_global != COConfigurationManager.getIntParameter( "Max.Peer.Connections.Total" )){

       			COConfigurationManager.setParameter( "Max.Peer.Connections.Total", connections_global );
       		}
    	}
    }

	public void
	setLoggingEnabled(
		boolean	enabled )
	{
		COConfigurationManager.setParameter( CONFIG_DEBUG, enabled );
	}

	@Override
	public void
	log(
		String		str )
	{
		if ( DEBUG ){

			logger.log( str );
		}
	}

	protected void
	informDownCapChanged()
	{
		informListeners( SpeedManagerListener.PR_DOWN_CAPACITY );
	}

	protected void
	informUpCapChanged()
	{
		informListeners( SpeedManagerListener.PR_UP_CAPACITY );
	}

	protected void
	informListeners(
		int		type )
	{
		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((SpeedManagerListener)it.next()).propertyChanged( type );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	addListener(
		SpeedManagerListener		l )
	{
		listeners.add( l );
	}

	@Override
	public void
	removeListener(
		SpeedManagerListener		l )
	{
		listeners.remove( l );
	}

	@Override
	public void
	generate(
		IndentWriter writer)
	{
		writer.println( "SpeedManager: enabled=" + enabled + ",provider=" + provider );

		try{
			writer.indent();

			ping_mapper.generateEvidence( writer );

		}finally{

			writer.exdent();
		}
	}

	protected static class
	pingContact
		implements SpeedManagerPingSource
	{
		private final DHTSpeedTesterContact	contact;

		private int	ping_time;

		protected
		pingContact(
			DHTSpeedTesterContact	_contact )
		{
			contact	= _contact;
		}

		protected void
		setPingTime(
			int		time )
		{
			ping_time = time;
		}

		@Override
		public InetSocketAddress
		getAddress()
		{
			return( contact.getAddress());
		}

		@Override
		public int
		getPingTime()
		{
			return( ping_time );
		}

		@Override
		public void
		destroy()
		{
			contact.destroy();
		}
	}

	protected static class
	nullProvider
		implements SpeedManagerAlgorithmProvider
	{
		@Override
		public void
		reset()
		{
		}

		@Override
		public void
		destroy()
		{
		}

		@Override
		public void
		updateStats()
		{
		}

		@Override
		public void
		pingSourceFound(
			SpeedManagerPingSource		source,
			boolean						is_replacement )
		{
		}

		@Override
		public void
		pingSourceFailed(
			SpeedManagerPingSource		source )
		{
		}

		@Override
		public void
		calculate(
			SpeedManagerPingSource[]	sources )
		{
		}

		@Override
		public int
		getIdlePingMillis()
		{
			return( 0 );
		}

		@Override
		public int
		getCurrentPingMillis()
		{
			return( 0 );
		}

		@Override
		public int
		getMaxPingMillis()
		{
			return( 0 );
		}

		@Override
		public int
		getCurrentChokeSpeed()
		{
			return( 0 );
		}

		@Override
		public int
		getMaxUploadSpeed()
		{
			return( 0 );
		}

		@Override
		public boolean
		getAdjustsDownloadLimits()
		{
			return( false );
		}
	}
}
