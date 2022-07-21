/*
 * File    : PEPeerTransportProtocol.java
 * Created : 22-Oct-2003
 * By      : stuff
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
package com.biglybt.core.peer.impl.transport;


import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.impl.CoreImpl;
import com.biglybt.core.logging.*;
import com.biglybt.core.networkmanager.*;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.impl.udp.UDPNetworkManager;
import com.biglybt.core.peer.*;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peer.impl.PEPeerTransportFactory;
import com.biglybt.core.peer.util.PeerIdentityDataID;
import com.biglybt.core.peer.util.PeerIdentityManager;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageManager;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.peermanager.messaging.azureus.*;
import com.biglybt.core.peermanager.messaging.bittorrent.*;
import com.biglybt.core.peermanager.messaging.bittorrent.ltep.*;
import com.biglybt.core.peermanager.peerdb.PeerExchangerItem;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.peermanager.peerdb.PeerItemFactory;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.peermanager.utils.*;
import com.biglybt.core.proxy.AEProxyAddressMapper;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.tag.TaggableResolver;
import com.biglybt.core.torrent.TOTorrentFileHashTree.*;
import com.biglybt.core.util.*;
import com.biglybt.pif.clientid.ClientIDGenerator;
import com.biglybt.pif.dht.mainline.MainlineDHTProvider;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;
import com.biglybt.pifimpl.local.network.ConnectionImpl;


public class
PEPeerTransportProtocol
extends LogRelation
implements PEPeerTransport
{
	protected final static LogIDs LOGID = LogIDs.PEER;

	private volatile int	_lastPiece =-1;		//last piece that was requested from this peer (mostly to try to request from same one)

	protected final PEPeerControl 	manager;
	protected final DiskManager		diskManager;
	protected final PiecePicker		piecePicker;

	protected final int		nbPieces;

	private final String			peer_source;
	private byte[] peer_id;
	private final String ip;
	private final String network;
	protected String ip_resolved;
	private IPToHostNameResolverRequest	ip_resolver_request;

	private final int port;

	private PeerItem peer_item_identity;
	private int tcp_listen_port = 0;
	private int udp_listen_port = 0;
	private int udp_non_data_port = 0;
	// if the peer announces an ipv6 address
	private InetAddress alternativeAddress;

	private byte	crypto_level;

	protected PEPeerStats peer_stats;

	private final ArrayList<DiskManagerReadRequest> requested = new ArrayList<>();

	private Map data;

	private long lastNeededUndonePieceChange;

	private boolean really_choked_by_other_peer = true;
	private boolean effectively_choked_by_other_peer = true;
	private long	effectively_unchoked_time = -1;

	protected boolean choking_other_peer = true;
	private boolean interested_in_other_peer = false;
	private boolean other_peer_interested_in_me = false;
	private long snubbed =0;

	/** lazy allocation; null until needed */
	private volatile BitFlags	peerHavePieces =null;
	private volatile boolean	availabilityAdded =false;
	private volatile boolean	received_bitfield;

	private int[]	piece_priority_offsets;

	private boolean handshake_sent;

	private boolean seeding = false;

	private static final byte RELATIVE_SEEDING_NONE = 0x00;
	// indicates that the peer told us that he's uploading only
	private static final byte RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED = 0x01;
	// peer is not useful to us
	private static final byte RELATIVE_SEEDING_UPLOAD_ONLY_SEED = 0x02;
	private byte relativeSeeding = RELATIVE_SEEDING_NONE;

	private final boolean incoming_raw;
	private boolean incoming_effective;

	protected volatile boolean closing = false;
	private volatile int current_peer_state;

	final private NetworkConnection connection;
	private OutgoingBTPieceMessageHandler outgoing_piece_message_handler;
	private OutgoingBTHaveMessageAggregator outgoing_have_message_aggregator;
	private Connection	plugin_connection;

	private boolean identityAdded = false;  //needed so we don't remove id's in closeAll() on duplicate connection attempts

	private int connection_state = PEPeerTransport.CONNECTION_PENDING;
	
	private int outbound_connection_progress	= CP_UNKNOWN;
	
	private String client = ""; // Client name to show to user.
	private String client_peer_id = ""; // Client name derived from the peer ID.
	private String client_handshake = ""; // Client name derived from the handshake.
	private String client_handshake_version = ""; // Client version derived from the handshake.

	//When superSeeding, number of unique piece announced
	private int uniquePiece = -1;

	//When downloading a piece in exclusivity mode the piece number being downloaded
	private int[] reserved_pieces = null;

	//Spread time (0 secs , fake default)
	private int spreadTimeHint = 0 * 1000;

	private long last_message_sent_time = 0;
	private long last_message_received_time = 0;
	private long last_data_message_received_time = -1;
	private long last_good_data_time =-1;			// time data written to disk was recieved
	private long last_data_message_sent_time = -1;

	private long connection_established_time = 0;

	private int consecutive_no_request_count;

	private int messaging_mode = MESSAGING_BT_ONLY;
	private Message[] supported_messages = null;
	private byte	other_peer_bitfield_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_cancel_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_choke_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_handshake_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_bt_have_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_az_have_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_interested_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_keep_alive_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_pex_version				= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_piece_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_unchoke_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_uninterested_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_request_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_suggest_piece_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_have_all_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_have_none_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_reject_request_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_allowed_fast_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private final byte  other_peer_bt_lt_ext_version   	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_hash_request_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_hashes_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_hash_reject_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;

	
	private byte	other_peer_az_request_hint_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_az_bad_piece_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_az_stats_request_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_az_stats_reply_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte	other_peer_az_metadata_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;

	private static final boolean DEBUG_FAST = false;

	private boolean ut_pex_enabled 			= false;
	private boolean fast_extension_enabled 	= false;
	private boolean ml_dht_enabled 			= false;

	private static final int	ALLOWED_FAST_PIECE_OFFERED_NUM		= 10;
	private static final int	ALLOWED_FAST_OTHER_PEER_PIECE_MAX	= 10;

	private static final Object	KEY_ALLOWED_FAST_RECEIVED 	= new Object();
	private static final Object	KEY_ALLOWED_FAST_SENT 		= new Object();

	private final AEMonitor closing_mon	= new AEMonitor( "PEPeerTransportProtocol:closing" );
	private final AEMonitor general_mon  	= new AEMonitor( "PEPeerTransportProtocol:data" );

	private byte[] handshake_reserved_bytes = null;

	private LinkedHashMap recent_outgoing_requests;
	private AEMonitor	recent_outgoing_requests_mon;

	private boolean has_received_initial_pex = false;

	private static final boolean SHOW_DISCARD_RATE_STATS;
	static {
		final String	prop = System.getProperty( "show.discard.rate.stats" );
		SHOW_DISCARD_RATE_STATS = prop != null && prop.equals( "1" );
	}

	private static int requests_discarded = 0;
	private static int requests_discarded_endgame = 0;
	private static int requests_recovered = 0;
	private static int requests_completed = 0;

	private static final int REQUEST_HINT_MAX_LIFE	= PiecePicker.REQUEST_HINT_MAX_LIFE + 30*1000;

	private int[]	request_hint;

	private List peer_listeners_cow;
	private final AEMonitor	peer_listeners_mon = new AEMonitor( "PEPeerTransportProtocol:PL" );


	//certain Optimum Online networks block peer seeding via "complete" bitfield message filtering
	//lazy mode makes sure we never send a complete (seed) bitfield
	protected static boolean ENABLE_LAZY_BITFIELD;

	private boolean priority_connection;

	private int upload_priority_auto;

	private static final class DisconnectedTransportQueue extends LinkedHashMap<HashWrapper,DisconnectedTransportQueue.QueueEntry>
	{
		public DisconnectedTransportQueue()
		{
			super(20,0.75F);
			
			DisconnectedTransportQueue queue = this;
			
			SimpleTimer.addPeriodicEvent( "dtw:cleaner", 60*1000, (ev)->{
				
				synchronized( queue ){
					Iterator<QueueEntry> it = values().iterator();

					long now = SystemTime.getMonotonousTime();

					while(it.hasNext()){
						
						QueueEntry eldest = it.next();
						
						if( now - eldest.addTime > MAX_CACHE_AGE){
							
							it.remove();
							
						}else{
							
							break;
						}
					}
				}
			});
		}

		private static final long MAX_CACHE_AGE = 2*60*1000;

		// remove all elements older than 2 minutes until we hit the 20 again

		private void performCleaning() {
			if(size() > 20)
			{
				Iterator<QueueEntry> it = values().iterator();

				long now = SystemTime.getMonotonousTime();

				while(it.hasNext() && size() > 20)
				{
					QueueEntry eldest = it.next();
					if( now - eldest.addTime > MAX_CACHE_AGE){
						it.remove();
					}else{
						break;
					}
				}
			}
		}

		private static final class QueueEntry {
			public QueueEntry(PEPeerTransportProtocol trans)
			{
				transport = trans;
			}

			final PEPeerTransportProtocol transport;
			final long addTime = SystemTime.getMonotonousTime();
		}

		// hardcap at 100
		@Override
		protected boolean removeEldestEntry(Map.Entry<HashWrapper,QueueEntry> eldest) {
			return size() > 100;
		}

		synchronized public Object put(HashWrapper key, PEPeerTransportProtocol value) {
			performCleaning();
			return super.put(key, new QueueEntry(value));
		}

		synchronized public PEPeerTransportProtocol remove(HashWrapper key) {
			performCleaning();
			QueueEntry entry = super.remove(key);
			if(entry != null)
				return entry.transport;
			else
				return null;
		}

	}

	private static final DisconnectedTransportQueue recentlyDisconnected = new DisconnectedTransportQueue();

	private static boolean 	fast_unchoke_new_peers;

	private static final Random rnd = RandomUtils.SECURE_RANDOM;

	private static final byte[] sessionSecret;

	private static boolean enable_upload_bias;

	private static boolean enable_public_tcp_peers	= true;
	private static boolean enable_public_udp_peers	= true;
	
	private static boolean prefer_ipv6;
	
	static {
		rnd.setSeed(SystemTime.getHighPrecisionCounter());
		sessionSecret = new byte[20];
		rnd.nextBytes(sessionSecret);

		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"Use Lazy Bitfield",
					"Peer.Fast.Initial.Unchoke.Enabled",
					"Bias Upload Enable",
					ConfigKeys.Connection.BCFG_PEERCONTROL_TCP_PUBLIC_ENABLE,
					ConfigKeys.Connection.BCFG_PEERCONTROL_UDP_PUBLIC_ENABLE,
					ConfigKeys.Connection.BCFG_PEERCONTROL_PREFER_IPV6_CONNECTIONS },
				(ignore)->{
					{
						final String  prop = System.getProperty(SystemProperties.SYSPROP_LAZY_BITFIELD);

						ENABLE_LAZY_BITFIELD = prop != null && prop.equals( "1" );

						ENABLE_LAZY_BITFIELD |= COConfigurationManager.getBooleanParameter( "Use Lazy Bitfield" );

						fast_unchoke_new_peers 		= COConfigurationManager.getBooleanParameter( "Peer.Fast.Initial.Unchoke.Enabled" );

						enable_upload_bias 			= COConfigurationManager.getBooleanParameter( "Bias Upload Enable" );
						
						enable_public_tcp_peers		= COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_PEERCONTROL_TCP_PUBLIC_ENABLE );
						enable_public_udp_peers		= COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_PEERCONTROL_UDP_PUBLIC_ENABLE );
						
						prefer_ipv6			= COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_PEERCONTROL_PREFER_IPV6_CONNECTIONS );
					}
				});
	}



	// reconnect stuff
	private HashWrapper peerSessionID;
	private HashWrapper mySessionID;

	// allow reconnect if we've sent or recieved at least 1 piece over the current connection
	private boolean allowReconnect;
	private boolean isReconnect;

	private Set<Object>		upload_disabled_set;
	private Set<Object>		download_disabled_set;
	private boolean			is_upload_disabled;
	private boolean			is_download_disabled;

	private boolean is_optimistic_unchoke = false;

	private PeerExchangerItem peer_exchange_item = null;
	private boolean peer_exchange_supported = false;

	protected PeerMessageLimiter message_limiter;

	private boolean request_hint_supported;
	private boolean bad_piece_supported;
	private boolean stats_request_supported;
	private boolean stats_reply_supported;
	private boolean az_metadata_supported;

	private boolean have_aggregation_disabled;

	private volatile boolean	manual_lazy_bitfield_control;
	private volatile int[]		manual_lazy_haves;

	private final boolean is_metadata_download;

	private long request_latency;

	//INCOMING
	public
	PEPeerTransportProtocol(
		PEPeerControl 		_manager,
		String 				_peer_source,
		NetworkConnection	_connection,
		Map					_initial_user_data )
	{
		manager = _manager;
		peer_source		= _peer_source;
		connection 		= _connection;
		data			= _initial_user_data;

		incoming_raw = true;

		Boolean b_incoming = (Boolean)connection.getEndpoint().getProperty( AEProxyAddressMapper.MAP_PROPERTY_CONNECTION_INCOMING );
		
		if ( b_incoming != null ){
			
			incoming_effective = b_incoming;
			
		}else{
			
			incoming_effective = true;
		}
		
		is_metadata_download = manager.isMetadataDownload();

		diskManager =manager.getDiskManager();
		piecePicker =manager.getPiecePicker();
		
		nbPieces =manager.getNbPieces();


		InetSocketAddress notional_address = _connection.getEndpoint().getNotionalAddress();

		ip    = AddressUtils.getHostAddress( notional_address );

		network	= AENetworkClassifier.categoriseAddress( ip );

		port  = notional_address.getPort();

		peer_item_identity = PeerItemFactory.createPeerItem( ip, port, PeerItem.convertSourceID( _peer_source ), PeerItemFactory.HANDSHAKE_TYPE_PLAIN, 0, PeerItemFactory.CRYPTO_LEVEL_1, 0 );  //this will be recreated upon az handshake decode

		plugin_connection = new ConnectionImpl(connection, incoming_raw );

		peer_stats = manager.createPeerStats( this );

		changePeerState( PEPeer.CONNECTING );
	}

	@Override
	public void
	start()
	{
		// split out connection initiation from constructor so we can get access to the peer transport
		// before message processing starts

		if ( incoming_raw ){

			//"fake" a connect request to register our listener
			connection.connect(
				ProtocolEndpoint.CONNECT_PRIORITY_MEDIUM,
				new NetworkConnection.ConnectionListener()
				{
					@Override
					public final int
					connectStarted( int ct ){
						connection_state = PEPeerTransport.CONNECTION_CONNECTING;
						return( ct );
					}

					@Override
					public final void connectSuccess(ByteBuffer remaining_initial_data ) {  //will be called immediately
						if (Logger.isEnabled())
							Logger.log(new LogEvent(PEPeerTransportProtocol.this, LOGID,
							"In: Established incoming connection"));

						generateSessionId();

						initializeConnection();

						/*
						 * Waiting until we've received the initiating-end's full handshake, before sending back our own,
						 * really should be the "proper" behavior.  However, classic BT trackers running NAT checking will
						 * only send the first 48 bytes (up to infohash) of the peer handshake, skipping peerid, which means
						 * we'll never get their complete handshake, and thus never reply, which causes the NAT check to fail.
						 * So, we need to send our handshake earlier, after we've verified the infohash.
						 * NOTE:
						 * This code makes the assumption that the inbound infohash has already been validated,
						 * as we don't check their handshake fully before sending our own.
						 */
						sendBTHandshake();
					}

					@Override
					public final void
					connectFailure(
						Throwable failure_msg )
					{  //should never happen
						Debug.out( "ERROR: incoming connect failure: ", failure_msg );
						closeConnectionInternally( "ERROR: incoming connect failure [" + PEPeerTransportProtocol.this + "] : " + failure_msg.getMessage(), true, true );
					}

					@Override
					public final void
					exceptionThrown(
							Throwable error )
					{
						if( error.getMessage() == null ) {
							Debug.out( error );
						}

						closeConnectionInternally( "connection exception: " + error.getMessage(), false, true );
					}

					@Override
					public Object
					getConnectionProperty(
						String property_name )
					{
						return( null );
					}

					@Override
					public String
					getDescription()
					{
						return( getString());
					}
				});
		}else{
			// not pulled out startup from outbound connections yet...
		}
	}



	//OUTGOING

	public
	PEPeerTransportProtocol(
			PEPeerControl 	_manager,
			String 			_peer_source,
			String 			_ip,
			int 			_tcp_port,
			int 			_udp_port,
			boolean			_use_tcp,
			boolean 		_require_crypto_handshake,
			byte			_crypto_level,
			Map				_initial_user_data )
	{
		manager = _manager;

		is_metadata_download = manager.isMetadataDownload();

		diskManager =manager.getDiskManager();
		piecePicker =manager.getPiecePicker();
		nbPieces =diskManager.getNbPieces();
		lastNeededUndonePieceChange =Long.MIN_VALUE;

		peer_source		= _peer_source;
		ip    			= _ip;
		port  			= _tcp_port;
		tcp_listen_port = _tcp_port;
		udp_listen_port	= _udp_port;
		crypto_level	= _crypto_level;
		data			= _initial_user_data;

		network	= AENetworkClassifier.categoriseAddress( ip );

		if ( data != null ){

			Boolean pc = (Boolean)data.get( Peer.PR_PRIORITY_CONNECTION );

			if ( pc != null && pc.booleanValue()){

				setPriorityConnection( true );
			}
		}

		udp_non_data_port = UDPNetworkManager.getSingleton().getUDPNonDataListeningPortNumber();

		peer_item_identity = PeerItemFactory.createPeerItem( ip, tcp_listen_port, PeerItem.convertSourceID( _peer_source ), PeerItemFactory.HANDSHAKE_TYPE_PLAIN, _udp_port, crypto_level, 0 );  //this will be recreated upon az handshake decode

		incoming_raw = incoming_effective = false;

		peer_stats = manager.createPeerStats( this );

		if( port < 0 || port > 65535 ) {
			closeConnectionInternally( "given remote port is invalid: " + port );
			connection = null;
			return;
		}

    	// either peer specific or global pref plus optional per-download level

		boolean use_crypto =
				_require_crypto_handshake ||
    			NetworkManager.getCryptoRequired( manager.getAdapter().getCryptoLevel());

		boolean	lan_local = isLANLocal();

		boolean public_net = peer_item_identity.getNetwork() == AENetworkClassifier.AT_PUBLIC;

		if ( lan_local || !public_net ){

			use_crypto = false; // dont bother with PHE for lan peers
								// or non-public networks. one reason for this is that it doesn't work with i2psnark for some reason
								// (actually the reason is that it blocks peers that send a bad message (ConnectionAcceptor:MAX_BAD) which
								// causes the subsequent plain connection to be dropped)
								// AND it causes a raw-message to be submitted to the outgoing message queue which
								// then bypasses i2phelp plugin handshake modifications...
								// note also that incoming non-public connections also ignore crypto settings

		}

		InetSocketAddress	endpoint_address;
		ProtocolEndpoint	pe1 = null;
		ProtocolEndpoint	pe2 = null;

		if ( _use_tcp ){

			boolean utp_available = ProtocolEndpointFactory.isHandlerRegistered( ProtocolEndpoint.PROTOCOL_UTP );

			boolean socks_active = NetworkAdmin.getSingleton().isSocksActive();

			if ( public_net ){

				endpoint_address = new InetSocketAddress( ip, tcp_listen_port );

			}else{

				endpoint_address = InetSocketAddress.createUnresolved( ip, tcp_listen_port );
			}

			if ( lan_local || !public_net ){

				pe1 = ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_TCP, endpoint_address );

			}else if ( !utp_available ){
				
				if ( enable_public_tcp_peers ){
					
					pe1 = ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_TCP, endpoint_address );
				}
			}else if ( AERunStateHandler.isUDPNetworkOnly() && !socks_active ){

				pe1 = ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_UTP, endpoint_address );

			}else{

				if ( enable_public_tcp_peers ){
					
					pe1 = ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_TCP, endpoint_address );
	
					if ( !socks_active ){
	
						if ( RandomUtils.nextInt(2) == 1 ){
	
							pe2 = ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_UTP, endpoint_address );
						}
					}
				}else{
					
					if ( !socks_active ){
						
						pe1 = ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_UTP, endpoint_address );
					}
				}
			}
		}else{
				
			if ( public_net ){
	
				endpoint_address = new InetSocketAddress( ip, udp_listen_port );
	
			}else{
	
				endpoint_address = InetSocketAddress.createUnresolved( ip, udp_listen_port );
			}

			if ( enable_public_udp_peers ){

				pe1 = ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_UDP, endpoint_address );
			}
		}

		if ( pe1 == null ){
			
			closeConnectionInternally( "No enabled public peer protocols" );
			
			connection = null;
			
			return;
		}
		
		ConnectionEndpoint connection_endpoint	= new ConnectionEndpoint( endpoint_address );

		connection_endpoint.addProtocol( pe1 );

		if ( pe2 != null ){

			connection_endpoint.addProtocol( pe2 );
		}

		connection =
			NetworkManager.getSingleton().createConnection(
					connection_endpoint,
					new BTMessageEncoder(),
					new BTMessageDecoder(),
					use_crypto,
					!_require_crypto_handshake,
					manager.getSecrets( _crypto_level ));

		plugin_connection = new ConnectionImpl(connection, incoming_raw);

		changePeerState( PEPeer.CONNECTING );

		ByteBuffer	initial_outbound_data = null;

		if ( use_crypto ){

			BTHandshake handshake = new BTHandshake( manager.getTargetHash(),
					manager.getPeerId(),
                    manager.getExtendedMessagingMode(), other_peer_handshake_version );

			if (Logger.isEnabled())
			    Logger.log(new LogEvent(this, LOGID,
			    		"Sending encrypted handshake with reserved bytes: " +
			    			ByteFormatter.nicePrint(handshake.getReserved(), false)));

			DirectByteBuffer[] ddbs = handshake.getRawData();

			int	handshake_len = 0;

			for (int i=0;i<ddbs.length;i++){

				handshake_len += ddbs[i].remaining( DirectByteBuffer.SS_PEER );
			}

			initial_outbound_data = ByteBuffer.allocate( handshake_len );

			for (int i=0;i<ddbs.length;i++){

				DirectByteBuffer	ddb = ddbs[i];

				initial_outbound_data.put( ddb.getBuffer( DirectByteBuffer.SS_PEER ));

				ddb.returnToPool();
			}

			initial_outbound_data.flip();

			handshake_sent = true;
		}

		int	priority;

		if ( manager.isSeeding()){

			priority = ProtocolEndpoint.CONNECT_PRIORITY_LOW;

		}else if ( manager.isRTA()){

			if ( PeerClassifier.isAzureusIP( ip )){

				priority = ProtocolEndpoint.CONNECT_PRIORITY_SUPER_HIGHEST;

			}else{

				priority = ProtocolEndpoint.CONNECT_PRIORITY_HIGHEST;
			}
		}else if ( PeerClassifier.isAzureusIP( ip )){

			priority = ProtocolEndpoint.CONNECT_PRIORITY_HIGHEST;

		}else{

			priority = ProtocolEndpoint.CONNECT_PRIORITY_MEDIUM;
		}

			// give plugin peers some priority as they're being injected for a good reason

		if ( peer_source == PEPeerSource.PS_PLUGIN ){

			if ( priority > ProtocolEndpoint.CONNECT_PRIORITY_HIGH ){

				priority = ProtocolEndpoint.CONNECT_PRIORITY_HIGH;
			}
		}

		connection.connect(
				initial_outbound_data,
				priority,
				new NetworkConnection.ConnectionListener()
				{
					@Override
					public final int
					connectStarted(
						int		default_connect_timeout )
					{
							// we can end up here twice when connecting to both TCP and uTP...
						
						connection_state = PEPeerTransport.CONNECTION_CONNECTING;

						outbound_connection_progress = CP_CONNECTING;
						
						if ( default_connect_timeout <= 0 ){

								// uTP case - timeouts handled separately
							
							return( default_connect_timeout );
						}

						return( manager.getConnectTimeout( default_connect_timeout ));
					}

					@Override
					public final void
					connectSuccess(
						ByteBuffer remaining_initial_data )
					{
						outbound_connection_progress = CP_CONNECT_OK;

						if( closing ) {
							//Debug.out( "PEPeerTransportProtocol::connectSuccess() called when closing." );
							return;
						}

						generateSessionId();

						if (Logger.isEnabled())
							Logger.log(new LogEvent(PEPeerTransportProtocol.this, LOGID,
									"Out: Established outgoing connection"));

						initializeConnection();

					    if ( remaining_initial_data != null && remaining_initial_data.remaining() > 0){

				    		// queue as a *raw* message as already encoded

					    	connection.getOutgoingMessageQueue().addMessage(
								new BTRawMessage( new DirectByteBuffer( remaining_initial_data )), false );
					    }

						sendBTHandshake();
					}

					@Override
					public final void
					connectFailure(
						Throwable failure_msg )
					{
						outbound_connection_progress = CP_CONNECT_FAILED;
						
						closeConnectionInternally( "failed to establish outgoing connection: " + failure_msg.getMessage(), true, true );
					}

					@Override
					public final void
					exceptionThrown(
						Throwable error )
					{
						if( error.getMessage() == null ) {
							Debug.out( "error.getMessage() == null", error );
						}

							// this method can be called anytime, not just during connection
						
						boolean connecting = outbound_connection_progress == CP_CONNECTING;
							
						if ( connecting ){
							
							outbound_connection_progress = CP_CONNECT_FAILED;
						}
						
						closeConnectionInternally( "connection exception: " + error.getMessage(), connecting, true );
					}

					@Override
					public Object
					getConnectionProperty(
						String property_name )
					{
						if ( property_name == AEProxyFactory.PO_PEER_NETWORKS ){

							return( manager.getAdapter().getEnabledNetworks());
							
						}else if ( property_name.equals( AEProxyFactory.PO_LOCAL_PORT )){
							
							return( manager.getAdapter().getTCPListeningPortNumber());
						}

						return( null );
					}

					@Override
					public String
					getDescription()
					{
						return( getString());
					}
				});

		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, LOGID,
			"Out: Creating outgoing connection"));
	}




	protected void initializeConnection() {
		if( closing )  return;

		recent_outgoing_requests = new LinkedHashMap( 16, .75F, true ) {
			@Override
			public final boolean removeEldestEntry(Map.Entry eldest) {
				return size() > 16;
			}
		};
		recent_outgoing_requests_mon  = new AEMonitor( "PEPeerTransportProtocol:ROR" );

		message_limiter = new PeerMessageLimiter();

		/*
		//link in outgoing piece handler
		outgoing_piece_message_handler =
			new OutgoingBTPieceMessageHandler(
					this,
					connection.getOutgoingMessageQueue(),
					new OutgoingBTPieceMessageHandlerAdapter()
					{
						public void
						diskRequestCompleted(
								long bytes)
						{
							peer_stats.diskReadComplete( bytes );
						}
    			},
    			other_peer_piece_version);*/

		//link in outgoing have message aggregator
		outgoing_have_message_aggregator = new OutgoingBTHaveMessageAggregator( connection.getOutgoingMessageQueue(), other_peer_bt_have_version, other_peer_az_have_version );

		connection_established_time = SystemTime.getCurrentTime();

		connection_state = PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE;
		changePeerState( PEPeer.HANDSHAKING );

		registerForMessageHandling();
	}


	@Override
	public boolean 
	isMyPeer()
	{
		return( false );
	}

	@Override
	public String
	getPeerSource()
	{
		return( peer_source );
	}

	@Override
	public int
	getOutboundConnectionProgress()
	{
		int cp = outbound_connection_progress;
		
		if ( cp == CP_CONNECT_OK ){
			
			if ( last_message_received_time > 0 ){
				
				cp = CP_RECEIVED_DATA;
			}
		}
		
		return( cp );
	}

	/**
	 * Close the peer connection from within the PEPeerTransport object.
	 * @param reason
	 */
	protected void closeConnectionInternally( String reason, boolean connect_failed, boolean network_failure ) {
		performClose( reason, connect_failed, false, network_failure );
	}

	protected void closeConnectionInternally( String reason ) {
		performClose( reason, false, false, false );
	}


	/**
	 * Close the peer connection from the PEPeerControl manager side.
	 * NOTE: This method assumes PEPeerControl already knows about the close.
	 * This method is inteded to be only invoked by select administrative methods.
	 * You probably should not invoke this directly.
	 */
	@Override
	public void closeConnection(String reason ) {
		performClose( reason, false, true, false );
	}


	private void performClose( String reason, boolean connect_failed, boolean externally_closed, boolean network_failure )
	{
		try{
			closing_mon.enter();

			if( closing )
				return;
			closing = true;

			// immediatly lose interest in peer
			interested_in_other_peer =false;
			lastNeededUndonePieceChange =Long.MAX_VALUE;

			if (isSnubbed())
				manager.decNbPeersSnubbed();

			if( identityAdded ) {  //remove identity
				if( peer_id != null )
					PeerIdentityManager.removeIdentity( manager.getPeerIdentityDataID(), peer_id, getPort());
				else
					Debug.out( "PeerIdentity added but peer_id == null !!!" );
				identityAdded	= false;
			}

			changePeerState( PEPeer.CLOSING );

		}finally{
			closing_mon.exit();
		}

		//cancel any pending requests (on the manager side)
		cancelRequests();

		if( outgoing_have_message_aggregator != null ) {
			outgoing_have_message_aggregator.destroy();
		}

		if( peer_exchange_item != null ) {
			peer_exchange_item.destroy();
		}

		if( outgoing_piece_message_handler != null ) {
			outgoing_piece_message_handler.destroy();
		}

		if( connection != null ) {  //can be null if close is called within ::<init>::, like when the given port is invalid
			connection.close( reason );
		}

		if ( ip_resolver_request != null ){
			ip_resolver_request.cancel();
		}

		removeAvailability();

		changePeerState( PEPeer.DISCONNECTED );

		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, LOGID, "Peer connection closed: " + reason));

		if( !externally_closed ) {  //if closed internally, notify manager, otherwise we assume it already knows
			manager.peerConnectionClosed( this, connect_failed, network_failure );
		}

		setPriorityConnection( false );
		/*
		 *  all managed references should have been removed by now
		 *  add to recently disconnected list and null some stuff to make the object lighter
		 */

		outgoing_have_message_aggregator = null;
		peer_exchange_item = null;
		outgoing_piece_message_handler = null;
		plugin_connection = null;

		// only save stats if it's worth doing so; ignore rapid connect-disconnects
		
		if ( 	peer_stats.getTotalDataBytesReceived() > 0 || 
				peer_stats.getTotalDataBytesSent() > 0 || 
				getUploadRateLimitBytesPerSecond() != 0 ||
				getDownloadRateLimitBytesPerSecond() != 0 ||
				SystemTime.getCurrentTime() - connection_established_time > 30 * 1000){
			recentlyDisconnected.put(mySessionID, this);
		}
	}

	@Override
	public boolean
	isClosed()
	{
		return( closing );
	}

	@Override
	public PEPeerTransport reconnect(boolean tryUDP, boolean tryIPv6) {

		boolean use_tcp = isTCP() && !(tryUDP && getUDPListenPort() > 0);

		if ((use_tcp && getTCPListenPort() > 0) || (!use_tcp && getUDPListenPort() > 0))
		{
			boolean use_crypto = getPeerItemIdentity().getHandshakeType() == PeerItemFactory.HANDSHAKE_TYPE_CRYPTO;

			PEPeerTransport new_conn =
				PEPeerTransportFactory.createTransport(
						manager,
						getPeerSource(),
						tryIPv6 && alternativeAddress != null ? alternativeAddress.getHostAddress() : getIp(),
						getTCPListenPort(),
						getUDPListenPort(),
						use_tcp,
						use_crypto,
						crypto_level,
						null );

			// log to both relations
			Logger.log(new LogEvent(new Object[] {this, new_conn},LOGID,"attempting to reconnect, creating new connection"));
			if (new_conn instanceof PEPeerTransportProtocol)
			{
				PEPeerTransportProtocol pt = (PEPeerTransportProtocol) new_conn;
				pt.checkForReconnect(mySessionID);
				// carry over the alt address in case the reconnect fails and we try again with ipv6
				pt.alternativeAddress = alternativeAddress;

			}

			manager.addPeer( new_conn );

			return (new_conn);
		} else
		{
			return (null);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.peer.impl.PEPeerTransport#isSafeForReconnect()
	 */
	@Override
	public boolean isSafeForReconnect() {
		return allowReconnect;
	}

	@Override
	public boolean isReconnect(){
		return( isReconnect );
	}

	private void checkForReconnect(HashWrapper oldID)
	{
		PEPeerTransportProtocol oldTransport = recentlyDisconnected.remove(oldID);

		if(oldTransport != null)
		{
			Logger.log(new LogEvent(this,LOGID,LogAlert.AT_INFORMATION,"reassociating stats from "+oldTransport+" with this connection"));
			isReconnect = true;
			peerSessionID = oldTransport.peerSessionID;
			peer_stats = oldTransport.peer_stats;
			peer_stats.setPeer(this);
			setSnubbed(oldTransport.isSnubbed());
			snubbed = oldTransport.snubbed;
			last_good_data_time = oldTransport.last_good_data_time;
			int old_up = getUploadRateLimitBytesPerSecond();
			if ( old_up != 0 ){
				setUploadRateLimitBytesPerSecond( old_up );
			}
			int old_down = getDownloadRateLimitBytesPerSecond();
			if ( old_down != 0 ){
				setDownloadRateLimitBytesPerSecond( old_down );
			}
		}
	}

	private void generateSessionId()
	{
		SHA1Hasher sha1 = new SHA1Hasher();
		sha1.update(sessionSecret);
		sha1.update(manager.getTargetHash());
		sha1.update(getIp().getBytes());
		mySessionID = new HashWrapper(sha1.getDigest());
		checkForReconnect(mySessionID);
	}

	private void addAvailability()
	{
		if (!availabilityAdded &&!closing &&peerHavePieces !=null &&current_peer_state ==PEPeer.TRANSFERING)
		{
			final List peer_listeners_ref =peer_listeners_cow;
			if (peer_listeners_ref !=null)
			{
				for (int i =0; i <peer_listeners_ref.size(); i++)
				{
					final PEPeerListener peerListener =(PEPeerListener) peer_listeners_ref.get(i);
					peerListener.addAvailability(this, peerHavePieces);
				}
				availabilityAdded =true;
			}
		}
	}

	private void removeAvailability()
	{
		if (availabilityAdded &&peerHavePieces !=null)
		{
			final List peer_listeners_ref =peer_listeners_cow;
			if (peer_listeners_ref !=null)
			{
				for (int i =0; i <peer_listeners_ref.size(); i++)
				{
					final PEPeerListener peerListener =(PEPeerListener) peer_listeners_ref.get(i);
					peerListener.removeAvailability(this, peerHavePieces);
				}
			}
			availabilityAdded =false;
		}
		peerHavePieces =null;
	}



	protected void sendBTHandshake() {
		if ( !handshake_sent ){

			int msg_mode = manager.getExtendedMessagingMode();

			Boolean disable_az = (Boolean)connection.getEndpoint().getProperty( AEProxyAddressMapper.MAP_PROPERTY_DISABLE_AZ_MESSAGING );

			if ( disable_az != null && disable_az ){

				if ( msg_mode == BTHandshake.AZ_RESERVED_MODE ){

					msg_mode = BTHandshake.LT_RESERVED_MODE;
				}
			}

			BTHandshake handshake =	new BTHandshake( manager.getTargetHash(),
					manager.getPeerId(), msg_mode, other_peer_handshake_version );

			if (Logger.isEnabled())
			    Logger.log(new LogEvent(this, LOGID,
			    		"Sending handshake with reserved bytes: " +
			    			ByteFormatter.nicePrint(handshake.getReserved(), false)));

			connection.getOutgoingMessageQueue().addMessage(handshake, false);
		}
	}

	private void sendLTHandshake() {
		String client_name = (String)ClientIDManagerImpl.getSingleton().getProperty(  manager.getTargetHash(), ClientIDGenerator.PR_CLIENT_NAME );
		int localTcpPort = manager.getTCPListeningPortNumber();
		String tcpPortOverride = COConfigurationManager.getStringParameter("TCP.Listen.Port.Override");
		try{
			if ( !tcpPortOverride.isEmpty()){
				localTcpPort = Integer.parseInt(tcpPortOverride);
			}
		} catch (NumberFormatException e)	{} // ignore as invalid input
		
		boolean require_crypto = NetworkManager.getCryptoRequired( manager.getAdapter().getCryptoLevel());

		Map data_dict = new HashMap();

		data_dict.put("v", client_name);
		data_dict.put("p", new Integer(localTcpPort));
		data_dict.put("e", new Long(require_crypto ? 1L : 0L));

		boolean upload_only =
				manager.isSeeding() &&
				!( ENABLE_LAZY_BITFIELD || manual_lazy_bitfield_control || manager.isSuperSeedMode());

			// maintain this for any kinds of compatability

		data_dict.put( "upload_only", new Long(upload_only? 1L : 0L));

		int metainfo_size;

		if ( manager.isPrivateTorrent()){

			metainfo_size = 0;

		}else{

			metainfo_size = is_metadata_download?0:manager.getTorrentInfoDictSize();
		}

		if ( metainfo_size > 0 ){

			data_dict.put("metadata_size", new Integer(metainfo_size));
		}

		NetworkAdmin na = NetworkAdmin.getSingleton();

		if ( peer_item_identity.getNetwork() == AENetworkClassifier.AT_PUBLIC && !na.isSocksActive()){
				// don't send public address in handshake
			InetAddress defaultV6 = na.hasIPV6Potential(true) ? na.getDefaultPublicAddressV6() : null;

			if(defaultV6 != null){
				data_dict.put("ipv6",defaultV6.getAddress());
			}
		}

		LTHandshake lt_handshake = new LTHandshake(data_dict, other_peer_bt_lt_ext_version );

		lt_handshake.addDefaultExtensionMappings( true, is_metadata_download || metainfo_size > 0, true );

		connection.getOutgoingMessageQueue().addMessage(lt_handshake, false);
	}

	private void sendAZHandshake() {
		final Message[] avail_msgs = MessageManager.getSingleton().getRegisteredMessages();
		final String[] avail_ids = new String[avail_msgs.length];
		final byte[] avail_vers = new byte[avail_msgs.length];

		for (int i = 0; i < avail_msgs.length; i++)
		{
			avail_ids[i] = avail_msgs[i].getID();
			avail_vers[i] = avail_msgs[i].getVersion();
		}

		int local_tcp_port = manager.getTCPListeningPortNumber();
		int local_udp_port = UDPNetworkManager.getSingleton().getUDPListeningPortNumber();
		int local_udp2_port = UDPNetworkManager.getSingleton().getUDPNonDataListeningPortNumber();
		String tcpPortOverride = COConfigurationManager.getStringParameter("TCP.Listen.Port.Override");

		try{
			if ( !tcpPortOverride.isEmpty()){
				local_tcp_port = Integer.parseInt(tcpPortOverride);
			}
		} catch (NumberFormatException e){} // ignore as invalid input

		boolean require_crypto = NetworkManager.getCryptoRequired(manager.getAdapter().getCryptoLevel());

		/*
		 * we always send the Az-handshake immediately after the BT-handshake, before decoding the
		 * other side's Az-handshake, thus there should be no peerSessionID unless this is a
		 * reconnect
		 */
		if(peerSessionID != null)
			Logger.log(new LogEvent(this, LOGID, LogEvent.LT_INFORMATION,"notifying peer of reconnect attempt"));

		InetAddress defaultV6 = null;

		NetworkAdmin na = NetworkAdmin.getSingleton();

		if ( peer_item_identity.getNetwork() == AENetworkClassifier.AT_PUBLIC && !na.isSocksActive()){
				// don't send public address in handshake
			defaultV6 = na.hasIPV6Potential(true) ? na.getDefaultPublicAddressV6() : null;
		}

		//String peer_name = Constants.BIGLY_PROTOCOL_NAME;

		String client_name = (String)ClientIDManagerImpl.getSingleton().getProperty(  manager.getHash(), ClientIDGenerator.PR_CLIENT_NAME );
		
		int pos = client_name.indexOf( ' ' );
		
		String	peer_name;
		String	peer_version;
		
		if ( pos == -1 ){
			
			Debug.out( "Unsupported client name: " + client_name );
			
			peer_name 		= Constants.BIGLY_PROTOCOL_NAME;
			
			peer_version	= Constants.BIGLYBT_VERSION;
			
		}else {
			
			peer_name = client_name.substring( 0,  pos );
			
			pos = client_name.lastIndexOf( ' ' );
			
			peer_version = client_name.substring( pos+1 );
		}
		
		AZHandshake az_handshake = new AZHandshake(
				AZPeerIdentityManager.getAZPeerIdentity(),
				mySessionID,
				peerSessionID,
				peer_name,
				peer_version,
				local_tcp_port,
				local_udp_port,
				local_udp2_port,
				defaultV6,
				manager.isPrivateTorrent()?0:( is_metadata_download?0:manager.getTorrentInfoDictSize()),
				avail_ids,
				avail_vers,
				require_crypto ? AZHandshake.HANDSHAKE_TYPE_CRYPTO : AZHandshake.HANDSHAKE_TYPE_PLAIN,
				other_peer_handshake_version,
				manager.isSeeding() && ! ( ENABLE_LAZY_BITFIELD || manual_lazy_bitfield_control || manager.isSuperSeedMode()));
		
		connection.getOutgoingMessageQueue().addMessage( az_handshake, false );
	}

	@Override
	public int getPeerState() {  return current_peer_state;  }

	@Override
	public boolean isDownloadPossible()
	{
		if (!closing &&!effectively_choked_by_other_peer)
		{
			if (lastNeededUndonePieceChange <piecePicker.getNeededUndonePieceChange())
			{
				checkInterested();
				lastNeededUndonePieceChange =piecePicker.getNeededUndonePieceChange();
			}
			if (interested_in_other_peer &&current_peer_state ==PEPeer.TRANSFERING)
				return true;
		}
		return false;
	}


	@Override
	public int getPercentDoneInThousandNotation()
	{
		long total_done = getBytesDownloaded();

		return (int)((total_done *1000) /diskManager.getTotalLength());
	}

	@Override
	public boolean transferAvailable() {
		return (!effectively_choked_by_other_peer && interested_in_other_peer);
	}



	private void printRequestStats() {
		if( SHOW_DISCARD_RATE_STATS ) {
			final float discard_perc = (requests_discarded * 100F) / ((requests_completed + requests_recovered + requests_discarded) * 1F);
			final float discard_perc_end = (requests_discarded_endgame * 100F) / ((requests_completed + requests_recovered + requests_discarded_endgame) * 1F);
			final float recover_perc = (requests_recovered * 100F) / ((requests_recovered + requests_discarded) * 1F);
			System.out.println( "c="+requests_completed+ " d="+requests_discarded+ " de="+requests_discarded_endgame+ " r="+requests_recovered+ " dp="+discard_perc+  "% dpe="+discard_perc_end+ "% rp="+recover_perc+ "%" );
		}
	}




	/**
	 * Checks if this peer is a seed or not by trivially checking if
	 * thier Have bitflags exisits and shows a number of bits set equal
	 * to the torrent # of pieces (and the torrent # of pieces is >0)
	 */
	private void checkSeed()
	{
			// seed implicitly means *something* to send (right?)

		if (peerHavePieces !=null && nbPieces >0){
			setSeed((peerHavePieces.nbSet ==nbPieces));
		}else{
			setSeed(false);
		}

		if (manager.isSeeding() && isSeed()){
			// peer is a real seed so it's also a relative seed
			relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
		}else if(manager.isSeeding() && (relativeSeeding & RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED) != 0){
			// peer indicated upload-only, we're seeding so he's a relative seed
			relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
		}else if(peerHavePieces !=null && nbPieces > 0){

			int piecesDone = manager.getPiecePicker().getNbPiecesDone();
			DiskManagerPiece[] dmPieces = diskManager.getPieces();
			boolean couldBeSeed = true;

			if (!manager.isSeeding() &&	(relativeSeeding & RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED) != 0 ){
				/*
				 * peer indicated upload-only, check if we can use any of the data, otherwise flag
				 * as relative seed. Useful to disconnect not-useful pseudo-seeds during downloading
				 */

				for (int i = peerHavePieces.start;i <= peerHavePieces.end;i++){

						// relative seed if peer doesn't have the piece, we already have it or we don't need it

					couldBeSeed &= !peerHavePieces.flags[i] || dmPieces[i].isDone() || !dmPieces[i].isNeeded();

					if (!couldBeSeed){

						break;
					}
				}
			}else if ( manager.isSeeding() && piecesDone <= peerHavePieces.nbSet ){

					// we're seeding, check if peer has all the data we have (and more), flag as relative seed if so

				for (int i = peerHavePieces.start;i <= peerHavePieces.end;i++){

						// relative seed if we don't have the piece or we have it and the peer has it too

					couldBeSeed &= !(dmPieces[i].isDone()) || peerHavePieces.flags[i];

					if( !couldBeSeed ){

						break;
					}
				}
			}else{
				couldBeSeed = false;
			}

			if(couldBeSeed){
				relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
			}else{
				relativeSeeding &= ~RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
			}
		} else{
			relativeSeeding &= ~RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
		}
	}


	@Override
	public DiskManagerReadRequest
	request(
		final int 		pieceNumber,
		final int 		pieceOffset,
		final int 		pieceLength,
		final boolean 	return_duplicates )
	{
		final DiskManagerReadRequest request = manager.createDiskManagerRequest(pieceNumber, pieceOffset, pieceLength);

		if ( current_peer_state != TRANSFERING ){

			manager.requestCanceled(request);

			return null;
		}

		boolean added =false;

		synchronized( requested ){

			if (!requested.contains(request)){

				if ( requested.size() == 0 ){

					request.setLatencyTest();
				}

				requested.add(request);

				added = true;
			}
		}
		
		if ( added ){

			if ( is_metadata_download ){

				if ( az_metadata_supported ){

					connection.getOutgoingMessageQueue().addMessage( new AZMetaData( pieceNumber, other_peer_request_version ), false );

				}else{

					connection.getOutgoingMessageQueue().addMessage( new UTMetaData( pieceNumber, other_peer_request_version ), false );
				}
			}else{
				
				manager.getHashHandler().sendingRequest( this, request );

				connection.getOutgoingMessageQueue().addMessage( new BTRequest( pieceNumber, pieceOffset, pieceLength, other_peer_request_version ), false );
			}

			_lastPiece = pieceNumber;

			if ( DEBUG_FAST ){
				if ( really_choked_by_other_peer ){

					System.out.println( "Sending allow-fast request for " + pieceNumber + "/" + pieceOffset + "/" + pieceLength + " to " + getIp());
				}
			}

			try{
				recent_outgoing_requests_mon.enter();

				recent_outgoing_requests.put( request, null );

			}finally{
				recent_outgoing_requests_mon.exit();
			}

			return( request );

		}else{

			if ( return_duplicates ){

				return( request );

			}else{

				return( null );
			}
		}
	}

	@Override
	public int
	getRequestIndex(
			DiskManagerReadRequest request )
	{
		synchronized( requested ){

			return( requested.indexOf( request ));
		}
	}

	@Override
	public void sendCancel(DiskManagerReadRequest request ) {
		if ( current_peer_state != TRANSFERING ) return;
		if ( hasBeenRequested( request ) ) {
			removeRequest( request );
      connection.getOutgoingMessageQueue().addMessage( new BTCancel( request.getPieceNumber(), request.getOffset(), request.getLength(), other_peer_cancel_version ), false );
		}
	}


	private void sendHaveNone() {
		connection.getOutgoingMessageQueue().addMessage( new BTHaveNone( other_peer_have_none_version ), false );
	}

	@Override
	public void sendHave(int pieceNumber ) {
		if ( current_peer_state != TRANSFERING || pieceNumber == manager.getHiddenPiece()) return;
		//only force if the other peer doesn't have this piece and is not yet interested or we;ve disabled
		// aggregation
		final boolean force =!other_peer_interested_in_me &&peerHavePieces !=null &&!peerHavePieces.flags[pieceNumber];

		outgoing_have_message_aggregator.queueHaveMessage( pieceNumber, force || have_aggregation_disabled );
		checkInterested();
	}


	@Override
	public void sendChoke() {
		if ( current_peer_state != TRANSFERING ) return;

		//System.out.println( "["+(System.currentTimeMillis()/1000)+"] " +connection + " choked");

		connection.getOutgoingMessageQueue().addMessage( new BTChoke( other_peer_choke_version ), false );
		choking_other_peer = true;
		is_optimistic_unchoke = false;

		destroyPieceMessageHandler();
	}


	@Override
	public void sendUnChoke() {
		if ( current_peer_state != TRANSFERING ) return;

		//System.out.println( "["+(System.currentTimeMillis()/1000)+"] " +connection + " unchoked");

		createPieceMessageHandler();

		choking_other_peer = false;	// set this first as with pseudo peers we can effectively synchronously act
		// on the unchoke advice and we don't want that borking with choked still set

    connection.getOutgoingMessageQueue().addMessage( new BTUnchoke(other_peer_unchoke_version), false );
	}

	private void
	createPieceMessageHandler()
	{
		if ( outgoing_piece_message_handler == null ){

			outgoing_piece_message_handler = new OutgoingBTPieceMessageHandler(
				this,
				connection.getOutgoingMessageQueue(),
				new OutgoingBTPieceMessageHandlerAdapter()
				{
					@Override
					public void
					diskRequestCompleted(
							long bytes)
					{
						peer_stats.diskReadComplete( bytes );
					}
			},
			other_peer_piece_version);
		}
	}

	private void
	destroyPieceMessageHandler()
	{
		if (outgoing_piece_message_handler != null ){
			outgoing_piece_message_handler.removeAllPieceRequests();
			outgoing_piece_message_handler.destroy();
			outgoing_piece_message_handler = null;
		}
	}

	private void sendKeepAlive() {
		if ( current_peer_state != TRANSFERING ) return;

		if( outgoing_have_message_aggregator.hasPending() ) {
			outgoing_have_message_aggregator.forceSendOfPending();
		}
		else {
      connection.getOutgoingMessageQueue().addMessage( new BTKeepAlive(other_peer_keep_alive_version), false );
		}
	}

	private void sendMainlineDHTPort() {
		if (!this.ml_dht_enabled) {return;}
		MainlineDHTProvider provider = getDHTProvider();
		if (provider == null) {return;}
		Message message = new BTDHTPort(provider.getDHTPort());
		connection.getOutgoingMessageQueue().addMessage(message, false);
	}



	/**
	 * Global checkInterested method.
	 * Early-out scan of pieces to determine if the peer is interesting or not.
	 * They're interesting if they have a piece that we Need and isn't Done
	 */
	@Override
	public void checkInterested()
	{
		if ( closing || peerHavePieces ==null || peerHavePieces.nbSet == 0 ){

			return;
		}

		boolean is_interesting = false;

		if ( !is_download_disabled ){

			if ( piecePicker.hasDownloadablePiece()){

					// there is a piece worth being interested in

				if (!isSeed() && !isRelativeSeed()){

						// check individually if don't have all

					for (int i = peerHavePieces.start; i <= peerHavePieces.end; i++ ){

						if ( peerHavePieces.flags[i] && diskManager.isInteresting(i)){

							is_interesting = true;

							break;
						}
					}
				}else{

					is_interesting = true;
				}
			}
		}

		if ( is_interesting && !interested_in_other_peer ){

			connection.getOutgoingMessageQueue().addMessage(new BTInterested(other_peer_interested_version), false);

		}else if ( !is_interesting && interested_in_other_peer ){

			connection.getOutgoingMessageQueue().addMessage(new BTUninterested(other_peer_uninterested_version), false);
		}

		interested_in_other_peer = is_interesting || is_metadata_download;
	}

	/**
	 * Private method to send the bitfield.
	 */
	private void sendBitField()
	{
		if (closing){
			return;
		}

			//In case we're in super seed mode, we don't send our bitfield

		if (manager.isSuperSeedMode()){

				// trying this to see if it fixes issue with libtorrent (v0.15.9.0)

			sendHaveNone();

			return;
		}

		if ( is_metadata_download ){
			return;
		}

		//create bitfield
		final DirectByteBuffer buffer =DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG, (nbPieces +7) /8);
		final DiskManagerPiece[] pieces =diskManager.getPieces();


		int num_pieces = pieces.length;

		HashSet	lazies 		= null;
		int[]	lazy_haves	= null;

		if (ENABLE_LAZY_BITFIELD || manual_lazy_bitfield_control )
		{
			int bits_in_first_byte = Math.min(num_pieces, 8);
			int last_byte_start_bit = (num_pieces / 8) * 8;
			int bits_in_last_byte = num_pieces - last_byte_start_bit;
			if (bits_in_last_byte == 0)
			{
				bits_in_last_byte = 8;
				last_byte_start_bit -= 8;
			}
			lazies = new HashSet();
			// one bit from first byte
			int first_byte_entry = rnd.nextInt(bits_in_first_byte);
			if (pieces[first_byte_entry].isDone())
			{
				lazies.add(new MutableInteger(first_byte_entry));
			}
			// one bit from last byte
			int last_byte_entry = last_byte_start_bit + rnd.nextInt(bits_in_last_byte);
			if (pieces[last_byte_entry].isDone())
			{
				lazies.add(new MutableInteger(last_byte_entry));
			}
			// random others missing
			int other_lazies = rnd.nextInt(16) + 4;
			for (int i = 0; i < other_lazies; i++)
			{
				int random_entry = rnd.nextInt(num_pieces);
				if (pieces[random_entry].isDone())
				{
					lazies.add(new MutableInteger(random_entry));
				}
			}
			int num_lazy = lazies.size();
			if (num_lazy == 0)
			{
				lazies = null;
			} else
			{
				lazy_haves = new int[num_lazy];
				Iterator it = lazies.iterator();
				for (int i = 0; i < num_lazy; i++)
				{
					int lazy_have = ((MutableInteger) it.next()).getValue();
					lazy_haves[i] = lazy_have;
				}
				if (num_lazy > 1)
				{
					for (int i = 0; i < num_lazy; i++)
					{
						int swap = rnd.nextInt(num_lazy);
						if (swap != i)
						{
							int temp = lazy_haves[swap];
							lazy_haves[swap] = lazy_haves[i];
							lazy_haves[i] = temp;
						}
					}
				}
			}
		}

		int bToSend = 0;
		int i = 0;

		MutableInteger	mi = new MutableInteger(0);

		int	hidden_piece = manager.getHiddenPiece();

		for (; i <num_pieces; i++ ){

			if ((i %8) ==0){
				bToSend =0;
			}

			bToSend = bToSend << 1;

			if (pieces[i].isDone() && i != hidden_piece ){

				if ( lazies != null ){

					mi.setValue(i);

					if ( lazies.contains( mi )){

						// System.out.println( "LazySet: " + getIp() + " -> " + i );

					}else{
						bToSend += 1;
					}
				}else{
					bToSend += 1;
				}
			}

			if ((i %8) ==7){
				buffer.put(DirectByteBuffer.SS_BT, (byte) bToSend);
		}
		}

		if ((i %8) !=0){

			bToSend = bToSend << (8 - (i % 8));
			buffer.put( DirectByteBuffer.SS_BT, (byte)bToSend );
		}

		buffer.flip( DirectByteBuffer.SS_BT );

		connection.getOutgoingMessageQueue().addMessage(new BTBitfield(buffer, other_peer_bitfield_version ), false);

		if ( lazy_haves != null ){

			if ( manual_lazy_bitfield_control ){

				manual_lazy_haves = lazy_haves;

			}else{

				sendLazyHaves( lazy_haves, false );
			}
		}
	}

	protected void
	sendLazyHaves(
		final int[]	lazy_haves,
		boolean		immediate )
	{
		if ( immediate ){

			if ( current_peer_state == TRANSFERING ){

				for ( int lazy_have: lazy_haves ){

					connection.getOutgoingMessageQueue().addMessage(new BTHave(lazy_have, other_peer_bt_have_version), false);
				}
			}
		}else{

			SimpleTimer.addEvent(
				"LazyHaveSender",
				SystemTime.getCurrentTime() + 1000 + rnd.nextInt(2000),
				new TimerEventPerformer()
				{
					int	next_have	= 0;

					@Override
					public void perform(TimerEvent event) {

						if ( current_peer_state == TRANSFERING ){

							int lazy_have = lazy_haves[next_have++];

							connection.getOutgoingMessageQueue().addMessage(new BTHave(lazy_have, other_peer_bt_have_version), false);

							if ( next_have < lazy_haves.length && current_peer_state == TRANSFERING ){

								SimpleTimer.addEvent("LazyHaveSender", SystemTime.getCurrentTime() + rnd.nextInt(2000), this);
							}
						}
					}
				});
		}
	}


	@Override
	public byte[] getId() {  return peer_id;  }
	@Override
	public String getIp() {  return ip;  }
	@Override
	public InetAddress getAlternativeIPv6() { return alternativeAddress; }
	@Override
	public int getPort() {  return port;  }

	@Override
	public int getTCPListenPort() {  return tcp_listen_port;  }
	@Override
	public int getUDPListenPort() {  return udp_listen_port;  }
	@Override
	public int getUDPNonDataListenPort() { return( udp_non_data_port ); }


	@Override
	public String getClient() {  return client;  }

	@Override
	public boolean isIncoming() {  return incoming_effective;  }


	@Override
	public boolean isOptimisticUnchoke() {  return is_optimistic_unchoke && !isChokedByMe();  }
	@Override
	public void setOptimisticUnchoke(boolean is_optimistic ) {  is_optimistic_unchoke = is_optimistic;  }


	@Override
	public PEPeerControl getControl() {  return manager;  }
	@Override
	public PEPeerManager getManager() {  return manager;  }
	@Override
	public PEPeerStats getStats() {  return peer_stats;  }

	@Override
	public int[]
	getPriorityOffsets()
	{
		return( piece_priority_offsets );
	}

	@Override
	public boolean
	requestAllocationStarts(
		int[]	base_priorities )
	{
		return( false );
	}

	@Override
	public void
	requestAllocationComplete()
	{
	}

	/**
	 * @return null if no bitfield has been recieved yet
	 * else returns BitFlags indicating what pieces the peer has
	 */
	@Override
	public BitFlags getAvailable()
	{
		return peerHavePieces;
	}
	@Override
	public boolean isPieceAvailable(int pieceNumber)
	{
		if (peerHavePieces !=null)
			return peerHavePieces.flags[pieceNumber];
		return false;
	}

	@Override
	public boolean isChokingMe() {  return effectively_choked_by_other_peer;  }
	@Override
	public boolean isUnchokeOverride() {return really_choked_by_other_peer && !effectively_choked_by_other_peer; }
	@Override
	public boolean isChokedByMe() {  return choking_other_peer;  }
	/**
	 * @return true if the peer is interesting to us
	 */
	@Override
	public boolean isInteresting() {  return interested_in_other_peer;  }
	/**
	 * @return true if the peer is interested in what we're offering
	 */
	@Override
	public boolean isInterested() {  return other_peer_interested_in_me;  }
	@Override
	public boolean isSeed() {  return seeding;  }
	@Override
	public boolean isRelativeSeed() { return (relativeSeeding & RELATIVE_SEEDING_UPLOAD_ONLY_SEED) != 0; }

	private void
	setSeed(
			boolean	s )
	{
		if ( seeding != s ){

			seeding	= s;

		    PeerExchangerItem pex_item = peer_exchange_item;

			if ( pex_item != null && s){

				pex_item.seedStatusChanged();
			}
		}
	}

	@Override
	public boolean isSnubbed() {  return snubbed !=0;  }

	@Override
	public long getSnubbedTime()
	{
		if (snubbed ==0)
			return 0;
		final long now =SystemTime.getCurrentTime();
		if (now <snubbed)
			snubbed =now -26; // odds are ...
		return now -snubbed;
	}

	@Override
	public void setSnubbed(boolean b)
	{
		if (!closing)
		{
			final long now =SystemTime.getCurrentTime();
			if (!b)
			{
				if (snubbed !=0)
				{
					snubbed =0;
					manager.decNbPeersSnubbed();
				}
			} else if (snubbed ==0)
			{
				snubbed =now;
				manager.incNbPeersSnubbed();
			}
		}
	}
	@Override
	public void setUploadHint(int spreadTime) {  spreadTimeHint = spreadTime;  }
	@Override
	public int getUploadHint() {  return spreadTimeHint;  }
	@Override
	public void setUniqueAnnounce(int _uniquePiece) {  uniquePiece = _uniquePiece;  }
	@Override
	public int getUniqueAnnounce() {  return uniquePiece;  }



	@Override
	public Object getData(String key) {

		return( getUserData( key ));
	}

	@Override
	public void
	setData(String key, Object value)
	{
		setUserData( key, value );
	}

	/** To retreive arbitrary objects against a peer. */
	@Override
	public Object getUserData (Object key) {
		try{
			general_mon.enter();

			if (data == null) return null;
			return data.get(key);
		}finally{

			general_mon.exit();
		}
	}

	/** To store arbitrary objects against a peer. */
	@Override
	public void setUserData (Object key, Object value) {
		try{
			general_mon.enter();

			if (data == null) {
				data = new LightHashMap();
			}
			if (value == null) {
				if (data.containsKey(key)){
					data.remove(key);
					if ( data.size()==0 ){
						data = null;
					}
				}
			} else {
				data.put(key, value);
			}
		}finally{

			general_mon.exit();
		}
	}


	@Override
	public String
	getIPHostName()
	{
		if ( ip_resolved == null ){

			ip_resolved = ip;

			ip_resolver_request = IPToHostNameResolver.addResolverRequest(
					ip_resolved,
					new IPToHostNameResolverListener()
					{
						@Override
						public final void
						IPResolutionComplete(
								String		res,
								boolean		ok )
						{
							ip_resolved	= res;
						}
					});
		}

		return( ip_resolved );
	}



	private void cancelRequests()
	{
		if (!closing) {   //cancel any unsent requests in the queue
            final Message[] type ={new BTRequest(-1, -1, -1, other_peer_request_version)};
			connection.getOutgoingMessageQueue().removeMessagesOfType(type, false);
		}
		if (requested !=null &&requested.size() >0) {
			synchronized( requested ){

				if (!closing)
				{   // may have unchoked us, gotten a request, then choked without filling it - snub them
					// if they actually have data coming in, they'll be unsnubbed as soon as it writes
					manager.checkSnubbing( this );
				}
				for (int i = requested.size() - 1; i >= 0; i--) {
					final DiskManagerReadRequest request =(DiskManagerReadRequest) requested.remove(i);
					manager.requestCanceled(request);
				}
			}
		}
	}

	@Override
	public int
	getMaxNbRequests()
	{
		return( -1 );
	}

	@Override
	public int
	getNbRequests() {
		return requested.size();
	}

	/**
	 *
	 * @return	may be null for performance purposes
	 */

	@Override
	public List
	getExpiredRequests() {
		List result = null;

		// this is frequently called, hence we operate without a monitor and
		// take the hit of possible exceptions due to concurrent list
		// modification (only out-of-bounds can occur)

		try{
			for (int i =requested.size() -1; i >=0; i--)
			{
				final DiskManagerReadRequest request = (DiskManagerReadRequest) requested.get(i);

				if (request.isExpired()){

					if ( result == null ){

						result = new ArrayList();
					}

					result.add(request);
				}
			}

			return( result );

		}catch(Throwable e ){

			return result;
		}
	}

	private DiskManagerReadRequest
	lookupRequest(
		int		piece_number,
		int		piece_offset,
		int		length )
	{
		synchronized( requested ){
			
			for ( DiskManagerReadRequest r: requested ){

				if ( r.getPieceNumber() == piece_number &&
						r.getOffset()	== piece_offset &&
						r.getLength() == length ){

					return( r );
				}
			}

			return( null );
		}
	}

	private boolean	
	hasBeenRequested( 
		DiskManagerReadRequest request ) 
	{
		synchronized( requested ){

			return requested.contains( request );
		}
	}

	protected void
	removeRequest(
		DiskManagerReadRequest	request )
	{
		synchronized( requested ){

			requested.remove(request);
		}
	    
		final BTRequest msg = new BTRequest( request.getPieceNumber(), request.getOffset(), request.getLength(), other_peer_request_version );
		
		connection.getOutgoingMessageQueue().removeMessage( msg, false );
		
		msg.destroy();
	}

	private void
	resetRequestsTime(final long now)
	{
		synchronized( requested ){
			
			final int requestedSize =requested.size();
			for (int i =0; i <requestedSize; i++)
			{
				final DiskManagerReadRequest request =(DiskManagerReadRequest) requested.get(i);
				if (request != null)
					request.resetTime(now);
			}
		}
	}


	public String toString() {
		// parg: removed this as we want to see the peer's IP/host, not (e.g.) a SOCKS server address if in use
		//if( connection != null && connection.isConnected() ) {
		//	return connection + (isTCP()?" [":"(UDP) [") + client+ "]";
		//}
		return (isIncoming() ? "R: " : "L: ")+ ip + ":" + port + (isTCP()?" [":"(UDP) [") + client+ "]";
	}

	public String
	getString()
	{
		return( toString());
	}


	@Override
	public void doKeepAliveCheck() {
		final long now =SystemTime.getCurrentTime();
		final long wait_time =now -last_message_sent_time;

		if( last_message_sent_time == 0 || wait_time < 0 ) {
			last_message_sent_time =now; //don't send if brand new connection
			return;
		}

		if( wait_time > 2*60*1000 ) {  //2min keep-alive timer
			sendKeepAlive();
			last_message_sent_time =now;  //not quite true, but we don't want to queue multiple keep-alives before the first is actually sent
		}
	}


	@Override
	public void
	updateAutoUploadPriority(
		Object		key,
		boolean		inc )
	{
		try{
			general_mon.enter();

	  		boolean	key_exists = getUserData( key ) != null;

	  		if ( inc && !key_exists ){

	  			upload_priority_auto++;

	  			setUserData( key, "" );

	  		}else if ( !inc && key_exists ){

	  			upload_priority_auto--;

	  			setUserData( key, null );
	  		}

		}finally{

			general_mon.exit();
		}
	}

	@Override
	public boolean doTimeoutChecks() {
		//Timeouts for states PEPeerTransport.CONNECTION_PENDING and
		//PEPeerTransport.CONNECTION_CONNECTING are handled by the ConnectDisconnectManager
		//so we don't need to deal with them here.

		if ( connection != null ){

			connection.getOutgoingMessageQueue().setPriorityBoost(
				upload_priority_auto > 0 ||
				manager.getUploadPriority() > 0 ||
				( enable_upload_bias && !manager.isSeeding()));
		}

		if ( fast_extension_enabled ){

			checkAllowedFast();
		}

		final long now =SystemTime.getCurrentTime();
		//make sure we time out stalled connections
		if( connection_state == PEPeerTransport.CONNECTION_FULLY_ESTABLISHED ) {
			if (last_message_received_time >now)
				last_message_received_time =now;
			if (last_data_message_received_time >now)
				last_data_message_received_time =now;
			if (now -last_message_received_time >5*60*1000
					&&now -last_data_message_received_time >5*60*1000) { //5min timeout
				// assume this is due to a network failure
				// e.g. something that didn't close the TCP socket properly
				// will attempt reconnect
				closeConnectionInternally( "timed out while waiting for messages", false, true );
				return true;
			}
		}
		//ensure we dont get stuck in the handshaking phases
		else if( connection_state == PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE ) {
			if (connection_established_time >now)
				connection_established_time =now;
			else if (now - connection_established_time > 3*60*1000 ) { //3min timeout
				closeConnectionInternally( "timed out while waiting for handshake" );
				return true;
			}
		}

		return false;
	}



	@Override
	public void doPerformanceTuningCheck() {
		Transport	transport = connection.getTransport();

		if( transport != null && peer_stats != null && outgoing_piece_message_handler != null ) {

			//send speed -based tuning
			final long send_rate = peer_stats.getDataSendRate() + peer_stats.getProtocolSendRate();

			if( send_rate >= 3125000 ) {  // 25 Mbit/s
				transport.setTransportMode( Transport.TRANSPORT_MODE_TURBO );
				outgoing_piece_message_handler.setRequestReadAhead( 256 );
			}
			else if( send_rate >= 1250000 ) {  // 10 Mbit/s
				transport.setTransportMode( Transport.TRANSPORT_MODE_TURBO );
				outgoing_piece_message_handler.setRequestReadAhead( 128 );
			}
			else if( send_rate >= 125000 ) {  // 1 Mbit/s
				if( transport.getTransportMode() < Transport.TRANSPORT_MODE_FAST ) {
					transport.setTransportMode( Transport.TRANSPORT_MODE_FAST );
				}
				outgoing_piece_message_handler.setRequestReadAhead( 32 );
			}
			else if( send_rate >= 62500 ) {  // 500 Kbit/s
				outgoing_piece_message_handler.setRequestReadAhead( 16 );
			}
			else if( send_rate >= 31250 ) {  // 250 Kbit/s
				outgoing_piece_message_handler.setRequestReadAhead( 8 );
			}
			else if( send_rate >= 12500 ) {  // 100 Kbit/s
				outgoing_piece_message_handler.setRequestReadAhead( 4 );
			}
			else {
				outgoing_piece_message_handler.setRequestReadAhead( 2 );
			}


			//receive speed -based tuning
			final long receive_rate = peer_stats.getDataReceiveRate() + peer_stats.getProtocolReceiveRate();

			if( receive_rate >= 1250000 ) {  // 10 Mbit/s
				transport.setTransportMode( Transport.TRANSPORT_MODE_TURBO );
			}
			else if( receive_rate >= 125000 ) {  // 1 Mbit/s
				if( transport.getTransportMode() < Transport.TRANSPORT_MODE_FAST ) {
					transport.setTransportMode( Transport.TRANSPORT_MODE_FAST );
				}
			}

		}
	}




	@Override
	public int getConnectionState() {  return connection_state;  }




	@Override
	public long getTimeSinceLastDataMessageReceived() {
		if( last_data_message_received_time == -1 ) {  //never received
			return -1;
		}

		final long now =SystemTime.getCurrentTime();

		if (last_data_message_received_time >now)
			last_data_message_received_time =now;   //time went backwards
		return now -last_data_message_received_time;
	}

	@Override
	public long getTimeSinceGoodDataReceived()
	{
		if (last_good_data_time ==-1)
			return -1;	// never received
		final long now =SystemTime.getCurrentTime();
		if (last_good_data_time >now)
			last_good_data_time =now;   //time went backwards
		return now -last_good_data_time;
	}


	@Override
	public long getTimeSinceLastDataMessageSent() {
		if( last_data_message_sent_time == -1 ) {  //never sent
			return -1;
		}
		final long now =SystemTime.getCurrentTime();
		if (last_data_message_sent_time >now)
			last_data_message_sent_time =now;   //time went backwards
		return now -last_data_message_sent_time;
	}





	@Override
	public long getTimeSinceConnectionEstablished() {
		if( connection_established_time == 0 ) {  //fudge it while the transport is being connected
			return 0;
		}
		final long now =SystemTime.getCurrentTime();
		if (connection_established_time >now)
			connection_established_time =now;
		return now -connection_established_time;
	}

	@Override
	public int
	getConsecutiveNoRequestCount()
	{
		return( consecutive_no_request_count );
	}

	@Override
	public void
	setConsecutiveNoRequestCount(
			int num )
	{
		consecutive_no_request_count	= num;
	}

	protected void decodeBTHandshake( BTHandshake handshake ) {

		if (Logger.isEnabled())
		    Logger.log(new LogEvent(this, LOGID,
		    		"Received handshake with reserved bytes: " +
		    			ByteFormatter.nicePrint(handshake.getReserved(), false)));
		
		PeerIdentityDataID  my_peer_data_id = manager.getPeerIdentityDataID();

		if(getConnectionState() == CONNECTION_FULLY_ESTABLISHED)
		{
			handshake.destroy();
			closeConnectionInternally("peer sent another handshake after the initial connect");
		}

		if( !Arrays.equals( manager.getTargetHash(), handshake.getDataHash() ) ) {
			closeConnectionInternally( "handshake has wrong infohash" );
			handshake.destroy();
			return;
		}

		peer_id = handshake.getPeerId();

		// Decode a client identification string from the given peerID
		this.client_peer_id = this.client = StringInterner.intern(PeerClassifier.getClientDescription( peer_id, network ));

		//make sure the client type is not banned
		if( !PeerClassifier.isClientTypeAllowed( client ) ) {
			closeConnectionInternally( client+ " client type not allowed to connect, banned" );
			handshake.destroy();
			return;
		}

		//make sure we are not connected to ourselves
		if( Arrays.equals( manager.getPeerId(), peer_id ) ) {
			manager.peerVerifiedAsSelf( this );  //make sure we dont do it again
			closeConnectionInternally( "given peer id matches myself" );
			handshake.destroy();
			return;
		}

		//make sure we are not already connected to this peer
		boolean sameIdentity = PeerIdentityManager.containsIdentity( my_peer_data_id, peer_id, getPort());
		boolean sameIP = false;


		//allow loopback connects for co-located proxy-based connections and testing
		boolean same_allowed = COConfigurationManager.getBooleanParameter( "Allow Same IP Peers" ) || ip.equals( "127.0.0.1" );
		if( !same_allowed ){
			if( PeerIdentityManager.containsIPAddress( my_peer_data_id, ip )) {
				sameIP = true;
			}
		}

		if ( sameIdentity ){
			
			boolean close = true;

			PEPeerTransport existing = manager.getTransportFromIdentity( peer_id );

			if ( existing != null ){

				String	existing_ip = existing.getIp();

				if ( connection.isLANLocal()){   //this new connection is lan-local

					// normally we don't allow a lan-local to replace a lan-local connection. There is
					// however one exception - where the existing connection comes from the gateway address
					// and therefore actually denotes an effectively non-lan-local connection. Unfortunately
					// we don't have a good way of finding the default gateway, so just go for ending in .1 or .254

					if ( 	!existing.isLANLocal() ||
							(( existing_ip.endsWith( ".1" ) || existing_ip.endsWith( ".254" ) ) && !existing_ip.equals( ip ))) {  //so drop the existing connection if it is an external (non lan-local) one

						String msg = "Dropping existing non-lanlocal peer connection [" +existing+ "] in favour of [" + this + "]";
								
						Debug.outNoStack( msg );
						
						manager.removePeer( existing, msg );
						
						close = false;
					}
				}else{
					
					boolean this_ipv6		= ip.contains( ":" );
					boolean existing_ipv6 	= existing_ip.contains( ":" );
					
					if ( this_ipv6 != existing_ipv6 ){
					
						boolean close_existing;
						
						if ( prefer_ipv6 ){
							
							close_existing = this_ipv6;
									
						}else{
							
							close_existing = existing_ipv6;
							
						}
						
						if ( close_existing ){
							
							String msg = "Dropping existing peer connection [" +existing+ "] in favour of [" + this + "]";

							manager.removePeer( existing, msg );
							
							close = false;
						}
					}
				}
			}

			if ( close ){
				
				if ( Constants.IS_CVS_VERSION ){
					try{
						List<PEPeer> peers = manager.getPeers();
						String dup_str = "?";
						boolean	dup_ip = false;
						for ( PEPeer p: peers ){
							if ( p == this ){
								continue;
							}
							byte[] id = p.getId();
							if ( Arrays.equals( id, peer_id )){
								dup_ip	= p.getIp().equals( getIp());
								dup_str = p.getClient() + "/" + p.getClientNameFromExtensionHandshake() + "/" + p.getIp() + "/" + p.getPort();
								break;
							}
						}
						String my_str = getClient() + "/" + getIp() + "/" + getPort();

						if ( !dup_ip){
							Debug.outNoStack(
								"Duplicate peer id detected: id=" + ByteFormatter.encodeString( peer_id ) + ": this=" + my_str + ",other=" + dup_str );
						}
					}catch( Throwable e ){
					}
				}
				
				closeConnectionInternally( "peer matches already-connected peer id" );
				
				handshake.destroy();
				
				return;
			}
		}

		if( sameIP ) {
			closeConnectionInternally( "peer matches already-connected IP address, duplicate connections not allowed" );
			handshake.destroy();
			return;
		}

		//make sure we haven't reached our connection limit
		boolean max_reached  = manager.getMaxNewConnectionsAllowed( network ) == 0;

		if (	max_reached &&
				!manager.doOptimisticDisconnect( isLANLocal(), isPriorityConnection(), network ))
		{
			int[] _con_max = manager.getMaxConnections();
			int con_max = _con_max[0] + _con_max[1];
			final String msg = "too many existing peer connections [p" +
			PeerIdentityManager.getIdentityCount( my_peer_data_id )
			+"/g" +PeerIdentityManager.getTotalIdentityCount()
			+", pmx" +PeerUtils.MAX_CONNECTIONS_PER_TORRENT+ "/gmx"
			+PeerUtils.MAX_CONNECTIONS_TOTAL+"/dmx" + con_max + "]";
			//System.out.println( msg );
			closeConnectionInternally( msg );
			handshake.destroy();
			return;
		}

		try{
			closing_mon.enter();

			if( closing ){

				final String msg = "connection already closing";

				closeConnectionInternally( msg );

				handshake.destroy();

				return;
			}

			if ( !PeerIdentityManager.addIdentity( my_peer_data_id, peer_id, getPort(), ip )){

				closeConnectionInternally( "peer matches already-connected peer id" );

				handshake.destroy();

				return;
			}

			identityAdded = true;

		}finally{

			closing_mon.exit();
		}

		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, LOGID, "In: has sent their handshake"));

		// Let's store the reserved bits somewhere so they can be examined later (externally).
		handshake_reserved_bytes = handshake.getReserved();

		/*
		 * Waiting until we've received the initiating-end's full handshake, before sending back our own,
		 * really should be the "proper" behavior.  However, classic BT trackers running NAT checking will
		 * only send the first 48 bytes (up to infohash) of the peer handshake, skipping peerid, which means
		 * we'll never get their complete handshake, and thus never reply, which causes the NAT check to fail.
		 * So, we need to send our handshake earlier, after we've verified the infohash.
		 *
      if( incoming ) {  //wait until we've received their handshake before sending ours
        sendBTHandshake();
      }
		 */

		this.ml_dht_enabled = (handshake_reserved_bytes[7] & 0x01 ) == 1;

			// disable fast if we have per-torrent upload limit as it is non-trivial to enforce for choked fast-start
			// transfers as peer is in the multi-peer upload group (as choked) and in this mode the limit isn't
			// enforced

		fast_extension_enabled =
			BTHandshake.FAST_EXTENSION_ENABLED &&
			manager.getUploadRateLimitBytesPerSecond() == 0 &&
			(handshake_reserved_bytes[7] & 0x04) != 0;

		messaging_mode = decideExtensionProtocol(handshake);

		//extended protocol processing
		if (messaging_mode == MESSAGING_AZMP) {
			/**
			 * We log when a non-Azureus client claims to support extended messaging...
			 * Obviously other Azureus clients do, so there's no point logging about them!
			 */
			if (Logger.isEnabled() && !client.contains("Azureus") && !client.contains(Constants.BIGLYBT_NAME)) {
				Logger.log(new LogEvent(this, LOGID, "Handshake claims extended AZ "
						+ "messaging support... enabling AZ mode."));
			}

			// Ignore the handshake setting - wait for the AZHandshake to indicate
			// support instead.
			this.ml_dht_enabled = false;

			Transport transport = connection.getTransport();
			int padding_mode;
			if ( transport.isEncrypted()){
				if ( transport.isTCP()){
					padding_mode = AZMessageEncoder.PADDING_MODE_NORMAL;
				}else{
					padding_mode = AZMessageEncoder.PADDING_MODE_MINIMAL;
				}
			}else{
				padding_mode = AZMessageEncoder.PADDING_MODE_NONE;
			}
			connection.getIncomingMessageQueue().setDecoder(new AZMessageDecoder());
			connection.getOutgoingMessageQueue().setEncoder(new AZMessageEncoder(padding_mode));

			// We will wait until we get the Az handshake before considering the connection
			// initialised.
			this.sendAZHandshake();
			handshake.destroy();
		}
		else if (messaging_mode == MESSAGING_LTEP) {
			if (Logger.isEnabled()) {
				Logger.log(new LogEvent(this, LOGID, "Enabling LT extension protocol support..."));
			}

			connection.getIncomingMessageQueue().setDecoder(new LTMessageDecoder());
			connection.getOutgoingMessageQueue().setEncoder(new LTMessageEncoder(this));

			generateSessionId();

			/**
			 * We don't need to wait for the LT handshake, nor do we require it, nor
			 * does it matter if the LT handshake comes later, nor does it matter if
			 * it we receive it repeatedly. So there - we can initialise the connection
			 * right now. :P
			 */

				// UNLESS (PARG, 2014/9/11) this is a metadata download in which case we MUST
				// receive the LT handshake (with the metadata size in it) before transitioning
				// to active...

			if ( !is_metadata_download ){

				this.initPostConnection(handshake);
			}

			this.sendLTHandshake();
		}
		else {
			this.client = ClientIdentifier.identifyBTOnly(this.client_peer_id, this.handshake_reserved_bytes);

			connection.getIncomingMessageQueue().getDecoder().resumeDecoding();

			this.initPostConnection(handshake);
		}

	}

	private int decideExtensionProtocol(BTHandshake handshake) {

		int messaging_mode = manager.getExtendedMessagingMode();

			// regardless of what they support, if we're just plain bt then so be it

		if ( messaging_mode == BTHandshake.BT_RESERVED_MODE ){

			return( MESSAGING_BT_ONLY );
		}

		boolean supports_ltep = (handshake.getReserved()[5] & 16) == 16;

		if ( messaging_mode == BTHandshake.LT_RESERVED_MODE ){

			return( supports_ltep?MESSAGING_LTEP:MESSAGING_BT_ONLY );
		}

		boolean supports_azmp = (handshake.getReserved()[0] & 128) == 128;

		if ( !supports_azmp ){

			if ( supports_ltep ){

				return( MESSAGING_LTEP ); // LTEP is supported.
			}

			return( MESSAGING_BT_ONLY ); // LTEP isn't supported.
		}

		if ( !supports_ltep ){

				// 	Check if the client is misbehaving...

			if (client.contains("Plus!")){


				if (Logger.isEnabled())
					Logger.log(new LogEvent(this, LOGID, "Handshake mistakingly indicates"
							+ " extended AZ messaging support...ignoring."));

				return MESSAGING_BT_ONLY;
			}

			return( MESSAGING_AZMP );
		}



		boolean enp_major_bit = (handshake.getReserved()[5] & 2) == 2;
		boolean enp_minor_bit = (handshake.getReserved()[5] & 1) == 1;

		// Only enable one of the blocks below.
		String their_ext_preference = ((enp_major_bit == enp_minor_bit) ? "Force " : "Prefer ") + ((enp_major_bit) ? "AZMP" : "LTEP");

		// Force AZMP block.
		String our_ext_preference = "Force AZMP";
		boolean use_azmp = enp_major_bit || enp_minor_bit; // Anything other than Force LTEP, then we force AZMP to be used.
		boolean we_decide = use_azmp;

		// Prefer AZMP block (untested).
		/*
		String our_ext_preference = "Prefer AZMP";
		boolean use_azmp = enp_major_bit; // Requires other client to prefer or force AZMP.
		boolean we_decide = use_azmp && !enp_minor_bit; // We decide only if we are using AZMP and the other client didn't force it.
		*/

		// Prefer LTEP block (untested).
		/*
		String our_ext_preference = "Prefer LTEP";
		boolean use_azmp = enp_major_bit && enp_minor_bit; // Only use it Force AZMP is enabled.
		boolean we_decide = enp_minor_bit && !use_azmp; // We decide only if we are using LTEP and the other client didn't force it.
		*/

		if (Logger.isEnabled()) {
			String msg = "Peer supports both AZMP and LTEP: ";
			msg += "\"" + our_ext_preference + "\"" + (we_decide ? ">" : "<") + ((our_ext_preference.equals(their_ext_preference)) ? "= " : " ");
			msg += "\"" + their_ext_preference + "\" - using " + (use_azmp ? "AZMP" : "LTEP");
			Logger.log(new LogEvent(this, LOGID, msg));
		}

		return( use_azmp?MESSAGING_AZMP:MESSAGING_LTEP );

	}


  protected void decodeLTHandshake(LTHandshake handshake) {
	  String lt_handshake_name = handshake.getClientName();
	  if (lt_handshake_name != null) {
		  this.client_handshake = StringInterner.intern(lt_handshake_name);
		  this.client = StringInterner.intern(ClientIdentifier.identifyLTEP(this.client_peer_id, this.client_handshake, this.peer_id));
	  }
	  if(handshake.getTCPListeningPort() > 0 )
	  {
		  // Only use crypto if it was specifically requested. Not sure what the default
		  // should be if they haven't indicated...
		  Boolean crypto_requested = handshake.isCryptoRequested();
		  byte handshake_type = (crypto_requested != null && crypto_requested.booleanValue()) ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
		  tcp_listen_port = handshake.getTCPListeningPort();
		  peer_item_identity = PeerItemFactory.createPeerItem(
			  ip, tcp_listen_port,
			  PeerItem.convertSourceID(peer_source),
			  handshake_type,
			  udp_listen_port, // probably none
			  crypto_level,
			  0
			  );
	  }

	  if(handshake.isUploadOnly())
	  {
		  relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;
		  checkSeed();
	  }

	  if(AddressUtils.isGlobalAddressV6(handshake.getIPv6()))
		  alternativeAddress = handshake.getIPv6();

	  LTMessageEncoder encoder = (LTMessageEncoder)connection.getOutgoingMessageQueue().getEncoder();
	  encoder.updateSupportedExtensions(handshake.getExtensionMapping());
	  this.ut_pex_enabled = encoder.supportsUTPEX();

	  if ( is_metadata_download ){

		  if ( encoder.supportsUTMetaData()){

			  int	mds = handshake.getMetadataSize();

			  if ( mds > 0 ){

				  spoofMDAvailability( mds );
			  }
		  }

		  if ( current_peer_state != PEPeer.TRANSFERING ){

			  	// this was deferred in the BT handshake decode for metadata downloads...

			  initPostConnection( null );
		  }
	  }

	  /**
	   * Grr... this is one thing which I'm sure I had figured out much better than it is here...
	   * Basically, we "initialise" the connection at the BT handshake stage, because the LT handshake
	   * is mandatory or required to come first (unlike the AZ one).
	   *
	   * But when we receive an LT handshake, we have to "initialise" it like we did previously, because
	   * we may have to set the internals up to indicate if PEX is supported.
	   *
	   * I'm not entirely sure this method is meant to be called more than once, and I'm less convinced
	   * that it's safe to do it repeatedly over the lifetime of a properly-initialised, actually-doing-stuff
	   * connection... but I'll worry about that later.
	   */
	  this.doPostHandshakeProcessing();

	  handshake.destroy();
  }

  protected void decodeAZHandshake(AZHandshake handshake) {
	  if(getConnectionState() == CONNECTION_FULLY_ESTABLISHED)
	  {
		  handshake.destroy();
		  closeConnectionInternally("peer sent another az-handshake after the intial connect");
	  }

		this.client_handshake = StringInterner.intern(handshake.getClient());
		this.client_handshake_version = StringInterner.intern(handshake.getClientVersion());
		this.client = StringInterner.intern(ClientIdentifier.identifyAZMP(this.client_peer_id, client_handshake, client_handshake_version, this.peer_id));

		if (handshake.getTCPListenPort() > 0)
		{ // use the ports given in handshake
			tcp_listen_port = handshake.getTCPListenPort();
			udp_listen_port = handshake.getUDPListenPort();
			udp_non_data_port = handshake.getUDPNonDataListenPort();
			final byte type = handshake.getHandshakeType() == AZHandshake.HANDSHAKE_TYPE_CRYPTO ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;

			// remake the id using the peer's remote listen port instead of
			// their random local port
			peer_item_identity = PeerItemFactory.createPeerItem(ip, tcp_listen_port, PeerItem.convertSourceID(peer_source), type, udp_listen_port, crypto_level, 0);
		}

		if(AddressUtils.isGlobalAddressV6(handshake.getIPv6()))
			alternativeAddress = handshake.getIPv6();


		if(handshake.getReconnectSessionID() != null)
		{
			if (Logger.isEnabled()) {
				Logger.log(new LogEvent(this, LOGID, LogEvent.LT_INFORMATION, "received reconnect request ID: "+handshake.getReconnectSessionID().toBase32String()));
			}
			checkForReconnect(handshake.getReconnectSessionID());
		}

		if(handshake.getRemoteSessionID() != null)
			peerSessionID = handshake.getRemoteSessionID();

		if (handshake.isUploadOnly())
		{
			relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;
			checkSeed();
		}


		String[] supported_message_ids = handshake.getMessageIDs();
		byte[] supported_message_versions = handshake.getMessageVersions();

		//find mutually available message types
		final ArrayList messages = new ArrayList();

		for (int i = 0; i < handshake.getMessageIDs().length; i++)
		{
			Message msg = MessageManager.getSingleton().lookupMessage(supported_message_ids[i]);
			if (msg != null){
					// mutual support!
				messages.add(msg);

				byte supported_version = supported_message_versions[i];

				String 	featureID	= msg.getFeatureID();
				int		subID		= msg.getFeatureSubID();

				if ( featureID == BTMessage.BT_FEATURE_ID ){
					
					switch( subID ){
						case BTMessage.SUBID_BT_BITFIELD:
							other_peer_bitfield_version = supported_version;
							break;
						case BTMessage.SUBID_BT_CANCEL:
							other_peer_cancel_version = supported_version;
							break;
						case BTMessage.SUBID_BT_CHOKE:
							other_peer_choke_version = supported_version;
							break;
						case BTMessage.SUBID_BT_HANDSHAKE:
							other_peer_handshake_version = supported_version;
							break;
						case BTMessage.SUBID_BT_HAVE:
							other_peer_bt_have_version = supported_version;
							break;
						case BTMessage.SUBID_BT_INTERESTED:
							other_peer_interested_version = supported_version;
							break;
						case BTMessage.SUBID_BT_KEEP_ALIVE:
							other_peer_keep_alive_version = supported_version;
							break;
						case BTMessage.SUBID_BT_PIECE:
							other_peer_piece_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_UNCHOKE:
							other_peer_unchoke_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_UNINTERESTED:
							other_peer_uninterested_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_REQUEST:
							other_peer_request_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_SUGGEST_PIECE:
							other_peer_suggest_piece_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_HAVE_ALL:
							other_peer_have_all_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_HAVE_NONE:
							other_peer_have_none_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_REJECT_REQUEST:
							other_peer_reject_request_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_ALLOWED_FAST:
							other_peer_allowed_fast_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_DHT_PORT:
							this.ml_dht_enabled = true;
							break;
						case  BTMessage.SUBID_BT_HASH_REQUEST:
							other_peer_hash_request_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_HASHES:
							other_peer_hashes_version = supported_version;
							break;
						case  BTMessage.SUBID_BT_HASH_REJECT:
							other_peer_hash_reject_version = supported_version;
							break;

					}
				}else if ( featureID == AZMessage.AZ_FEATURE_ID ){
									
					switch( subID ){
						case AZMessage.SUBID_AZ_PEER_EXCHANGE:
							other_peer_pex_version = supported_version;
							break;
						case AZMessage.SUBID_AZ_REQUEST_HINT:
							other_peer_az_request_hint_version = supported_version;
							break;
						case AZMessage.SUBID_AZ_HAVE:
							other_peer_az_have_version = supported_version;
							break;
						case AZMessage.SUBID_AZ_BAD_PIECE:
							other_peer_az_bad_piece_version = supported_version;
							break;
						case AZMessage.SUBID_AZ_STAT_REQUEST:
							other_peer_az_stats_request_version = supported_version;
							break;
						case AZMessage.SUBID_AZ_STAT_REPLY:
							other_peer_az_stats_reply_version = supported_version;
							break;
						case AZMessage.SUBID_AZ_METADATA:
							other_peer_az_metadata_version = supported_version;
							break;
					}
				}
				
					// we expect unmatched ones here at the moment as we're not
					// dealing with them yet or they don't make sense.
					// for example AZVER
				
			}
		}

		if ( is_metadata_download ){

			int	 mds = handshake.getMetadataSize();

			if ( mds > 0 ){

				  manager.setTorrentInfoDictSize( mds );
			}
		}

		supported_messages = (Message[]) messages.toArray(new Message[messages.size()]);

		if(outgoing_piece_message_handler != null){
			outgoing_piece_message_handler.setPieceVersion(other_peer_piece_version);
		}

		if ( outgoing_have_message_aggregator != null ){
			outgoing_have_message_aggregator.setHaveVersion(other_peer_bt_have_version, other_peer_az_have_version);
		}

		this.initPostConnection(handshake);
	}

  	private void
  	spoofMDAvailability(
  		int		mds )
  	{
		  int	md_pieces = ( mds + 16*1024 - 1 )/(16*1024);

		  manager.setTorrentInfoDictSize( mds );

		  BitFlags tempHavePieces = new BitFlags(nbPieces);

		  for ( int i=0;i<md_pieces;i++){

			  tempHavePieces.set(i);
		  }

		  peerHavePieces = tempHavePieces;

		  addAvailability();

		  really_choked_by_other_peer = false;

		  calculatePiecePriorities();
  	}

  	private void initPostConnection(Message handshake) {
		changePeerState(PEPeer.TRANSFERING);
		connection_state = PEPeerTransport.CONNECTION_FULLY_ESTABLISHED;
		sendBitField();
		if ( handshake != null ){
			handshake.destroy();
		}
		addAvailability();
		sendMainlineDHTPort();

		if ( is_metadata_download ){
			interested_in_other_peer = true;
		}
  	}

	protected void
	decodeHaveAll(
		BTHaveAll have_all )
	{
		have_all.destroy();

		received_bitfield = true;

		if ( is_metadata_download ){

			return;
		}

		try{
			closing_mon.enter();

			if ( !closing ){

				final BitFlags tempHavePieces;

				if ( peerHavePieces ==null ){

					tempHavePieces = new BitFlags(nbPieces);

				}else{

					tempHavePieces = peerHavePieces;

					removeAvailability();
				}

				tempHavePieces.setAll();

				for ( int i=0; i<nbPieces; i++ ){

					manager.updateSuperSeedPiece( this, i );
				}

				peerHavePieces = tempHavePieces;

				addAvailability();

				checkSeed();

				checkInterested();
			}
		}finally{

			closing_mon.exit();
		}
	}

	protected void
	decodeHaveNone(
		BTHaveNone have_none )
	{
		have_none.destroy();

			// bug in older clients - 'upload_only' was being sent by superseeds in AZ handshakes and causing relative-peer
			// related disconnects. The client also sent an initial 'have-none' so we can use this to clear the erroneous flag		
		
		if ( ( relativeSeeding & RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED ) != 0 ){
			
			relativeSeeding &= ~RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;
		}
		
		received_bitfield = true;

		if ( is_metadata_download ){

			return;
		}

		try{
			closing_mon.enter();

			if ( !closing ){

				final BitFlags tempHavePieces;

				if ( peerHavePieces ==null ){

					tempHavePieces = new BitFlags(nbPieces);

				}else{

					tempHavePieces = peerHavePieces;

					removeAvailability();
				}

				tempHavePieces.clear();

				peerHavePieces = tempHavePieces;

					//some of these kinda pointless but whatever

				addAvailability();

				checkSeed();

				checkInterested();

				checkFast( tempHavePieces );
			}
		}finally{

			closing_mon.exit();
		}
	}

	protected void decodeBitfield( BTBitfield bitfield )
	{
		received_bitfield = true;

		if ( is_metadata_download ){

			bitfield.destroy();

			return;
		}

		final DirectByteBuffer field =bitfield.getBitfield();

		final byte[] dataf =new byte[(nbPieces +7) /8];

		if( field.remaining( DirectByteBuffer.SS_PEER ) < dataf.length ) {
			final String error = toString() + " has sent invalid Bitfield: too short [" +field.remaining( DirectByteBuffer.SS_PEER )+ "<" +dataf.length+ "]";
			Debug.out( error );
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR, error ));
			bitfield.destroy();
			return;
		}

		field.get( DirectByteBuffer.SS_PEER, dataf );

		try{
			closing_mon.enter();
			if (closing)
				bitfield.destroy();
			else
			{
				final BitFlags tempHavePieces;
				if (peerHavePieces ==null)
				{
					tempHavePieces =new BitFlags(nbPieces);
				} else
				{
					tempHavePieces =peerHavePieces;
					removeAvailability();
				}
				for (int i =0; i <nbPieces; i++)
				{
					final int index =i /8;
					final int bit =7 -(i %8);
					final byte bData =dataf[index];
					final byte b =(byte) (bData >>bit);
					if ((b &0x01) ==1)
					{
						tempHavePieces.set(i);
						manager.updateSuperSeedPiece(this,i);
					}
				}

				bitfield.destroy();

				peerHavePieces =tempHavePieces;
				addAvailability();

				checkSeed();
				checkInterested();

				checkFast( tempHavePieces );
			}
		}
		finally{
			closing_mon.exit();
		}
	}

	@Override
	public void
	setSuspendedLazyBitFieldEnabled(
		boolean	enable )
	{
		manual_lazy_bitfield_control	= enable;

		if ( !enable ){

			int[] pending = manual_lazy_haves;

			manual_lazy_haves = null;

			if ( pending != null ){

				sendLazyHaves( pending, true );
			}
		}
	}


	protected void decodeMainlineDHTPort(BTDHTPort port) {
		int i_port = port.getDHTPort();
		port.destroy();

		if (!this.ml_dht_enabled) {return;}
		MainlineDHTProvider provider = getDHTProvider();
		if (provider == null) {return;}

		if ( network == AENetworkClassifier.AT_PUBLIC ){
			try {provider.notifyOfIncomingPort(getIp(), i_port);}
			catch (Throwable t) {Debug.printStackTrace(t);}
		}
	}

	protected void decodeChoke( BTChoke choke ) {
		choke.destroy();

		if ( is_metadata_download ){
			return;
		}

		if (!really_choked_by_other_peer)
		{
			really_choked_by_other_peer = true;
			calculatePiecePriorities();
			cancelRequests();
		}
	}


	protected void decodeUnchoke( BTUnchoke unchoke ) {
		unchoke.destroy();

		if ( is_metadata_download ){
			return;
		}

		if (really_choked_by_other_peer)
		{
			really_choked_by_other_peer = false;
			calculatePiecePriorities();
		}
	}

	protected void
	decodeInterested(
		BTInterested interested )
	{
		interested.destroy();

			// Don't allow known seeds to be interested in us

		other_peer_interested_in_me = !(isSeed() || isRelativeSeed());

		if ( 	other_peer_interested_in_me &&
				!is_upload_disabled &&
				fast_unchoke_new_peers &&
				isChokedByMe() &&
				getData( "fast_unchoke_done" ) == null ){

			setData( "fast_unchoke_done", "" );

			sendUnChoke();
		}
	}


	protected void decodeUninterested( BTUninterested uninterested ) {
		uninterested.destroy();
		other_peer_interested_in_me = false;

		//force send any pending haves in case one of them would make the other peer interested again
		if( outgoing_have_message_aggregator != null ) {
			outgoing_have_message_aggregator.forceSendOfPending();
		}

	}




	protected void decodeHave( BTHave have ) {
		final int pieceNumber =have.getPieceNumber();
		have.destroy();

		if ( is_metadata_download ){

			return;
		}

		if ((pieceNumber >=nbPieces) ||(pieceNumber <0)) {
			closeConnectionInternally("invalid pieceNumber: " +pieceNumber);
			return;
		}

		if (closing)
			return;

		if (peerHavePieces ==null)
			peerHavePieces =new BitFlags(nbPieces);

		if (!peerHavePieces.flags[pieceNumber])
		{
			if (!interested_in_other_peer &&diskManager.isInteresting(pieceNumber)&&!is_download_disabled)
			{
                connection.getOutgoingMessageQueue().addMessage(new BTInterested(other_peer_interested_version), false);
				interested_in_other_peer =true;
			}
			peerHavePieces.set(pieceNumber);

			final int pieceLength =manager.getPieceLength(pieceNumber);
			manager.havePiece(pieceNumber, pieceLength, this);

			checkSeed(); // maybe a seed using lazy bitfield, or suddenly became a seed;
			if ( other_peer_interested_in_me ){
				if ( isSeed() || isRelativeSeed()){
					other_peer_interested_in_me = false; // never consider seeds interested
				}
			}
            peer_stats.hasNewPiece(pieceLength);
        }
    }

    protected void
    decodeAZHave(
    	AZHave have )
    {
        final int[] pieceNumbers = have.getPieceNumbers();

        have.destroy();

        if ( closing ){

            return;
        }

        if ( peerHavePieces == null ){

            peerHavePieces = new BitFlags( nbPieces );
        }

        boolean	send_interested = false;
        boolean	new_have		= false;

        for (int i=0;i<pieceNumbers.length;i++){

        	int pieceNumber = pieceNumbers[i];

	        if ((pieceNumber >= nbPieces) ||(pieceNumber <0)) {

	            closeConnectionInternally("invalid pieceNumber: " +pieceNumber);

	            return;
	        }

	        if ( !peerHavePieces.flags[pieceNumber]){

	        	new_have	= true;

	            if (	!( send_interested || interested_in_other_peer || is_download_disabled ) &&
	            		diskManager.isInteresting(pieceNumber)){

	            	send_interested = true;
	            }

	            peerHavePieces.set(pieceNumber);

	            final int pieceLength =manager.getPieceLength(pieceNumber);

	            manager.havePiece(pieceNumber, pieceLength, this);

	            peer_stats.hasNewPiece(pieceLength);
	        }
        }

        if ( new_have ){

            checkSeed(); // maybe a seed using lazy bitfield, or suddenly became a seed;

            if ( other_peer_interested_in_me ){
            	if ( isSeed() || isRelativeSeed()){
            		other_peer_interested_in_me = false; // never consider seeds interested
            	}
            }
        }

        if ( send_interested ){

            connection.getOutgoingMessageQueue().addMessage(new BTInterested(other_peer_interested_version), false);

            interested_in_other_peer = true;
        }
    }

    protected long
    getBytesDownloaded()
    {
		if (peerHavePieces ==null ||peerHavePieces.flags.length ==0)
			return 0;

		final long	total_done;

		if ( peerHavePieces.flags[nbPieces-1] ){

			total_done = ((long)(peerHavePieces.nbSet -1) *diskManager.getPieceLength()) +diskManager.getPieceLength( nbPieces -1 );

		}else{

			total_done =(long)peerHavePieces.nbSet *diskManager.getPieceLength();
		}

		return( Math.min( total_done, diskManager.getTotalLength()));
    }

    @Override
    public long
    getBytesRemaining()
    {
    	return( diskManager.getTotalLength() - getBytesDownloaded());
    }

    @Override
    public void
    sendBadPiece(
  	  int		piece_number )
    {
  	  if ( bad_piece_supported ){

  		  AZBadPiece	bp = new AZBadPiece( piece_number, other_peer_az_bad_piece_version );

  		  connection.getOutgoingMessageQueue().addMessage( bp, false );
        }
    }

    protected void
    decodeAZBadPiece(
    	AZBadPiece bad_piece )
    {
        final int piece_number = bad_piece.getPieceNumber();

        bad_piece.destroy();

        manager.badPieceReported( this, piece_number );
    }

    @Override
    public void
    sendStatsRequest(
  	  Map		request )
    {
  	  if ( stats_request_supported ){

  		  AZStatRequest	sr = new AZStatRequest( request, other_peer_az_stats_request_version );

  		  connection.getOutgoingMessageQueue().addMessage( sr, false );
        }
    }

    protected void
    decodeAZStatsRequest(
    	AZStatRequest request )
    {
        Map req = request.getRequest();

        request.destroy();

        manager.statsRequest( this, req );
    }

    @Override
    public void
    sendStatsReply(
  	  Map		reply )
    {
  	  if ( stats_reply_supported ){

  		  AZStatReply	sr = new AZStatReply( reply, other_peer_az_stats_reply_version );

  		  connection.getOutgoingMessageQueue().addMessage( sr, false );
        }
    }

    protected void
    decodeAZStatsReply(
    	AZStatReply reply )
    {
        Map rep = reply.getReply();

        reply.destroy();

        manager.statsReply( this, rep );
    }

	protected void decodeRequest( BTRequest request ) {
		final int number = request.getPieceNumber();
		final int offset = request.getPieceOffset();
		final int length = request.getLength();
		request.destroy();

		if( !manager.validateReadRequest( this, number, offset, length ) ) {
			closeConnectionInternally( "request for piece #" + number + ":" + offset + "->" + (offset + length -1) + " is an invalid request" );
			return;
		}

		if ( manager.getHiddenPiece() == number ){
			closeConnectionInternally( "request for piece #" + number + " is invalid as piece is hidden" );
			return;
		}

		boolean	request_ok = !is_upload_disabled;

		if ( request_ok ){

			if ( choking_other_peer ){

				try{
					general_mon.enter();

					int[][] pieces = (int[][])getUserData( KEY_ALLOWED_FAST_SENT );

					if ( pieces != null ){

						for (int i=0;i<pieces.length;i++){

							if ( pieces[i][0] == number ){

								if ( pieces[i][1] >= length ){

									 pieces[i][1] -= length;

									 if ( DEBUG_FAST ){
										 System.out.println( "Permitting fast-allowed request for " + number + "/" + offset + "/" + length + " to " + getIp());
									 }

									 request_ok = true;

									 createPieceMessageHandler();

									 break;

								}else{

									manager.reportBadFastExtensionUse( this );
								}
							}
						}
					}
				}finally{

					general_mon.exit();
				}
			}else{

				request_ok = true;
			}
		}

		if ( request_ok ){

			if ( outgoing_piece_message_handler == null || !outgoing_piece_message_handler.addPieceRequest( number, offset, length )){

				sendRejectRequest( number, offset, length );
			}

			allowReconnect = true;

		}else{
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID, "decodeRequest(): peer request for piece #"
						+ number + ":" + offset + "->" + (offset + length -1)
						+ " ignored as peer is currently choked."));

			sendRejectRequest( number, offset, length );
		}
	}

	@Override
	public void
	sendRejectRequest(
		DiskManagerReadRequest	request )
	{
		sendRejectRequest( request.getPieceNumber(), request.getOffset(), request.getLength());
	}

	private void
	sendRejectRequest(
		int		number,
		int		offset,
		int		length )
	{
		if ( fast_extension_enabled && !closing ){

			// System.out.println( "Sending reject request " + number + "/" + offset + "/" + length + " to " + getIp());

			BTRejectRequest	reject = new BTRejectRequest( number, offset, length, other_peer_reject_request_version );

	  		connection.getOutgoingMessageQueue().addMessage( reject, false );
		}
	}

	protected void decodePiece( BTPiece piece ) {
		final int pieceNumber = piece.getPieceNumber();
		final int offset = piece.getPieceOffset();
		final DirectByteBuffer payload = piece.getPieceData();
		final int length = payload.remaining( DirectByteBuffer.SS_PEER );

		/*
    if ( AEDiagnostics.CHECK_DUMMY_FILE_DATA ){
      int pos = payload.position( DirectByteBuffer.SS_PEER );
      long  off = ((long)number) * getControl().getPieceLength(0) + offset;
      for (int i=0;i<length;i++){
        byte  v = payload.get( DirectByteBuffer.SS_PEER );
        if ((byte)off != v ){
          System.out.println( "piece: read is bad at " + off + ": expected = " + (byte)off + ", actual = " + v );
          break;
        }
        off++;
      }
      payload.position( DirectByteBuffer.SS_PEER, pos );
    }
		 */

		final Object error_msg =
			new Object()
		{
			public final String
			toString()
			{
				return( "decodePiece(): Peer has sent piece #" + pieceNumber + ":" + offset + "->"	+ (offset + length -1) + ", " );
			}
		};

	  if( !manager.validatePieceReply( this, pieceNumber, offset, payload ) ) {
			peer_stats.bytesDiscarded( length );
			manager.discarded( this, length );
			requests_discarded++;
			printRequestStats();
			piece.destroy();
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
						error_msg
						+"but piece block discarded as invalid."));
			return;
		}

	  if ( DEBUG_FAST ){
		  if ( fast_extension_enabled && really_choked_by_other_peer ){

			  System.out.println( "Received allow-fast piece for " + pieceNumber + "/" + offset + "/" + length + " from " + getIp());
		  }
	  }
		final DiskManagerReadRequest existing_request = lookupRequest( pieceNumber, offset, length );

		boolean piece_error = true;

		if( existing_request != null ) {  //from active request
			if ( existing_request.isLatencyTest()){
				long latency = SystemTime.getMonotonousTime() - existing_request.getTimeSent();

				if ( latency > 0 ){
					request_latency = latency;
				}
			}
			removeRequest( existing_request );
			final long now =SystemTime.getCurrentTime();
			resetRequestsTime(now);

			if( manager.isWritten( pieceNumber, offset ) ) {  //oops, looks like this block has already been written
				peer_stats.bytesDiscarded( length );
				manager.discarded( this, length );

				if( manager.isInEndGameMode() ) {  //we're probably in end-game mode then
					if (last_good_data_time !=-1 &&now -last_good_data_time <= PEPeerControl.SNUB_MILLIS )
						setSnubbed(false);
					last_good_data_time =now;
					requests_discarded_endgame++;
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_INFORMATION,
								error_msg
								+"but piece block ignored as already written in end-game mode."));
				}
				else {
					// if they're not snubbed, then most likely this peer got a re-request after some other peer
					// snubbed themselves, and the slow peer finially finished the piece, but before this peer did
					// so give credit to this peer anyway for having delivered a block at this time
					if (!isSnubbed())
						last_good_data_time =now;
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_WARNING,
								error_msg
								+"but piece block discarded as already written."));
					requests_discarded++;
				}

				printRequestStats();
			}
			else {  //successfully received block!
				manager.writeBlock( pieceNumber, offset, payload, this, false);
				if (last_good_data_time !=-1 &&now -last_good_data_time <=60 *1000)
					setSnubbed( false );
				last_good_data_time =now;
				requests_completed++;
				piece_error = false;  //dont destroy message, as we've passed the payload on to the disk manager for writing
			}
		}
		else {  //initial request may have already expired, but check if we can use the data anyway
			if( !manager.isWritten( pieceNumber, offset ) ) {
				final boolean ever_requested;

				DiskManagerReadRequest request = manager.createDiskManagerRequest( pieceNumber, offset, length );

				try{  recent_outgoing_requests_mon.enter();
				ever_requested = recent_outgoing_requests.containsKey( request );
				}
				finally{  recent_outgoing_requests_mon.exit();  }

				if( ever_requested ) { //security-measure: we dont want to be accepting any ol' random block
					manager.writeBlock( pieceNumber, offset, payload, this, true);
					final long now =SystemTime.getCurrentTime();
					if (last_good_data_time !=-1 &&now -last_good_data_time <=60 *1000)
						setSnubbed(false);
					resetRequestsTime(now);
					last_good_data_time =now;
					requests_recovered++;
					printRequestStats();
					piece_error = false;  //dont destroy message, as we've passed the payload on to the disk manager for writing
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_INFORMATION,
								error_msg
								+"expired piece block data recovered as useful."));
				}
				else {

					System.out.println( "[" +client+ "]" +error_msg + "but expired piece block discarded as never requested." );

					peer_stats.bytesDiscarded( length );
					manager.discarded( this, length );
					requests_discarded++;
					printRequestStats();
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_ERROR,
								error_msg
								+"but expired piece block discarded as never requested."));
				}
			}
			else {
				peer_stats.bytesDiscarded( length );
				manager.discarded( this, length );
				requests_discarded++;
				printRequestStats();
				if (Logger.isEnabled())
					Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_WARNING,
							error_msg
							+"but expired piece block discarded as already written."));
			}
		}

		if( piece_error )
			piece.destroy();
		else
			allowReconnect = true;
	}



	protected void
	decodeCancel(
		BTCancel cancel )
	{
		int number = cancel.getPieceNumber();
		int offset = cancel.getPieceOffset();
		int length = cancel.getLength();
		cancel.destroy();
		if(outgoing_piece_message_handler != null)
			outgoing_piece_message_handler.removePieceRequest( number, offset, length );
	}

	protected void
	decodeRejectRequest(
		BTRejectRequest reject )
	{
		int number = reject.getPieceNumber();
		int offset = reject.getPieceOffset();
		int length = reject.getLength();
		reject.destroy();

		final DiskManagerReadRequest request = manager.createDiskManagerRequest( number, offset, length );

		if ( hasBeenRequested( request )){

			removeRequest( request );

			manager.requestCanceled( request );

				// if fast-allowed block rejected, remove from set so we don't re-request it

			try{
				general_mon.enter();

				List<Integer> pieces = (List<Integer>)getUserData( KEY_ALLOWED_FAST_RECEIVED );

				if ( pieces != null ){

					pieces.remove( new Integer( number ));

					if ( pieces.size() == 0 ){

						setUserData( KEY_ALLOWED_FAST_RECEIVED, null );
					}
				}

				int[]	priorities = piece_priority_offsets;

				if ( priorities != null ){

					priorities[number] = Integer.MIN_VALUE;
				}

				calculatePiecePriorities();

			}finally{

				general_mon.exit();
			}
		}
	}

	@Override
	public void 
	sendHashRequest(
		HashRequest req)
	{
		BTHashRequest	request = new BTHashRequest( req.getRootHash(), req.getBaseLayer(), req.getOffset(), req.getLength(), req.getProofLayers(), other_peer_hash_request_version );

  		connection.getOutgoingMessageQueue().addMessage( request, false );	
  	}
	
	private void
	decodeHashRequest(
		BTHashRequest	request )
	{
		try{

			OutgoingMessageQueue omq = connection.getOutgoingMessageQueue();
			
				// although hash rates are limited in the PEPeerControlImpl::PeerStats code we have cases
				// where a peer's queued protocol data grows at 1 MB every 2 seconds... Given that hash requests
				// are the only significantly sized messages classified as "protocol" I'm going to cap this and
				// see how that helps
			
			if ( omq.getProtocolQueuedBytes() > 4*1024*1024 ){
							
				BTHashReject	reject = new BTHashReject( request.getPiecesRoot(), request.getBaseLayer(), request.getIndex(), request.getLength(), request.getProofLayers(), other_peer_hash_reject_version );

				//System.out.println( "Sending BT_HASH_REJECT: index=" + request.getIndex() + ", length=" + request.getLength());

				omq.addMessage( reject, false );		

			}else{
				
				manager.getHashHandler().receivedHashRequest( 
					this,
					( hashes )->{
										
						if ( hashes != null ){
						
							BTHashes	bt_hashes = new BTHashes( request.getPiecesRoot(), request.getBaseLayer(), request.getIndex(), request.getLength(), request.getProofLayers(), hashes, other_peer_hashes_version );
	
							//System.out.println( "Sending BT_HASHES: index=" + request.getIndex() + ", length=" + request.getLength());
							
							omq.addMessage( bt_hashes, false );		
	
						}else{
							
							BTHashReject	reject = new BTHashReject( request.getPiecesRoot(), request.getBaseLayer(), request.getIndex(), request.getLength(), request.getProofLayers(), other_peer_hash_reject_version );
	
							//System.out.println( "Sending BT_HASH_REJECT: index=" + request.getIndex() + ", length=" + request.getLength());
	
							omq.addMessage( reject, false );		
						}
					},
					request.getPiecesRoot(), 
					request.getBaseLayer(), 
					request.getIndex(), 
					request.getLength(), 
					request.getProofLayers());
			}

		}finally{
			
			request.destroy();
		}
	}
	
	private void
	decodeHashes(
		BTHashes	hashes )
	{
		try{
			
			manager.getHashHandler().receivedHashes( this, hashes.getPiecesRoot(), hashes.getBaseLayer(), hashes.getIndex(), hashes.getLength(), hashes.getProofLayers(), hashes.getHashes());
			
		}finally{
			
			hashes.destroy();
		}	
	}
	
	private void
	decodeHashReject(
		BTHashReject	reject )
	{
		try{
		
			manager.getHashHandler().rejectedHashes( this, reject.getPiecesRoot(), reject.getBaseLayer(), reject.getIndex(), reject.getLength(), reject.getProofLayers());

		}finally{
			
			reject.destroy();
		}	
	}
	
	private void
	decodeAllowedFast(
		BTAllowedFast	allowed )
	{
		int	piece = allowed.getPieceNumber();

		allowed.destroy();

		if ( piecePicker.getNbPiecesDone() > ALLOWED_FAST_OTHER_PEER_PIECE_MAX ){

				// we have too many pieces already, ignore

			return;
		}

		if ( DEBUG_FAST ){
			System.out.println( "Received allow-fast " + piece + " from " + getIp());
		}

		try{
			general_mon.enter();

			List<Integer> pieces = (List<Integer>)getUserData( KEY_ALLOWED_FAST_RECEIVED );

			if ( pieces == null ){

				pieces = new ArrayList<>(ALLOWED_FAST_OTHER_PEER_PIECE_MAX);

				setUserData( KEY_ALLOWED_FAST_RECEIVED, pieces );
			}

			if ( pieces.size() < ALLOWED_FAST_OTHER_PEER_PIECE_MAX * 2 ){

				Integer i = new Integer( piece );

				if ( !pieces.contains( i ) && i >=0 && i < nbPieces ){

					pieces.add( i );

					calculatePiecePriorities();
				}
			}
		}finally{

			general_mon.exit();
		}
	}

	private void
	sendAllowFast(
		int		number )
	{
		if ( fast_extension_enabled ){

			if ( DEBUG_FAST ){
				System.out.println( "Sending allow-fast " + number + " to " + getIp());
			}

			BTAllowedFast	af = new BTAllowedFast( number, other_peer_allowed_fast_version );

	  		connection.getOutgoingMessageQueue().addMessage( af, false );
		}
	}

	protected void
	calculatePiecePriorities()
	{
		try{
			general_mon.enter();

			if ( really_choked_by_other_peer ){

				List<Integer> pieces = (List<Integer>)getUserData( KEY_ALLOWED_FAST_RECEIVED );

				if ( pieces == null ){

					effectively_choked_by_other_peer = true;

					piece_priority_offsets	= null;

				}else{

					int[]	priorities = piece_priority_offsets;

					if ( priorities == null ){

						priorities = new int[nbPieces];

						Arrays.fill( priorities, Integer.MIN_VALUE );
					}

					for ( int i: pieces ){

						priorities[i] = 0;
					}

					piece_priority_offsets = priorities;

					if ( effectively_choked_by_other_peer ){

						effectively_choked_by_other_peer = false;

						effectively_unchoked_time	= SystemTime.getMonotonousTime();
					}
				}
			}else{

				if ( effectively_choked_by_other_peer ){

					effectively_choked_by_other_peer = false;

					effectively_unchoked_time	= SystemTime.getMonotonousTime();
				}

				piece_priority_offsets = null;
			}
		}finally{

			general_mon.exit();
		}
	}

	protected void
	checkFast(
		BitFlags	flags )
	{
		if ( 	fast_extension_enabled &&
				!is_upload_disabled &&
				!(isSeed() || isRelativeSeed()) &&
				PeerClassifier.fullySupportsFE( client_peer_id )){

				// if already has enough pieces then bail

			if ( flags.nbSet >= ALLOWED_FAST_OTHER_PEER_PIECE_MAX ){

				return;
			}

			if ( !manager.isFastExtensionPermitted( this )){

				return;
			}

			int[][] pieces;

			try{
				general_mon.enter();

				pieces = (int[][])getUserData( KEY_ALLOWED_FAST_SENT );

				if ( pieces == null ){

					List<Integer> l_pieces = generateFastSet( ALLOWED_FAST_PIECE_OFFERED_NUM );

					pieces = new int[l_pieces.size()][2];

					int	piece_size = diskManager.getPieceLength();

					for ( int i=0;i<l_pieces.size(); i++ ){

						int	piece_number = l_pieces.get(i);

						pieces[i] = new int[]{ piece_number, piece_size*2 };
					}

					setUserData( KEY_ALLOWED_FAST_SENT, pieces );
				}
			}finally{

				general_mon.exit();
			}

			for ( int i=0;i<pieces.length;i++){

				int	piece_number = pieces[i][0];

				if ( !flags.flags[ piece_number ] ){

					sendAllowFast( piece_number );
				}
			}
		}
	}

	private void
	checkAllowedFast()
	{
		try{
			general_mon.enter();

			if ( piecePicker.getNbPiecesDone() > ALLOWED_FAST_OTHER_PEER_PIECE_MAX ){

				List<Integer> pieces = (List<Integer>)getUserData( KEY_ALLOWED_FAST_RECEIVED );

				if ( pieces != null ){

					if ( DEBUG_FAST ){
						System.out.println( "Clearing down fast received for " + getIp());
					}

					setUserData( KEY_ALLOWED_FAST_RECEIVED, null );

					calculatePiecePriorities();
				}
			}

			BitFlags flags = peerHavePieces;

			if ( flags != null && flags.nbSet >= ALLOWED_FAST_OTHER_PEER_PIECE_MAX ){

				int[][] pieces = (int[][])getUserData( KEY_ALLOWED_FAST_SENT );

				if ( pieces != null ){

					if ( DEBUG_FAST ){
						System.out.println( "Clearing down fast sent for " + getIp());
					}

					setUserData( KEY_ALLOWED_FAST_SENT, null );
				}
			}
		}finally{

			general_mon.exit();
		}
	}

	private void registerForMessageHandling() {

		//INCOMING MESSAGES
		connection.getIncomingMessageQueue().registerQueueListener(
			new IncomingMessageQueue.MessageQueueListener()
			{
				@Override
				public final boolean
				messageReceived(
					Message message )
				{
					if( closing ) {
						message.destroy();
						return true;
					}

					if (Logger.isEnabled()){
						Logger.log(new LogEvent(PEPeerTransportProtocol.this, LogIDs.NET,
								"Received [" + message.getDescription() + "] message"));
					}
					
					final long now =SystemTime.getCurrentTime();
					last_message_received_time =now;
					if( message.getType() == Message.TYPE_DATA_PAYLOAD ) {
						last_data_message_received_time =now;
					}

					String 	featureID 	= message.getFeatureID();
					int		subID		= message.getFeatureSubID();
					
					if ( featureID == BTMessage.BT_FEATURE_ID ){
						switch( subID ){
							case BTMessage.SUBID_BT_HANDSHAKE:{
								decodeBTHandshake( (BTHandshake)message );
								return true;
							}

							case BTMessage.SUBID_BT_PIECE:{
								decodePiece( (BTPiece)message );
								return true;
							}
						
							case BTMessage.SUBID_BT_KEEP_ALIVE:{
								message.destroy();
	
								//make sure they're not spamming us
								if( !message_limiter.countIncomingMessage( message.getID(), 6, 60*1000 ) ) {  //allow max 6 keep-alives per 60sec
									System.out.println( manager.getDisplayName() + ": Incoming keep-alive message flood detected, dropping spamming peer connection." +PEPeerTransportProtocol.this );
									closeConnectionInternally( "Incoming keep-alive message flood detected, dropping spamming peer connection." );
								}
	
								return true;
							}

							case BTMessage.SUBID_BT_BITFIELD:{
								decodeBitfield( (BTBitfield)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_CHOKE:{
								decodeChoke( (BTChoke)message );
								if( choking_other_peer ) {
									connection.enableEnhancedMessageProcessing( false, manager.getPartitionID());  //downgrade back to normal handler
								}
								return true;
							}
	
							case BTMessage.SUBID_BT_UNCHOKE:{
								decodeUnchoke( (BTUnchoke)message );
								connection.enableEnhancedMessageProcessing( true, manager.getPartitionID() );  //make sure we use a fast handler for the resulting download
								return true;
							}
	
							case BTMessage.SUBID_BT_INTERESTED:{
								decodeInterested( (BTInterested)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_UNINTERESTED:{
								decodeUninterested( (BTUninterested)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_HAVE:{
								decodeHave( (BTHave)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_REQUEST:{
								decodeRequest( (BTRequest)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_CANCEL:{
								decodeCancel( (BTCancel)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_SUGGEST_PIECE:{
								decodeSuggestPiece( (BTSuggestPiece)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_HAVE_ALL:{
								decodeHaveAll( (BTHaveAll)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_HAVE_NONE:{
								decodeHaveNone( (BTHaveNone)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_REJECT_REQUEST:{
								decodeRejectRequest( (BTRejectRequest)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_ALLOWED_FAST:{
								decodeAllowedFast( (BTAllowedFast)message );
								return true;
							}
	
							case BTMessage.SUBID_BT_DHT_PORT:{
								decodeMainlineDHTPort((BTDHTPort)message);
								return true;
							}
							
							case BTMessage.SUBID_BT_HASH_REQUEST:{
								decodeHashRequest((BTHashRequest)message);
								return true;
							}
							
							case BTMessage.SUBID_BT_HASHES:{
								decodeHashes((BTHashes)message);
								return true;
							}
							
							case BTMessage.SUBID_BT_HASH_REJECT:{
								decodeHashReject((BTHashReject)message);
								return true;
							}
						}		
					}else if ( featureID == LTMessage.LT_FEATURE_ID ){
						
						switch( subID ){
							
							case LTMessage.SUBID_LT_HANDSHAKE:{
								decodeLTHandshake((LTHandshake)message);
								return true;
							}
	
							case LTMessage.SUBID_UT_PEX:{
								decodePeerExchange((UTPeerExchange)message);
								return true;
							}
	
							case LTMessage.SUBID_UT_METADATA:{
								decodeMetaData((UTMetaData)message);
								return true;
							}
	
							case LTMessage.SUBID_UT_UPLOAD_ONLY:{
								decodeUploadOnly((UTUploadOnly)message);
								return true;
							}						
						}
					}else if ( featureID == AZMessage.AZ_FEATURE_ID ){
						
						switch( subID ){
						
							case AZMessage.SUBID_AZ_HANDSHAKE:{
								decodeAZHandshake( (AZHandshake)message );
								return true;
							}
	
							case AZMessage.SUBID_AZ_METADATA:{
								decodeMetaData((AZMetaData)message );
								return true;
							}
	
							case AZMessage.SUBID_AZ_REQUEST_HINT:{
								decodeAZRequestHint( (AZRequestHint)message );
								return true;
							}
	
							case AZMessage.SUBID_AZ_HAVE:{
								decodeAZHave((AZHave)message );
								return true;
							}
	
							case AZMessage.SUBID_AZ_BAD_PIECE:{
								decodeAZBadPiece((AZBadPiece)message );
								return true;
							}
	
							case AZMessage.SUBID_AZ_STAT_REQUEST:{
								decodeAZStatsRequest((AZStatRequest)message );
								return true;
							}
	
							case AZMessage.SUBID_AZ_STAT_REPLY:{
								decodeAZStatsReply((AZStatReply)message );
								return true;
							}
							
							case AZMessage.SUBID_AZ_PEER_EXCHANGE:{
								decodePeerExchange( (AZPeerExchange)message );
								return true;
							}			
						}
					}
					
						// generic handling of az-style PEX (e.g. used by non-public net)

					if ( message instanceof AZStylePeerExchange ){
						decodePeerExchange((AZStylePeerExchange)message );
						return true;
					}
	
					return false;
				}

				@Override
				public final void protocolBytesReceived(int byte_count ) {
					//update stats
					peer_stats.protocolBytesReceived( byte_count );
					manager.protocolBytesReceived( PEPeerTransportProtocol.this, byte_count );
				}

				@Override
				public final void dataBytesReceived(int byte_count ) {
					// Observe that the peer is sending data so that if theyre so slow that the whole
					// data block times out, we don't think theyre not sending anything at all
					last_data_message_received_time =SystemTime.getCurrentTime();

					//update stats
					peer_stats.dataBytesReceived( byte_count );

					manager.dataBytesReceived( PEPeerTransportProtocol.this, byte_count );
				}

				@Override
				public boolean
				isPriority()
				{
					return( false );
				}
		});


		//OUTGOING MESSAGES
		connection.getOutgoingMessageQueue().registerQueueListener( new OutgoingMessageQueue.MessageQueueListener() {
			@Override
			public final boolean messageAdded(Message message ) {  return true;  }

			@Override
			public final void messageQueued(Message message ) { /* ignore */ }

			@Override
			public final void messageRemoved(Message message ) { /*ignore*/ }

			@Override
			public final void messageSent(Message message ) {
				//update keep-alive info
				final long now =SystemTime.getCurrentTime();
				last_message_sent_time =now;

				if( message.getType() == Message.TYPE_DATA_PAYLOAD ) {
					last_data_message_sent_time =now;
				}

				String message_id = message.getID();

				if( message_id.equals( BTMessage.ID_BT_UNCHOKE ) ) { // is about to send piece data

					connection.enableEnhancedMessageProcessing( true, manager.getPartitionID() );  //so make sure we use a fast handler

				}else if( message_id.equals( BTMessage.ID_BT_CHOKE ) ) { // is done sending piece data

					if( effectively_choked_by_other_peer ) {

						connection.enableEnhancedMessageProcessing( false, manager.getPartitionID() );  //so downgrade back to normal handler
					}
				}else if( message_id.equals( BTMessage.ID_BT_REQUEST )){

					BTRequest request = (BTRequest)message;

					DiskManagerReadRequest dm_request = lookupRequest( request.getPieceNumber(), request.getPieceOffset(), request.getLength());

					if ( dm_request != null ){

						dm_request.setTimeSent( SystemTime.getMonotonousTime());
					}
				}

				if (Logger.isEnabled())
					Logger.log(new LogEvent(PEPeerTransportProtocol.this, LogIDs.NET,
							"Sent [" + message.getDescription() + "] message"));
			}

			@Override
			public final void protocolBytesSent(int byte_count ) {
				//update stats
				peer_stats.protocolBytesSent( byte_count );
				manager.protocolBytesSent( PEPeerTransportProtocol.this, byte_count );
			}

			@Override
			public final void dataBytesSent(int byte_count ) {
				//update stats
				peer_stats.dataBytesSent( byte_count );
				manager.dataBytesSent( PEPeerTransportProtocol.this, byte_count );
			}
			 @Override
			 public void flush(){}
		});

		//start message processing

		connection.addRateLimiter( manager.getUploadLimitedRateGroup(), true );
		connection.addRateLimiter(  manager.getDownloadLimitedRateGroup(), false );

		connection.startMessageProcessing();
	}

	@Override
	public void
	addRateLimiter(
		LimitedRateGroup	limiter,
		boolean				upload )
	{
		connection.addRateLimiter( limiter, upload );
	}

	@Override
	public LimitedRateGroup[]
	getRateLimiters(boolean upload)
	{
		return( connection.getRateLimiters( upload ));
	}

	@Override
	public void
	removeRateLimiter(
		LimitedRateGroup	limiter,
		boolean				upload )
	{
		connection.removeRateLimiter( limiter, upload );
	}

	@Override
	public void
	setUploadDisabled(
		Object		key,
		boolean		disabled )
	{
		synchronized( this ){

			if ( upload_disabled_set == null ){

				if ( disabled ){

					upload_disabled_set = new HashSet<>();

					upload_disabled_set.add( key );

				}else{

					Debug.out( "derp" );
				}
			}else{

				if ( disabled ){

					if ( !upload_disabled_set.add( key )){

						Debug.out( "derp" );
					}

				}else{

					if ( !upload_disabled_set.remove( key )){

						Debug.out( "derp" );
					}

					if ( upload_disabled_set.size() == 0 ){

						upload_disabled_set = null;
					}
				}
			}

			is_upload_disabled = upload_disabled_set != null;

			//System.out.println( "setUploadDisabled " + getIp() + " -> " + (upload_disabled_set==null?0:upload_disabled_set.size()));
		}
	}

	@Override
	public void
	setDownloadDisabled(
		Object		key,
		boolean		disabled )
	{
		boolean	check = false;

		synchronized( this ){

			if ( download_disabled_set == null ){

				if ( disabled ){

					download_disabled_set = new HashSet<>();

					download_disabled_set.add( key );

				}else{

					Debug.out( "derp" );
				}
			}else{

				if ( disabled ){

					if ( !download_disabled_set.add( key )){

						Debug.out( "derp" );
					}

				}else{

					if ( !download_disabled_set.remove( key )){

						Debug.out( "derp" );
					}

					if ( download_disabled_set.size() == 0 ){

						download_disabled_set = null;
					}
				}
			}

			boolean	old = is_download_disabled;

			is_download_disabled = download_disabled_set != null;

			check = old != is_download_disabled;

			//System.out.println( "setDownloadDisabled " + getIp() + " -> " + (download_disabled_set==null?0:download_disabled_set.size()));
		}

		if ( check ){
			checkInterested();
		}
	}

	@Override
	public boolean
	isUploadDisabled()
	{
		return( is_upload_disabled );
	}

	public boolean
	isUploadDisabled(
		Object		key )
	{
		synchronized( this ){

			if ( upload_disabled_set == null ){

				return( false );
			}

			return( upload_disabled_set.contains( key ));
		}
	}

	@Override
	public boolean
	isDownloadDisabled()
	{
		return( is_download_disabled );
	}

	public boolean
	isDownloadDisabled(
		Object		key )
	{
		synchronized( this ){

			if ( download_disabled_set == null ){

				return( false );
			}

			return( download_disabled_set.contains( key ));
		}
	}

	@Override
	public NetworkConnectionBase
	getNetworkConnection()
	{
		return( connection );
	}
	
	@Override
	public Connection getPluginConnection() {
		return plugin_connection;
	}


	@Override
	public Message[] getSupportedMessages() {
		return supported_messages;
	}


	@Override
	public boolean supportsMessaging() {
		return supported_messages != null;
	}

	@Override
	public int getMessagingMode()
	{
		return messaging_mode;
	}

	@Override
	public byte[] getHandshakeReservedBytes() {
		return this.handshake_reserved_bytes;
	}

	@Override
	public void
	setHaveAggregationEnabled(
			boolean		enabled )
	{
		have_aggregation_disabled	= !enabled;
	}

	@Override
	public boolean
	hasReceivedBitField()
	{
		return( received_bitfield );
	}

	@Override
	public long
	getUnchokedForMillis()
	{
		long	time = effectively_unchoked_time;

		if ( effectively_choked_by_other_peer || time < 0 ){

			return( -1 );
		}

		return( SystemTime.getMonotonousTime() - time );
	}

	@Override
	public long
	getLatency()
	{
		return( request_latency );
	}

	@Override
	public String
	getEncryption()
	{
		Transport	transport = connection.getTransport();

		if ( transport == null ){

			return( "" );
		}

		return( transport.getEncryption( false ));
	}

	@Override
	public String
	getProtocol()
	{
		Transport	transport = connection.getTransport();

		String 	result;

		if ( transport == null ){

			result	= "";

		}else{

			result = transport.getProtocol();
		}

		return( result );
	}

	@Override
	public String
	getProtocolQualifier()
	{
		return((String)connection.getEndpoint().getProperty( AEProxyAddressMapper.MAP_PROPERTY_PROTOCOL_QUALIFIER ));
	}

	@Override
	public void
	addListener(
			PEPeerListener listener )
	{
		try{
			peer_listeners_mon.enter();

			if( peer_listeners_cow == null ){

				peer_listeners_cow = new ArrayList();
			}

			final List	new_listeners = new ArrayList( peer_listeners_cow );

			new_listeners.add( listener );

			peer_listeners_cow	= new_listeners;

		}finally{

			peer_listeners_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
			PEPeerListener listener )
	{
		try{
			peer_listeners_mon.enter();

			if ( peer_listeners_cow != null ){

				List	new_listeners = new ArrayList( peer_listeners_cow );

				new_listeners.remove( listener );

				if ( new_listeners.isEmpty()){

					new_listeners	= null;
				}

				peer_listeners_cow	= new_listeners;
			}
		}finally{

			peer_listeners_mon.exit();
		}
	}


	private void changePeerState( int new_state ) {
		current_peer_state = new_state;

		if ( current_peer_state == PEPeer.TRANSFERING ){
			
			manager.informFullyConnected( this );
			
			doPostHandshakeProcessing();
		}

		final List	peer_listeners_ref = peer_listeners_cow;

		if ( peer_listeners_ref != null ){

			for( int i=0; i < peer_listeners_ref.size(); i++ ) {

				final PEPeerListener l = (PEPeerListener)peer_listeners_ref.get( i );

				l.stateChanged(this, current_peer_state);
			}
		}
	}




	/**
	 * this method might be called repeatedly since LTEP allows headers to be sent more than once
	 * make sure that multiple invokations do not have unintended consequences
	 */
	private void doPostHandshakeProcessing() {
		//peer exchange registration
		if( manager.isPeerExchangeEnabled()) {
			//try and register all connections for their peer exchange info
		    PeerExchangerItem pex_item = peer_exchange_item;

			if(pex_item == null && canBePeerExchanged()){
				pex_item = peer_exchange_item = manager.createPeerExchangeConnection( this );
			}

			if ( pex_item != null ){

					//check for peer exchange support

				if ( ut_pex_enabled || peerSupportsMessageType(AZMessage.ID_AZ_PEER_EXCHANGE )){

					peer_exchange_supported = true;

					pex_item.enableStateMaintenance();

				}else{

					MessageStreamEncoder encoder = connection.getOutgoingMessageQueue().getEncoder();

					if ( 	encoder instanceof LTMessageEncoder &&
							((LTMessageEncoder)encoder).hasCustomExtensionHandler( LTMessageEncoder.CET_PEX )){

						peer_exchange_supported = true;

						pex_item.enableStateMaintenance();

					}else{

							//no need to maintain internal states as we wont be sending/receiving peer exchange messages

						pex_item.disableStateMaintenance();
					}
				}
			}
		}

		request_hint_supported 	= peerSupportsMessageType( AZMessage.ID_AZ_REQUEST_HINT );
		bad_piece_supported 	= peerSupportsMessageType( AZMessage.ID_AZ_BAD_PIECE );
		stats_request_supported = peerSupportsMessageType( AZMessage.ID_AZ_STAT_REQUEST );
		stats_reply_supported 	= peerSupportsMessageType( AZMessage.ID_AZ_STAT_REPLY );
		az_metadata_supported 	= peerSupportsMessageType( AZMessage.ID_AZ_METADATA );

		if ( is_metadata_download ){

			if ( az_metadata_supported ){

				int	mds = manager.getTorrentInfoDictSize();

				if ( mds > 0 ){

					spoofMDAvailability( mds );
				}
			}
		}
	}

	private boolean
	canBePeerExchanged()
	{
		if ( client_peer_id != null ){

				// disable the exchange of location targetted peers

			boolean ok = !client_peer_id.startsWith( PeerClassifier.CACHE_LOGIC );

			// System.out.println( "canPEX: " + client_peer_id + " -> " + ok );

			return( ok );

		}else{

			Debug.out( "No client peer id!" );

			return( false );
		}
	}

	private boolean peerSupportsMessageType( String message_id ) {
		if( supported_messages != null ) {
			for( int i=0; i < supported_messages.length; i++ ) {
				if( supported_messages[i].getID().equals( message_id ) )  return true;
			}
		}
		return false;
	}



	@Override
	public void updatePeerExchange() {
		if ( current_peer_state != TRANSFERING ) return;
		if( !peer_exchange_supported )  return;
	    PeerExchangerItem pex_item = peer_exchange_item;

		if( pex_item != null && manager.isPeerExchangeEnabled()) {

			if ( peer_item_identity.getNetwork() == AENetworkClassifier.AT_PUBLIC ){

				final PeerItem[] adds = pex_item.getNewlyAddedPeerConnections( AENetworkClassifier.AT_PUBLIC );
				final PeerItem[] drops = pex_item.getNewlyDroppedPeerConnections( AENetworkClassifier.AT_PUBLIC );

				if( (adds != null && adds.length > 0) || (drops != null && drops.length > 0) ) {
					if (ut_pex_enabled) {
						connection.getOutgoingMessageQueue().addMessage( new UTPeerExchange(adds, drops, null, (byte)0), false);
					}
					else {
						connection.getOutgoingMessageQueue().addMessage( new AZPeerExchange( manager.getTargetHash(), adds, drops, other_peer_pex_version ), false );
					}
				}
			}else{

				MessageStreamEncoder encoder = connection.getOutgoingMessageQueue().getEncoder();

				if ( encoder instanceof LTMessageEncoder ){

					((LTMessageEncoder)encoder).handleCustomExtension( LTMessageEncoder.CET_PEX, new Object[]{ pex_item });
				}
			}
		}
	}



	protected void decodePeerExchange( AZStylePeerExchange exchange ) {

			// if we're seeding ignore µT-PEXed seeds, Az won't send them in the first place

		PeerItem[] added = exchange instanceof UTPeerExchange ? ((UTPeerExchange)exchange).getAddedPeers(!( manager.isSeeding() || Constants.DOWNLOAD_SOURCES_PRETEND_COMPLETE )) : exchange.getAddedPeers();
		PeerItem[] dropped = exchange.getDroppedPeers();

		int	max_added	= exchange.getMaxAllowedPeersPerVolley(!has_received_initial_pex, true);
		int max_dropped = exchange.getMaxAllowedPeersPerVolley(!has_received_initial_pex, false);

		exchange.destroy();

			//make sure they're not spamming us

		if( !message_limiter.countIncomingMessage( exchange.getID(), 7, 120*1000 ) ) {  //allow max 7 PEX per 2min  //TODO reduce max after 2308 release?
			System.out.println( manager.getDisplayName() + ": Incoming PEX message flood detected, dropping spamming peer connection." +PEPeerTransportProtocol.this );
			closeConnectionInternally( "Incoming PEX message flood detected, dropping spamming peer connection." );
			return;
		}

		if(		( added != null   && added.length  > max_added ) ||
				( dropped != null && dropped.length > max_dropped )) {

				// log these too-large messages and ignore them - if the swarm really is this large then
				// we'll discover the peers soon anyway

    		if (Logger.isEnabled()){
    			Logger.log(
    				new LogEvent(this, LOGID,
    				"Invalid PEX message received: too large, ignoring this exchange. (added=" + (added==null?0:added.length) + ",dropped=" + (dropped==null?0:dropped.length) +")" ));
    		}

    		added 	= null;
    		dropped	= null;
		}

    	has_received_initial_pex = true;

    	PeerExchangerItem pex_item = peer_exchange_item;

		if( peer_exchange_supported && pex_item != null && manager.isPeerExchangeEnabled()){
			if( added != null ) {
				for( int i=0; i < added.length; i++ ) {
					PeerItem pi = added[i];
					manager.peerDiscovered( this, pi );
					pex_item.addConnectedPeer( pi );
				}
			}

			if( dropped != null ) {
				for( int i=0; i < dropped.length; i++ ) {
					pex_item.dropConnectedPeer( dropped[i] );
				}
			}
		}else{
			
			String reason;
			
			if ( !peer_exchange_supported ){
				reason = "Peer Exchange not supported by this peer";
			}else if ( pex_item == null ){
				reason = "Peer is not eligible for Peer Exchange (e.g. hiding listen port)";
			}else{
				reason = "Peer Exchange disabled for this download";
			}
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID, reason + ", " + "dropping received PEX message"));
		}
	}

	protected void
	decodeMetaData(
		AZUTMetaData metadata )
	{
		try{
			final int BLOCK_SIZE = 16*1024;

			int	type = metadata.getMessageType();

			if ( type == UTMetaData.MSG_TYPE_REQUEST ){

				if ( !manager.isPrivateTorrent()){

					int	piece = metadata.getPiece();

					int total_size = manager.getTorrentInfoDictSize();

					byte[] data = total_size<=0?null:manager.getAdapter().getTorrentInfoDict( this );

					UTMetaData	reply ;

					int	offset = piece*BLOCK_SIZE;

					if ( is_metadata_download || data == null || offset >= data.length ){

						reply = new UTMetaData( piece, null, 0, other_peer_bt_lt_ext_version );

					}else{

						int	to_send = Math.min( data.length - offset, BLOCK_SIZE );

						// System.out.println( "Sending ut_metadata: " + offset + "/" + to_send );

						reply = new UTMetaData( piece, ByteBuffer.wrap( data, offset, to_send ), total_size, other_peer_bt_lt_ext_version );
					}

					connection.getOutgoingMessageQueue().addMessage( reply, false );
				}
			}else if ( type == UTMetaData.MSG_TYPE_DATA ){

				int	piece_number 		= metadata.getPiece();
				DirectByteBuffer data 	= metadata.getMetadata();

				int	data_size 	= data.remaining( DirectByteBuffer.SS_PEER );

				int total_size	= manager.getTorrentInfoDictSize();

				int	piece_count 	= (total_size+BLOCK_SIZE-1)/BLOCK_SIZE;
				int	last_piece_size	= total_size%BLOCK_SIZE;

				if ( last_piece_size == 0 ){

					last_piece_size = BLOCK_SIZE;
				}

				boolean	good = false;

				if ( piece_number < piece_count ){

					int	expected_size = piece_number==piece_count-1?last_piece_size:BLOCK_SIZE;

					if ( data_size == expected_size ){

						DiskManagerReadRequest request = manager.createDiskManagerRequest( piece_number, 0, BLOCK_SIZE );

						if ( hasBeenRequested( request )){

							good = true;

							metadata.setMetadata( null );

							removeRequest( request );

							long now = SystemTime.getCurrentTime();

							resetRequestsTime( now );

							manager.writeBlock( piece_number, 0, data, this, false);

							if ( last_good_data_time !=-1 && now -last_good_data_time <= PEPeerControl.SNUB_MILLIS ){

								setSnubbed( false );
							}

							last_good_data_time = now;

							requests_completed++;
						}
					}
				}

				if ( ! good ){

					peer_stats.bytesDiscarded( data_size );

					manager.discarded( this, data_size );

					requests_discarded++;

					printRequestStats();

					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
								"metadata piece discarded as invalid."));

				}
			}else{

				int	piece = metadata.getPiece();

				final DiskManagerReadRequest request = manager.createDiskManagerRequest( piece, 0, 16*1024 );

				if ( hasBeenRequested( request )){

					removeRequest( request );

					manager.requestCanceled( request );
				}
			}
		}finally{

			metadata.destroy();
		}
	}

	protected void
	decodeUploadOnly(
		UTUploadOnly message )
	{
		try{
			boolean ulo = message.isUploadOnly();

			if ( ulo ){

				relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;

			}else{

				relativeSeeding &= ~RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;
			}
		}finally{

			message.destroy();
		}
	}

	@Override
	public boolean
	sendRequestHint(
			int		piece_number,
			int		offset,
			int		length,
			int		life )
	{
		if ( request_hint_supported ){

			AZRequestHint	rh = new AZRequestHint( piece_number, offset, length, life, other_peer_az_request_hint_version );

			connection.getOutgoingMessageQueue().addMessage( rh, false );

			return( true );

		}else{

			return( false );
		}
	}

	protected void
	decodeSuggestPiece(
		BTSuggestPiece	hint )
	{
		int	piece_number 	= hint.getPieceNumber();
		int	offset			= 0;
		int	length			= manager.getPieceLength( piece_number );

		hint.destroy();

		if ( manager.validateHintRequest( this, piece_number, offset, length )){

			if ( request_hint == null ){

				request_hint = new int[]{ piece_number, offset, length };
			}
		}
	}

	protected void
	decodeAZRequestHint(
		AZRequestHint	hint )
	{
		int	piece_number 	= hint.getPieceNumber();
		int	offset			= hint.getOffset();
		int	length			= hint.getLength();
		int	life			= hint.getLife();

		hint.destroy();

		if ( life > REQUEST_HINT_MAX_LIFE ){

			life = REQUEST_HINT_MAX_LIFE;
		}

		if ( manager.validateHintRequest( this, piece_number, offset, length )){

			if ( request_hint == null ){

				// we ignore life time currently as once hinted we don't accept another hint
				// until that one is satisfied. This is to prevent too many pieces starting

				request_hint = new int[]{ piece_number, offset, length };
			}
		}
	}

	@Override
	public int[]
	           getRequestHint()
	{
		return( request_hint );
	}

	@Override
	public void
	clearRequestHint()
	{
		request_hint = null;
	}

	@Override
	public PeerItem getPeerItemIdentity() {  return peer_item_identity;  }

	@Override
	public int[]
   	getReservedPieceNumbers()
   	{
   		return( reserved_pieces );
   	}

 	@Override
  public void
 	addReservedPieceNumber(int piece_number)
 	{
 		int[]	existing = reserved_pieces;

 		if ( existing == null ){

 			reserved_pieces = new int[]{ piece_number };

 		}else{

 			int[] updated = new int[existing.length+1];

 			System.arraycopy( existing, 0, updated, 0, existing.length );

 			updated[existing.length] = piece_number;

 			reserved_pieces = updated;
 		}
 	}

 	@Override
  public void
 	removeReservedPieceNumber(int piece_number)
 	{
 		int[]	existing = reserved_pieces;

 		if ( existing != null ){

 			if ( existing.length == 1 ){

 				if ( existing[0] == piece_number ){

 					reserved_pieces = null;
 				}
 			}else{

 				int[] updated = new int[existing.length-1];

 				int		pos 	= 0;
 				boolean	found 	= false;

 				for (int i=0;i<existing.length;i++){

 					int	pn = existing[i];

 					if ( found || pn != piece_number ){

 						if ( pos == updated.length ){

 							return;
 						}

 						updated[pos++] = pn;

 					}else{

 						found = true;
 					}
 				}

 				reserved_pieces = updated;
 			}
 		}
 	}

	@Override
	public int
	getIncomingRequestCount()
	{
		if ( outgoing_piece_message_handler == null ){
			return( 0 );
		}

		return outgoing_piece_message_handler.getRequestCount();
	}

	@Override
	public int
	getOutgoingRequestCount()
	{
		return( getNbRequests());
  }

	@Override
	public boolean
	isStalledPendingLoad()
	{
		if ( outgoing_piece_message_handler == null ){

			return( false );
		}

		return outgoing_piece_message_handler.isStalledPendingLoad();
	}

	@Override
	public int[] getIncomingRequestedPieceNumbers() {
		if ( outgoing_piece_message_handler == null ){
			return( new int[0]);
		}
		return outgoing_piece_message_handler.getRequestedPieceNumbers();
	}

	@Override
	public int 
	getIncomingRequestedPieceNumberCount()
	{
		if ( outgoing_piece_message_handler == null ){
			return( 0 );
		}
		return outgoing_piece_message_handler.getRequestedPieceNumberCount();
	}
	
	@Override
	public DiskManagerReadRequest[] 
	getRecentPiecesSent()
	{
		if ( outgoing_piece_message_handler == null ){

			return( new DiskManagerReadRequest[0] );
		}

		return( outgoing_piece_message_handler.getRecentMessages());
	}
	
	@Override
	public int[] 
	getOutgoingRequestedPieceNumbers() 
	{
		synchronized( requested ){

			/** Cheap hack to reduce (but not remove all) the # of duplicate entries */
			int iLastNumber = -1;

			// allocate max size needed (we'll shrink it later)
			final int[] pieceNumbers = new int[requested.size()];
			int pos = 0;

			for (int i = 0; i < requested.size(); i++) {
				DiskManagerReadRequest request = null;
				try {
					request = (DiskManagerReadRequest) requested.get(i);
				} catch (Exception e) {
					Debug.printStackTrace(e);
				}

				if (request != null && iLastNumber != request.getPieceNumber()) {
					iLastNumber = request.getPieceNumber();
					pieceNumbers[pos++] = iLastNumber;
				}
			}

			final int[] trimmed = new int[pos];
			System.arraycopy(pieceNumbers, 0, trimmed, 0, pos);

			return trimmed;
		}
	}

	@Override
	public int[]
	getCurrentIncomingRequestProgress()
	{
		return( connection.getIncomingMessageQueue().getCurrentMessageProgress());
	}

	@Override
	public int[]
	getCurrentOutgoingRequestProgress()
	{
		return( connection.getOutgoingMessageQueue().getCurrentMessageProgress());
	}

	@Override
	public long
	getLastMessageSentTime()
	{
		return( last_message_sent_time );
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#getLogRelationText()
	 */
	@Override
	public String getRelationText() {
		String text = "";
		if (manager instanceof LogRelation)
			text = ((LogRelation)manager).getRelationText() + "; ";
		text += "Peer: " + toString();
		return text;
	}


	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#queryForClass(java.lang.Class)
	 */
	@Override
	public Object[] getQueryableInterfaces() {
		return new Object[] { manager };
	}

	@Override
	public int getLastPiece()
	{
		return _lastPiece;
	}

	@Override
	public void setLastPiece(int pieceNumber)
	{
		_lastPiece =pieceNumber;
	}



	@Override
	public boolean isLANLocal() {
		if( connection == null )  return( AddressUtils.isLANLocalAddress( ip ) == AddressUtils.LAN_LOCAL_YES );
		return connection.isLANLocal();
	}
	
	@Override
	public void resetLANLocalStatus(){
		if ( connection != null ){
			connection.resetLANLocalStatus();
		}
	}

	@Override
	public boolean
	isTCP()
	{
		if ( connection == null ){
			return( false );
		}

		ProtocolEndpoint[] protocols = connection.getEndpoint().getProtocols();

			// not very good here - for most purposes we lump uTP with TCP as 'UDP' originally
			// meant the Vuze UDP protocol. The reconnect handling, for example, requires uTP to
			// appear to be TCP so for the moment lets make it so

		return( protocols[0].getType() != ProtocolEndpoint.PROTOCOL_UDP );
	}

	@Override
	public String
	getNetwork()
	{
		return( network );
	}

	@Override
	public void
	setUploadRateLimitBytesPerSecond(
		int 	bytes )
	{
		if ( bytes == -1 ){

			if ( !isUploadDisabled( PEPeerTransport.class )){

				setUploadDisabled( PEPeerTransport.class, true );

				connection.setUploadLimit( 0 );
			}
		}else{

			if ( is_upload_disabled ){

				if ( isUploadDisabled( PEPeerTransport.class )){

					setUploadDisabled( PEPeerTransport.class, false );
				}
			}

			connection.setUploadLimit( bytes );
		}
	}

	@Override
	public int
	getUploadRateLimitBytesPerSecond()
	{
		if ( is_upload_disabled ){

			if ( isUploadDisabled( PEPeerTransport.class )){

				return( -1 );
			}
		}

		return connection.getUploadLimit();
	}

	@Override
	public void
	setDownloadRateLimitBytesPerSecond(
		int 	bytes )
	{
		if ( bytes == -1 ){

			if ( !isDownloadDisabled( PEPeerTransport.class )){

				setDownloadDisabled( PEPeerTransport.class, true );

				connection.setDownloadLimit( 0 );
			}
		}else{

			if ( is_download_disabled ){

				if ( isDownloadDisabled( PEPeerTransport.class )){

					setDownloadDisabled( PEPeerTransport.class, false );
				}
			}

			connection.setDownloadLimit( bytes );
		}
	}

	@Override
	public int
	getDownloadRateLimitBytesPerSecond()
	{
		if ( is_download_disabled ){

			if ( isDownloadDisabled( PEPeerTransport.class )){

				return( -1 );
			}
		}

		return connection.getDownloadLimit();
	}

	@Override
	public String getClientNameFromPeerID() {return this.client_peer_id;}
	@Override
	public String getClientNameFromExtensionHandshake() {
		if (!this.client_handshake.equals("") && !this.client_handshake_version.equals("")) {
			return this.client_handshake + " " + this.client_handshake_version;
		}
		return this.client_handshake;
	}

	private static MainlineDHTProvider getDHTProvider() {
		return CoreImpl.getSingleton().getGlobalManager().getMainlineDHTProvider();
	}

	@Override
	public void
	setPriorityConnection(
		boolean		is_priority )
	{
		synchronized( this ){

			if ( priority_connection == is_priority ){

				return;
			}

			priority_connection = is_priority;
		}

		manager.getAdapter().priorityConnectionChanged( is_priority );
	}

	@Override
	public boolean
	isPriorityConnection()
	{
		synchronized( this ){

			return( priority_connection );
		}
	}

	protected List<Integer>
	generateFastSet(
		byte[]		hash,
		String		ip,
		int			num_pieces,
		int			num_required )
	{
		List<Integer>	res = new ArrayList<>();

		try{
			if ( network == AENetworkClassifier.AT_PUBLIC ){

				byte[]	address = InetAddress.getByName( ip ).getAddress();

					// no IPv6 support yet

				if ( address.length == 4 ){

					byte[]	bytes = new byte[24];

					System.arraycopy( address, 0, bytes, 0, 3 );
					System.arraycopy( hash, 0, bytes, 4, 20 );

					num_required = Math.min( num_required, num_pieces );

					while( res.size() < num_required ){

						bytes = new SHA1Simple().calculateHash( bytes );

						int	pos = 0;

						while( pos < 20 && res.size() < num_required ){

							long	index = (bytes[pos++] << 24 )&0xff000000L |
											(bytes[pos++] << 16 )&0x00ff0000L |
											(bytes[pos++] << 8  )&0x0000ff00L |
											bytes[pos++]&0x000000ffL;

							Integer i = new Integer((int)( index%num_pieces ));

							if ( !res.contains(i)){

								res.add( i );
							}
						}
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( "Fast set generation failed", e );
		}

		return( res );
	}

	protected List<Integer>
	generateFastSet(
		int		num )
	{
		return( generateFastSet( manager.getTargetHash(), getIp(), nbPieces, num ));
	}

	@Override
	public int getTaggableType() {return TT_PEER;}
   	@Override
    public String getTaggableID(){ return( null ); }
   	@Override
   	public String getTaggableName(){
   		return( getIp());
   	}
	@Override
	public TaggableResolver	getTaggableResolver(){ return( null ); }
	@Override
	public Object getTaggableTransientProperty(String key) {
		return null;
	}
	@Override
	public void setTaggableTransientProperty(String key, Object value) {
	}

	@Override
	public void
	generateEvidence(
			IndentWriter	writer )
	{
		writer.println(
				"ip=" + getIp() + ",in=" + isIncoming() + ",port=" + getPort() + ",cli=" + client + ",tcp=" + getTCPListenPort() + ",udp=" + getUDPListenPort() +
				",oudp=" + getUDPNonDataListenPort() + ",prot=" + getProtocol() + ",p_state=" + getPeerState() + ",c_state=" + getConnectionState() + ",seed=" + isSeed() + ",partialSeed=" + isRelativeSeed() + ",pex=" + peer_exchange_supported + ",closing=" + closing );
		writer.println( "    choked=" + effectively_choked_by_other_peer + "/" + really_choked_by_other_peer + ",choking=" + choking_other_peer + ",is_opt=" + is_optimistic_unchoke );
		writer.println( "    interested=" + interested_in_other_peer + ",interesting=" + other_peer_interested_in_me + ",snubbed=" + snubbed );
		writer.println( "    lp=" + _lastPiece + ",up=" + uniquePiece + ",rp=" + reserved_pieces );
		writer.println(
				"    last_sent=" + last_message_sent_time + "/" + last_data_message_sent_time +
				",last_recv=" + last_message_received_time + "/" + last_data_message_received_time + "/" + last_good_data_time );
		writer.println( "    conn_at=" + connection_established_time + ",cons_no_reqs=" + consecutive_no_request_count +
				",discard=" + requests_discarded + "/" + requests_discarded_endgame + ",recov=" + requests_recovered + ",comp=" + requests_completed + ",curr=" + requested.size());

	}

	protected static class
	MutableInteger
	{
		private int	value;

		protected
		MutableInteger(
			int		v )
		{
			value = v;
		}

		protected void
		setValue(
			int	v )
		{
			value = v;
		}

		protected int
		getValue()
		{
			return( value );
		}

		public int
		hashCode()
		{
			return value;
		}

		public boolean
		equals(
			Object obj )
		{
			if (obj instanceof MutableInteger) {
				return value == ((MutableInteger)obj).value;
			}
			return false;
		}
	}
}
