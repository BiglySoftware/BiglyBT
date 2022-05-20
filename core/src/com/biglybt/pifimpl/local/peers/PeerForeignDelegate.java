/*
 * File    : PeerForeignDelegate.java
 * Created : 22-Mar-2004
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

package com.biglybt.pifimpl.local.peers;

/**
 * @author parg
 * @author MjrTom
 *			2005/Oct/08: Add _lastPiece
 *
 */

import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.NetworkConnectionBase;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.peer.*;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.peermanager.peerdb.PeerItemFactory;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.tag.TaggableResolver;
import com.biglybt.core.torrent.TOTorrentFileHashTree.HashRequest;
import com.biglybt.core.util.*;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.network.ConnectionStub;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.peers.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pifimpl.local.messaging.MessageAdapter;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl.PluginLimitedRateGroup;

public class
PeerForeignDelegate
	implements 	PEPeerTransport
{
		// this implementation supports read-only peers (i.e. download only)

	protected volatile int		_lastPiece =-1;

	private final PeerManagerImpl		manager;
	private final Peer					foreign;

	private NetworkConnectionBase	network_connection;

	private long	create_time		= SystemTime.getCurrentTime();
	private long	last_data_received_time =-1;
	private long	last_data_message_received_time =-1;
	private int[]	reserved_pieces	= null;
	private int		consecutive_no_requests;

	private BitFlags	bit_flags;

	private boolean		priority_connection;

	private Map			data;

	private HashMap<PEPeerListener, PeerListener2> peer_listeners;

	protected AEMonitor	this_mon	= new AEMonitor( "PeerForeignDelegate" );

	private Set<Object>	download_disabled_set;

	private boolean	is_download_disabled;

	private volatile boolean	closed;

	protected
	PeerForeignDelegate(
		PeerManagerImpl		_manager,
		Peer				_foreign )
	{
		manager		= _manager;
		foreign		= _foreign;

		PEPeerManager pm = manager.getDelegate();

		network_connection = new PeerForeignNetworkConnection( this, foreign );

		network_connection.addRateLimiter( pm.getUploadLimitedRateGroup(), true );
		network_connection.addRateLimiter( pm.getDownloadLimitedRateGroup(), false );

		_foreign.bindConnection(
			new ConnectionStub()
			{
				  @Override
				  public void
				  addRateLimiter(
					  RateLimiter		limiter,
					  boolean			is_upload )
				  {
					  network_connection.addRateLimiter( UtilitiesImpl.wrapLimiter( limiter, false ), is_upload );
				  }

				  @Override
				  public void
				  removeRateLimiter(
					  RateLimiter		limiter,
					  boolean			is_upload )
				  {
					  network_connection.removeRateLimiter( UtilitiesImpl.wrapLimiter( limiter, false ), is_upload );
				  }

				  @Override
				  public RateLimiter[]
				  getRateLimiters(
					 boolean			is_upload )
				  {
						LimitedRateGroup[] limiters = network_connection.getRateLimiters( is_upload );

						RateLimiter[]	result = new RateLimiter[limiters.length];

						int	pos = 0;

						for ( LimitedRateGroup l: limiters ){

							if ( l instanceof PluginLimitedRateGroup  ){

								result[pos++] = UtilitiesImpl.unwrapLmiter((PluginLimitedRateGroup)l);
							}
						}

						if ( pos == result.length ){

							return( result );
						}

						RateLimiter[]	result_mod = new RateLimiter[pos];

						System.arraycopy( result, 0, result_mod, 0, pos );

						return( result_mod );
				  }
			});
	}

	@Override
	public boolean 
	isMyPeer()
	{
		return( false );
	}
	
	@Override
	public int 
	getOutboundConnectionProgress()
	{
		return( CP_UNKNOWN );
	}
	
	@Override
	public void
	start()
	{
		NetworkManager.getSingleton().startTransferProcessing( network_connection );

		NetworkManager.getSingleton().upgradeTransferProcessing( network_connection, manager.getPartitionID());
	}

	protected void
	stop()
	{
		NetworkManager.getSingleton().stopTransferProcessing( network_connection );
	}

    /**
     * Should never be called
     */
    @Override
    public void sendChoke() {}

    /**
     * Nothing to do if called
     */
    @Override
    public void sendHave(int piece) {}

    /**
     * Should never be called
     */
    @Override
    public void sendUnChoke() {}

    @Override
    public InetAddress getAlternativeIPv6() {  return null; }



    @Override
    public boolean
    transferAvailable()
    {
    	return( foreign.isTransferAvailable());
    }

    @Override
    public boolean isDownloadPossible()
    {
    	if ( is_download_disabled ){

    		return( false );
    	}

    	return foreign.isDownloadPossible();
    }

	@Override
	public void
	sendCancel(
		DiskManagerReadRequest	request )
	{
		foreign.cancelRequest( request );
	}

  /**
   *
   * @param pieceNumber
   * @param pieceOffset
   * @param pieceLength
   * @return true is the piece is really requested
   */
	@Override
	public DiskManagerReadRequest
	request(
		int 	pieceNumber,
		int 	pieceOffset,
		int 	pieceLength,
		boolean	return_duplicates )
	{
		DiskManagerReadRequest	request = manager.getDelegate().getDiskManager().createReadRequest( pieceNumber, pieceOffset, pieceLength );

		if ( foreign.addRequest( request )){

			return( request );

		}else{

			return( null );
		}
	}

	@Override
	public int
	getRequestIndex(
		DiskManagerReadRequest request )
	{
		return( foreign.getRequests().indexOf( request ));
	}
	
	protected  void
	dataReceived()
	{
		last_data_received_time	= SystemTime.getCurrentTime();
	}

	@Override
	public void
	closeConnection(
		String reason )
	{
		closed = true;

		try{
			foreign.close( reason, false, false );

		}finally{

			stop();
		}
	}

	@Override
	public boolean
	isClosed()
	{
		return( closed );

	}
	@Override
	public List
	getExpiredRequests()
	{
		return( foreign.getExpiredRequests());
	}

	@Override
	public long
	getLastMessageSentTime()
	{
		return( 0 );
	}

	@Override
	public int
	getMaxNbRequests()
	{
		return( foreign.getMaximumNumberOfRequests());
	}
	@Override
	public int
	getNbRequests()
	{
		return( foreign.getNumberOfRequests());
	}

	@Override
	public int[]
	getPriorityOffsets()
	{
		return( foreign.getPriorityOffsets());
	}

	@Override
	public boolean
	requestAllocationStarts(
		int[]	base_priorities )
	{
		return( foreign.requestAllocationStarts( base_priorities ));
	}

	@Override
	public void
	requestAllocationComplete()
	{
		foreign.requestAllocationComplete();
	}

	@Override
	public PEPeerControl
	getControl()
	{
		return((PEPeerControl)manager.getDelegate());
	}


	@Override
	public void
	updatePeerExchange()
	{
	}


	@Override
	public PeerItem
	getPeerItemIdentity()
	{
		return PeerItemFactory.createPeerItem(
				foreign.getIp(),
				foreign.getTCPListenPort(),
				PeerItemFactory.PEER_SOURCE_PLUGIN,
				PeerItemFactory.HANDSHAKE_TYPE_PLAIN,
				foreign.getUDPListenPort(),
				PeerItemFactory.CRYPTO_LEVEL_1,
				0 );
	}


	@Override
	public int
	getConnectionState()
	{
		int	peer_state = getPeerState();

		if ( peer_state == Peer.CONNECTING ){

			return( CONNECTION_CONNECTING );

		}else if ( peer_state == Peer.HANDSHAKING ){

			return( CONNECTION_WAITING_FOR_HANDSHAKE );

		}else if ( peer_state == Peer.TRANSFERING ){

			return( CONNECTION_FULLY_ESTABLISHED );

		}else{

			return( CONNECTION_FULLY_ESTABLISHED );
		}
	}

  	@Override
	  public void
  	doKeepAliveCheck()
  	{
  	}

  	@Override
	  public boolean
  	doTimeoutChecks()
  	{
  		return false;
  	}

  	@Override
	  public void
  	doPerformanceTuningCheck()
  	{
  	}

    @Override
    public void
    setSuspendedLazyBitFieldEnabled(
  		boolean	enable )
    {
    }

  	@Override
	  public long
  	getTimeSinceConnectionEstablished()
  	{
  		long	now = SystemTime.getCurrentTime();

  		if ( now > create_time ){

  			return( now - create_time );
  		}

  		return( 0 );
  	}

    @Override
    public long getTimeSinceLastDataMessageReceived() {
        if (last_data_message_received_time ==-1)
          return -1;	//never received

        final long now =SystemTime.getCurrentTime();
        if (last_data_message_received_time <now)
            last_data_message_received_time =now;   //time went backwards
        return now -last_data_message_received_time;
      }

	@Override
	public long getTimeSinceGoodDataReceived()
	{
		if (last_data_received_time ==-1)
			return -1;	// never received
		long now =SystemTime.getCurrentTime();
		long time_since =now -last_data_received_time;

		if (time_since <0)
		{	// time went backwards
			last_data_received_time =now;
			time_since =0;
		}

		return time_since;
	}

  	@Override
	  public long
  	getTimeSinceLastDataMessageSent()
  	{
  		return 0;
  	}

  	@Override
	  public long
  	getUnchokedForMillis()
  	{
  		return( 0 );
  	}

  	@Override
	  public long getLatency() {
  		return 0;
  	}

  	@Override
	  public int
  	getConsecutiveNoRequestCount()
  	{
  		return( consecutive_no_requests );
  	}

  	@Override
	  public void
  	setConsecutiveNoRequestCount(
  			int num )
  	{
  		consecutive_no_requests = num;
  	}

		// PEPeer stuff

	@Override
	public PEPeerManager
	getManager()
	{
		return( manager.getDelegate());
	}

	@Override
	public String
	getPeerSource()
	{
		return( PEPeerSource.PS_PLUGIN );
	}

	@Override
	public int
	getPeerState()
	{
		int	peer_state = foreign.getState();

		return( peer_state );
	}




	@Override
	public byte[]
	getId()
	{
		return( foreign.getId());
	}


	@Override
	public String
	getIp()
	{
		return( foreign.getIp());
	}

	@Override
	public String
	getIPHostName()
	{
		return( foreign.getIp());
	}

	@Override
	public int
	getPort()
	{
		return( foreign.getPort());
	}


	@Override
	public int getTCPListenPort() {  return foreign.getTCPListenPort();  }
	@Override
	public int getUDPListenPort() {  return foreign.getUDPListenPort();  }
	@Override
	public int getUDPNonDataListenPort() { return( foreign.getUDPNonDataListenPort()); }




	@Override
	public BitFlags
	getAvailable()
	{
		boolean[]	flags = foreign.getAvailable();

		if ( flags != null ){

			if ( bit_flags == null || bit_flags.flags != flags ){

				bit_flags = new BitFlags( flags );
			}
		}

		return( bit_flags );
	}

	@Override
	public boolean
	hasReceivedBitField()
	{
		return( true );
	}

	@Override
	public boolean isPieceAvailable(int pieceNumber)
	{
		return foreign.isPieceAvailable(pieceNumber);
	}

	@Override
	public void
	setSnubbed(boolean b)
	{
		foreign.setSnubbed( b );
	}


	@Override
	public boolean
	isChokingMe()
	{
		if ( is_download_disabled ){

			return( true );
		}

		return( foreign.isChoked());
	}


	@Override
	public boolean
	isChokedByMe()
	{
		return( foreign.isChoking());
	}

	@Override
	public boolean
	isUnchokeOverride()
	{
		return( false );
	}

	@Override
	public boolean
	isInteresting()
	{
		if ( is_download_disabled ){

			return( false );
		}

		return( foreign.isInteresting());
	}


	@Override
	public boolean
	isInterested()
	{
		return( foreign.isInterested());
	}


	@Override
	public boolean
	isSeed()
	{
		return( foreign.isSeed());
	}

	@Override
	public boolean
	isRelativeSeed()
	{
		return( false );
	}


	@Override
	public boolean
	isSnubbed()
	{
		return( foreign.isSnubbed());
	}

	@Override
	public long getSnubbedTime()
	{
		return foreign.getSnubbedTime();
	}


	@Override
	public boolean isLANLocal() {
		return( AddressUtils.isLANLocalAddress( foreign.getIp()) == AddressUtils.LAN_LOCAL_YES );
	}

	@Override
	public void resetLANLocalStatus(){
	}
	
	@Override
	public boolean
	sendRequestHint(
		int		piece_number,
		int		offset,
		int		length,
		int		life )
	{
		return( false );
	}

	@Override
	public int[]
	getRequestHint()
	{
		return null;
	}

	@Override
	public void
	clearRequestHint()
	{
	}

	@Override
	public void
	sendRejectRequest(
		DiskManagerReadRequest request)
	{
	}

	@Override
	public void
	sendBadPiece(
		int piece_number)
	{
	}

	@Override
	public void
	sendStatsRequest(
		Map		request )
	{
	}

	@Override
	public void
	sendStatsReply(
		Map		reply )
	{
	}

	@Override
	public void 
	sendHashRequest(
		HashRequest req)
	{
	}
	
	@Override
	public boolean
	isTCP()
	{
		return( true );
	}

	@Override
	public String
	getNetwork()
	{
		return( AENetworkClassifier.categoriseAddress( getIp()));
	}

	@Override
	public PEPeerStats
	getStats()
	{
		PeerStatsImpl ps = (PeerStatsImpl)foreign.getStats();
		
		if ( ps == null ){
			
			return( null );
		}
		
		return( ps.getDelegate());
	}


	@Override
	public boolean
	isIncoming()
	{
		return( foreign.isIncoming());
	}

	@Override
	public int
	getPercentDoneInThousandNotation()
	{
		return foreign.getPercentDoneInThousandNotation();
	}

	@Override
	public long
	getBytesRemaining()
	{
		int	rem_pm = 1000 - getPercentDoneInThousandNotation();

		if ( rem_pm == 0 ){

			return( 0 );
		}

		try{
			Torrent t = manager.getDownload().getTorrent();

			if ( t == null ){

				return( Long.MAX_VALUE );
			}

			return(( t.getSize() * rem_pm ) / 1000 );

		}catch( Throwable e ){

			return( Long.MAX_VALUE );
		}
	}

	@Override
	public String
	getClient()
	{
		return( foreign.getClient());
	}

	@Override
	public byte[]
	getHandshakeReservedBytes()
	{
		return foreign.getHandshakeReservedBytes();
	}

	@Override
	public boolean
	isOptimisticUnchoke()
	{
		return( foreign.isOptimisticUnchoke());
	}

  @Override
  public void setOptimisticUnchoke(boolean is_optimistic ) {
    foreign.setOptimisticUnchoke( is_optimistic );
  }

	@Override
	public int getUniqueAnnounce()
	{
	    return -1;
	}

	@Override
	public int getUploadHint()
	{
	    return 0;
	}


	@Override
	public void setUniqueAnnounce(int uniquePieceNumber) {}

	@Override
	public void setUploadHint(int timeToSpread) {}

	@Override
	public boolean isStalledPendingLoad(){return( false );}

	@Override
	public void addListener(final PEPeerListener l )
	{
		final PEPeer self =this;
		// add a listener to the foreign, then call our listeners when it calls us
		PeerListener2 core_listener =
			new PeerListener2()
			{
				@Override
				public void
				eventOccurred(
					PeerEvent	event )
				{
					Object	data = event.getData();

					switch( event.getType() ){
						case PeerEvent.ET_STATE_CHANGED:{
							l.stateChanged(self, ((Integer)data).intValue());
							break;
						}
						case PeerEvent.ET_BAD_CHUNK:{
							Integer[] d = (Integer[])data;
							l.sentBadChunk(self, d[0].intValue(), d[1].intValue() );
							break;
						}
						case PeerEvent.ET_ADD_AVAILABILITY:{
							l.addAvailability(self, new BitFlags((boolean[])data));
							break;
						}
						case PeerEvent.ET_REMOVE_AVAILABILITY:{
							l.removeAvailability(self, new BitFlags((boolean[])data));
							break;
						}
					}
				}
			};

			foreign.addListener( core_listener );

		if( peer_listeners == null ){

			peer_listeners = new HashMap<>();
		}

		peer_listeners.put( l, core_listener );

	}

	@Override
	public void removeListener(PEPeerListener l )
	{
		if ( peer_listeners != null ){

			PeerListener2 core_listener = peer_listeners.remove(l);

			if ( core_listener != null ){

					foreign.removeListener(core_listener);
			}
		}
	}

	@Override
	public NetworkConnectionBase
	getNetworkConnection()
	{
		return( network_connection );
	}
	
	@Override
	public Connection
	getPluginConnection()
	{
		return( foreign.getConnection());
	}

	@Override
	public int[]
	getCurrentIncomingRequestProgress()
	{
		return( foreign.getCurrentIncomingRequestProgress());
	}

	@Override
	public int[]
	getCurrentOutgoingRequestProgress()
	{
		return( foreign.getCurrentOutgoingRequestProgress());
	}

	@Override
	public boolean
	supportsMessaging()
	{
		return foreign.supportsMessaging();
	}

	@Override
	public int getMessagingMode()
	{
		return PEPeer.MESSAGING_EXTERN;
	}

	@Override
	public String
	getEncryption()
	{
		return( "" );
	}

	@Override
	public String
	getProtocol()
	{
		String res = (String)foreign.getUserData( Peer.PR_PROTOCOL );

		if ( res != null ){

			return( res );
		}

		return( "Plugin" );
	}

	@Override
	public String
	getProtocolQualifier()
	{
		return((String)foreign.getUserData( Peer.PR_PROTOCOL_QUALIFIER ));
	}

	@Override
	public Message[]
	getSupportedMessages()
	{
		com.biglybt.pif.messaging.Message[] plug_msgs = foreign.getSupportedMessages();

		Message[] core_msgs = new Message[ plug_msgs.length ];

		for( int i=0; i < plug_msgs.length; i++ ) {
			core_msgs[i] = new MessageAdapter( plug_msgs[i] );
		}

		return core_msgs;
	}

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
			this_mon.enter();
			if (data == null) return null;
			return data.get(key);
		}finally{

			this_mon.exit();
		}
	}

	/** To store arbitrary objects against a peer. */
	@Override
	public void setUserData (Object key, Object value) {
		try{
			this_mon.enter();

			if (data == null) {
				data = new LightHashMap();
			}
			if (value == null) {
				if (data.containsKey(key)){
					data.remove(key);
					if ( data.size() == 0 ){
						data = null;
					}
				}
			} else {
				data.put(key, value);
			}
		}finally{

			this_mon.exit();
		}
	}

	public boolean
	equals(
		Object 	other )
	{
		if ( other instanceof PeerForeignDelegate ){

			return( foreign.equals(((PeerForeignDelegate)other).foreign ));
		}

		return( false );
	}

	public int
	hashCode()
	{
		return( foreign.hashCode());
	}



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
	public int[]
	getIncomingRequestedPieceNumbers()
	{
		return( new int[0] );
	}

	@Override
	public int 
	getIncomingRequestedPieceNumberCount()
	{
		return( 0 );
	}
	
	@Override
	public int
	getIncomingRequestCount()
	{
		return( 0 );
	}

	@Override
	public int getOutgoingRequestCount()
	{
		return( foreign.getOutgoingRequestCount());
	}

	@Override
	public int[]
	getOutgoingRequestedPieceNumbers()
	{
		return( foreign.getOutgoingRequestedPieceNumbers());
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

    /**
     * Nothing to do if called
     */
    @Override
    public void checkInterested() {}

    /**
     * Apaprently nothing significant to do if called
     */
	public boolean isAvailabilityAdded() {return false;}

    /**
     * Nothing to do if called
     */
	public void clearAvailabilityAdded() {}

	@Override
	public PEPeerTransport reconnect(boolean tryUDP, boolean tryIPv6){ return null; }
	@Override
	public boolean isSafeForReconnect() { return false; }

	@Override
	public void setUploadRateLimitBytesPerSecond(int bytes ){ network_connection.setUploadLimit( bytes ); }
	@Override
	public void setDownloadRateLimitBytesPerSecond(int bytes ){ network_connection.setDownloadLimit( bytes ); }
	@Override
	public int getUploadRateLimitBytesPerSecond(){ return network_connection.getUploadLimit(); }
	@Override
	public int getDownloadRateLimitBytesPerSecond(){ return network_connection.getDownloadLimit(); }

	@Override
	public void
	updateAutoUploadPriority(
		Object		key,
		boolean		inc )
	{
	}

	@Override
	public void
	addRateLimiter(
		LimitedRateGroup	limiter,
		boolean				upload )
	{
		network_connection.addRateLimiter( limiter, upload );
	}

	@Override
	public LimitedRateGroup[]
	getRateLimiters(
		boolean	upload )
	{
		return( network_connection.getRateLimiters( upload ));
	}

	@Override
	public void
	removeRateLimiter(
		LimitedRateGroup	limiter,
		boolean				upload )
	{
		network_connection.removeRateLimiter( limiter, upload );
	}

	@Override
	public void
	setUploadDisabled(
		Object		key,
		boolean		disabled )
	{
		// nothing to do here as we only support download here
	}

	@Override
	public void
	setDownloadDisabled(
		Object		key,
		boolean		disabled )
	{
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

			is_download_disabled = download_disabled_set != null;

			if ( is_download_disabled ){

				List<PeerReadRequest>	list = foreign.getRequests();

				if ( list != null ){

					for ( PeerReadRequest req: list ){

						foreign.cancelRequest( req );
					}
				}
			}
			//System.out.println( "setDownloadDisabled " + getIp() + " -> " + (download_disabled_set==null?0:download_disabled_set.size()));
		}
	}

	@Override
	public boolean
	isUploadDisabled()
	{
		return( true );
	}

	@Override
	public boolean
	isDownloadDisabled()
	{
		return( is_download_disabled );
	}

	@Override
	public void
	setHaveAggregationEnabled(
		boolean		enabled )
	{
	}

	@Override
	public void
	setPriorityConnection(
		boolean		is_priority )
	{
		priority_connection = is_priority;
	}

	@Override
	public boolean
	isPriorityConnection()
	{
		return( priority_connection );
	}

	@Override
	public void
	generateEvidence(
		IndentWriter	writer )
	{
		writer.println( "delegate: ip=" + getIp() + ",tcp=" + getTCPListenPort()+",udp="+getUDPListenPort()+",state=" + foreign.getState()+",foreign=" + foreign );
	}

	@Override
	public String getClientNameFromExtensionHandshake() {return null;}
	@Override
	public String getClientNameFromPeerID() {return null;}

	@Override
	public int getTaggableType() {return TT_PEER;}
   	@Override
    public String getTaggableID(){ return( null ); }
	@Override
	public String getTaggableName(){
		return(getIp());
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
}
