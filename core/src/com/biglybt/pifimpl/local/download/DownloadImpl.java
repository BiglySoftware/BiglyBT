/*
 * File    : DownloadImpl.java
 * Created : 06-Jan-2004
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

package com.biglybt.pifimpl.local.download;

/**
 * @author parg
 *
 */

import java.io.File;
import java.net.URL;
import java.util.*;

import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.download.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;
import com.biglybt.core.download.impl.DownloadManagerMoveHandler;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerDownloadRemovalVetoException;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.peermanager.messaging.bittorrent.BTHandshake;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.TrackerPeerSourceAdapter;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.*;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.disk.DiskManager;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.*;
import com.biglybt.pif.download.savelocation.SaveLocationChange;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.tag.Tag;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.ddb.DDBaseImpl;
import com.biglybt.pifimpl.local.disk.DiskManagerFileInfoImpl;
import com.biglybt.pifimpl.local.peers.PeerManagerImpl;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;

public class
DownloadImpl
	extends LogRelation
	implements 	Download, DownloadManagerListener,
				DownloadManagerTrackerListener,
				 DownloadManagerActivationListener,
				DownloadManagerStateAttributeListener
{
	private final DownloadManagerImpl		manager;
	private final DownloadManager			download_manager;
	private final DownloadStatsImpl			download_stats;

	private int			latest_state		= ST_STOPPED;
	private boolean 	latest_forcedStart;

	private final DownloadAnnounceResultImpl	last_announce_result 	= new DownloadAnnounceResultImpl(this,null);
	private final DownloadScrapeResultImpl		last_scrape_result		= new DownloadScrapeResultImpl( this, null );
	private final AggregateScrapeResult			aggregate_scrape;

    private TorrentImpl torrent = null;

	private List		listeners 				= new ArrayList();
	private AEMonitor	listeners_mon			= new AEMonitor( "Download:L");
	private List		tracker_listeners		= new ArrayList();
	private AEMonitor	tracker_listeners_mon	= new AEMonitor( "Download:TL");
	private List		removal_listeners 		= new ArrayList();
	private AEMonitor	removal_listeners_mon	= new AEMonitor( "Download:RL");
	private Map			peer_listeners			= new HashMap();
	private AEMonitor	peer_listeners_mon		= new AEMonitor( "Download:PL");

	private CopyOnWriteList completion_listeners     = new CopyOnWriteList();

	private CopyOnWriteMap read_attribute_listeners_map_cow  = new CopyOnWriteMap();
	private CopyOnWriteMap write_attribute_listeners_map_cow = new CopyOnWriteMap();

	private CopyOnWriteList	activation_listeners = new CopyOnWriteList();
	private DownloadActivationEvent	activation_state;


	private Map<String,int[]>	announce_response_map;

	protected
	DownloadImpl(
		DownloadManagerImpl	_manager,
		DownloadManager		_dm )
	{
		manager				= _manager;
		download_manager	= _dm;
		download_stats		= new DownloadStatsImpl( download_manager );

		aggregate_scrape		= new AggregateScrapeResult( this, download_manager );

		activation_state =
			new DownloadActivationEvent()
			{
				@Override
				public Download
				getDownload()
				{
					return( DownloadImpl.this );
				}

				@Override
				public int
				getActivationCount()
				{
					return( download_manager.getActivationCount());
				}
			};

		download_manager.addListener( this );

		latest_forcedStart = download_manager.isForceStart();
	}

	// Not available to plugins
	public DownloadManager
	getDownload()
	{
		return( download_manager );
	}

	@Override
	public int
	getState()
	{
		return( convertState( download_manager.getState()) );
	}

	@Override
	public int
	getSubState()
	{
		int	state = getState();

		if ( state == ST_STOPPING ){

			int	substate = download_manager.getSubState();

			if ( substate == DownloadManager.STATE_QUEUED ){

				return( ST_QUEUED );

			}else if ( substate == DownloadManager.STATE_STOPPED ){

				return( ST_STOPPED );

			}else if ( substate == DownloadManager.STATE_ERROR ){

				return( ST_ERROR );
			}
		}

		return( state );
	}

	protected int
	convertState(
		int		dm_state )
	{
		// dm states: waiting -> initialising -> initialized ->
		//		disk states: allocating -> checking -> ready ->
		// dm states: downloading -> finishing -> seeding -> stopping -> stopped

		// "initialize" call takes from waiting -> initialising -> waiting (no port) or initialized (ok)
		// if initialized then disk manager runs through to ready
		// "startdownload" takes ready -> dl etc.
		// "stopIt" takes to stopped which is equiv to ready

		int	our_state;

		switch( dm_state ){
			case DownloadManager.STATE_WAITING:
			{
				our_state	= ST_WAITING;

				break;
			}
			case DownloadManager.STATE_INITIALIZING:
			case DownloadManager.STATE_INITIALIZED:
			case DownloadManager.STATE_ALLOCATING:
			case DownloadManager.STATE_CHECKING:
			{
				our_state	= ST_PREPARING;

				break;
			}
			case DownloadManager.STATE_READY:
			{
				our_state	= ST_READY;

				break;
			}
			case DownloadManager.STATE_DOWNLOADING:
			case DownloadManager.STATE_FINISHING:		// finishing download - transit to seeding
			{
				our_state	= ST_DOWNLOADING;

				break;
			}
			case DownloadManager.STATE_SEEDING:
			{
				our_state	= ST_SEEDING;

				break;
			}
			case DownloadManager.STATE_STOPPING:
			{
				our_state	= ST_STOPPING;

				break;
			}
			case DownloadManager.STATE_STOPPED:
			{
				our_state	= ST_STOPPED;

				break;
			}
			case DownloadManager.STATE_QUEUED:
			{
				our_state	= ST_QUEUED;

				break;
			}
			case DownloadManager.STATE_ERROR:
			{
				our_state	= ST_ERROR;

				break;
			}
			default:
			{
				our_state	= ST_ERROR;
			}
		}

		return( our_state );
	}

	@Override
	public String
	getErrorStateDetails()
	{
		return( download_manager.getErrorDetails());
	}

	@Override
	public long
	getFlags()
	{
		return( download_manager.getDownloadState().getFlags());
	}

	@Override
	public boolean
	getFlag(
		long		flag )
	{
		return( download_manager.getDownloadState().getFlag( flag ));
	}

	@Override
	public void setFlag(long flag, boolean set) {
		download_manager.getDownloadState().setFlag(flag, set);
	}

	@Override
    public Torrent
    getTorrent()
    {
    	if (this.torrent != null) {return this.torrent;}

        TOTorrent torrent = download_manager.getTorrent();
        if (torrent == null) {return null;}
        this.torrent = new TorrentImpl(torrent);
        return this.torrent;
    }

	@Override
	public void
	initialize()

		throws DownloadException
	{
		int	state = download_manager.getState();

		if ( state == DownloadManager.STATE_WAITING ){

			download_manager.initialize();

		}else{

			throw( new DownloadException( "Download::initialize: download not waiting (state=" + state + ")" ));
		}
	}

	@Override
	public void
	start()

		throws DownloadException
	{
		int	state = download_manager.getState();

		if ( state == DownloadManager.STATE_READY ){

			download_manager.startDownload();

		}else{

			throw( new DownloadException( "Download::start: download not ready (state=" + state + ")" ));
		}
	}

	@Override
	public void
	restart()

		throws DownloadException
	{
		int	state = download_manager.getState();

		if ( 	state == DownloadManager.STATE_STOPPED ||
				state == DownloadManager.STATE_QUEUED ){

			download_manager.setStateWaiting();

		}else{

			throw( new DownloadException( "Download::restart: download already running (state=" + state + ")" ));
		}
	}

	@Override
	public void
	stop()

		throws DownloadException
	{
		if ( download_manager.getState() != DownloadManager.STATE_STOPPED){

			download_manager.stopIt( DownloadManager.STATE_STOPPED, false, false );

		}else{

			throw( new DownloadException( "Download::stop: download already stopped" ));
		}
	}

	@Override
	public void setStopReason(String reason) {
		setUserData( UD_KEY_STOP_REASON, reason );
	}

	@Override
	public String getStopReason() {
		return((String)getUserData( UD_KEY_STOP_REASON ));
	}

	@Override
	public void
	stopAndQueue()

		throws DownloadException
	{
		if ( download_manager.getState() != DownloadManager.STATE_QUEUED){

			download_manager.stopIt( DownloadManager.STATE_QUEUED, false, false );

		}else{

			throw( new DownloadException( "Download::stopAndQueue: download already queued" ));
		}
	}

	@Override
	public void
	recheckData()

		throws DownloadException
	{
		if ( !download_manager.canForceRecheck()){

			throw( new DownloadException( "Download::recheckData: download must be stopped, queued or in error state" ));
		}

		download_manager.forceRecheck();
	}

	@Override
	public boolean
	isStartStopLocked()
	{
		return( download_manager.getState() == DownloadManager.STATE_STOPPED );
	}

	@Override
	public boolean
	isForceStart()
	{
		return download_manager.isForceStart();
	}

	@Override
	public void
	setForceStart(boolean forceStart)
	{
		download_manager.setForceStart(forceStart);
	}

	@Override
	public boolean
	isPaused()
	{
		return( download_manager.isPaused());
	}

	@Override
	public void
	pause()
	{
		download_manager.pause( false );
	}

	@Override
	public void
	resume()
	{
		download_manager.resume();
	}

	@Override
	public int
	getPosition()
	{
		return download_manager.getPosition();
	}

	@Override
	public long
	getCreationTime()
	{
		return( download_manager.getCreationTime());
	}

	@Override
	public void
	setPosition(int newPosition)
	{
		download_manager.setPosition(newPosition);
	}

	@Override
	public void
	moveUp()
	{
		download_manager.getGlobalManager().moveUp(download_manager);
	}

	@Override
	public void
	moveDown()
	{
		download_manager.getGlobalManager().moveDown(download_manager);
	}

	@Override
	public void
	moveTo(
		int	pos )
	{
		download_manager.getGlobalManager().moveTo( download_manager, pos );
	}

	@Override
	public String
	getName()
	{
		return download_manager.getDisplayName();
	}

  @Override
  public String getTorrentFileName() {
    return download_manager.getTorrentFileName();
  }

  @Override
  public String getCategoryName() {
    Category category = download_manager.getDownloadState().getCategory();
    if (category == null)
      category = CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED);

    if (category == null)
      return null;
    return category.getName();
  }

  @Override
  public List<Tag> getTags() {
	  return( new ArrayList<Tag>( TagManagerFactory.getTagManager().getTagsForTaggable( download_manager )));
  }

  @Override
  public String
  getAttribute(
  	TorrentAttribute		attribute )
  {
  	String	name = convertAttribute( attribute );

  	if ( name != null ){

  		return( download_manager.getDownloadState().getAttribute( name ));
  	}

  	return( null );
  }

  @Override
  public String[]
  getListAttribute(
  	TorrentAttribute		attribute )
  {
	  	String	name = convertAttribute( attribute );

	  	if ( name != null ){

	  		return( download_manager.getDownloadState().getListAttribute( name ));
	  	}

	  	return( null );
  }

  @Override
  public void
  setListAttribute(
	TorrentAttribute attribute,
	String[] value)
  {
	  String name = convertAttribute(attribute);

	  if (name != null) {
		  download_manager.getDownloadState().setListAttribute(name, value);
	  }
  }

  @Override
  public void
  setMapAttribute(
	TorrentAttribute		attribute,
	Map						value )
  {
	  	String	name = convertAttribute( attribute );

	  	if ( name != null ){

	  			// gotta clone before updating in case user has read values and then just
	  			// updated them - setter code optimises out sets of the same values...

			download_manager.getDownloadState().setMapAttribute( name, BEncoder.cloneMap( value ));
	  	}
  }

  @Override
  public Map
  getMapAttribute(
	TorrentAttribute		attribute )
  {
	  	String	name = convertAttribute( attribute );

	  	if ( name != null ){

	  		return( download_manager.getDownloadState().getMapAttribute( name ));
	  	}

	  	return( null );
  }

  @Override
  public void
  setAttribute(
  	TorrentAttribute		attribute,
	String					value )
  {
 	String	name = convertAttribute( attribute );

  	if ( name != null ){

  		download_manager.getDownloadState().setAttribute( name, value );
  	}
  }

  @Override
  public boolean hasAttribute(TorrentAttribute attribute) {
	  String name = convertAttribute(attribute);
	  if (name == null) {return false;}
	  return download_manager.getDownloadState().hasAttribute(name);
  }

  @Override
  public boolean getBooleanAttribute(TorrentAttribute attribute) {
	  String name = convertAttribute(attribute);
	  if (name == null) {return false;} // Default value
	  return download_manager.getDownloadState().getBooleanAttribute(name);
  }

  @Override
  public void setBooleanAttribute(TorrentAttribute attribute, boolean value) {
	  String name = convertAttribute(attribute);
	  if (name != null) {
		  download_manager.getDownloadState().setBooleanAttribute(name, value);
	  }
  }

  @Override
  public int getIntAttribute(TorrentAttribute attribute) {
	  String name = convertAttribute(attribute);
	  if (name == null) {return 0;} // Default value
	  return download_manager.getDownloadState().getIntAttribute(name);
  }

  @Override
  public void setIntAttribute(TorrentAttribute attribute, int value) {
	  String name = convertAttribute(attribute);
	  if (name != null) {
		  download_manager.getDownloadState().setIntAttribute(name, value);
	  }
  }

  @Override
  public long getLongAttribute(TorrentAttribute attribute) {
	  String name = convertAttribute(attribute);
	  if (name == null) {return 0L;} // Default value
	  return download_manager.getDownloadState().getLongAttribute(name);
  }

  @Override
  public void setLongAttribute(TorrentAttribute attribute, long value) {
	  String name = convertAttribute(attribute);
	  if (name != null) {
		  download_manager.getDownloadState().setLongAttribute(name, value);
	  }
  }

  protected String
  convertAttribute(
  	TorrentAttribute		attribute )
  {
 	if ( attribute.getName() == TorrentAttribute.TA_CATEGORY ){

  		return( DownloadManagerState.AT_CATEGORY );

 	}else if ( attribute.getName() == TorrentAttribute.TA_NETWORKS ){

		return( DownloadManagerState.AT_NETWORKS );

 	}else if ( attribute.getName() == TorrentAttribute.TA_TRACKER_CLIENT_EXTENSIONS ){

		return( DownloadManagerState.AT_TRACKER_CLIENT_EXTENSIONS );

	}else if ( attribute.getName() == TorrentAttribute.TA_PEER_SOURCES ){

		return( DownloadManagerState.AT_PEER_SOURCES );

	}else if ( attribute.getName() == TorrentAttribute.TA_DISPLAY_NAME ){

		return( DownloadManagerState.AT_DISPLAY_NAME );

	}else if ( attribute.getName() == TorrentAttribute.TA_USER_COMMENT ){

		return( DownloadManagerState.AT_USER_COMMENT );

	}else if ( attribute.getName() == TorrentAttribute.TA_RELATIVE_SAVE_PATH ){

		return( DownloadManagerState.AT_RELATIVE_SAVE_PATH );

	}else if ( attribute.getName() == TorrentAttribute.TA_SHARE_PROPERTIES ){

			// this is a share-level attribute only, not propagated to individual downloads

		return( null );

	}else if ( attribute.getName().startsWith( "Plugin." )){

		return( attribute.getName());

  	}else{

  		Debug.out( "Can't convert attribute '" + attribute.getName() + "'" );

  		return( null );
  	}
  }

  protected TorrentAttribute
  convertAttribute(
  	String			name )
  {
 	if ( name.equals( DownloadManagerState.AT_CATEGORY )){

  		return( TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_CATEGORY ));

	}else if ( name.equals( DownloadManagerState.AT_NETWORKS )){

	  	return( TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_NETWORKS ));

	}else if ( name.equals( DownloadManagerState.AT_PEER_SOURCES )){

		return( TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_PEER_SOURCES ));

	}else if ( name.equals( DownloadManagerState.AT_TRACKER_CLIENT_EXTENSIONS )){

		return( TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_TRACKER_CLIENT_EXTENSIONS ));

	}else if ( name.equals ( DownloadManagerState.AT_DISPLAY_NAME)){

		return ( TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_DISPLAY_NAME ));

	}else if ( name.equals ( DownloadManagerState.AT_USER_COMMENT)){

		return ( TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_USER_COMMENT ));

	}else if ( name.equals ( DownloadManagerState.AT_RELATIVE_SAVE_PATH)){

		return ( TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_RELATIVE_SAVE_PATH ));

	}else if ( name.startsWith( "Plugin." )){

		return( TorrentManagerImpl.getSingleton().getAttribute( name ));

  	}else{

  		return( null );
  	}
  }

  @Override
  public void setCategory(String sName) {
    Category category = CategoryManager.getCategory(sName);
    if (category == null)
      category = CategoryManager.createCategory(sName);
    download_manager.getDownloadState().setCategory(category);
  }

  @Override
  public boolean isPersistent() {
    return download_manager.isPersistent();
  }

	@Override
	public void
	remove()

		throws DownloadException, DownloadRemovalVetoException
	{
		remove( false, false );
	}

	@Override
	public void
	remove(
		boolean	delete_torrent,
		boolean	delete_data )

		throws DownloadException, DownloadRemovalVetoException
	{
		int	dl_state = download_manager.getState();

		if ( 	dl_state == DownloadManager.STATE_STOPPED 	||
				dl_state == DownloadManager.STATE_ERROR 	||
				dl_state == DownloadManager.STATE_QUEUED ){

			stopAndRemove(delete_torrent, delete_data);

		}else{

			throw( new DownloadRemovalVetoException( MessageText.getString("plugin.download.remove.veto.notstopped")));
		}
	}

	@Override
	public void
	stopAndRemove(
		boolean	delete_torrent,
		boolean	delete_data )

		throws DownloadException, DownloadRemovalVetoException
	{
		GlobalManager gm = download_manager.getGlobalManager();

		try {

			gm.removeDownloadManager(download_manager, delete_torrent, delete_data);

		} catch (GlobalManagerDownloadRemovalVetoException e) {

			throw (new DownloadRemovalVetoException(e.getMessage()));
		}
	}

	@Override
	public boolean
	canBeRemoved()

		throws DownloadRemovalVetoException
	{
		int	dl_state = download_manager.getState();

		if ( 	dl_state == DownloadManager.STATE_STOPPED 	||
				dl_state == DownloadManager.STATE_ERROR 	||
				dl_state == DownloadManager.STATE_QUEUED ){

			GlobalManager globalManager = download_manager.getGlobalManager();

			try{
				globalManager.canDownloadManagerBeRemoved(download_manager, false, false);

			}catch( GlobalManagerDownloadRemovalVetoException e ){

				throw( new DownloadRemovalVetoException( e.getMessage(),e.isSilent()));
			}

		}else{

			throw( new DownloadRemovalVetoException( MessageText.getString("plugin.download.remove.veto.notstopped")));
		}

		return( true );
	}

	@Override
	public DownloadStats
	getStats()
	{
		return( download_stats );
	}

 	@Override
 	public boolean
	isComplete()
 	{
 		return download_manager.isDownloadComplete(false);
 	}

 	@Override
 	public boolean isComplete(boolean bIncludeDND) {
 		return download_manager.isDownloadComplete(bIncludeDND);
 	}

 	@Override
 	public boolean
 	isChecking()
 	{
 		return( download_stats.getCheckingDoneInThousandNotation() != -1 );
 	}

 	@Override
 	public boolean
 	isMoving()
 	{
 		com.biglybt.core.disk.DiskManager dm = download_manager.getDiskManager();

 		if ( dm != null ){

 			return( dm.getMoveProgress() != null );
 		}

 		return( false );
 	}

	protected void
	isRemovable()
		throws DownloadRemovalVetoException
	{
			// no sync required, see update code

		for (int i=0;i<removal_listeners.size();i++){

			try{
				((DownloadWillBeRemovedListener)removal_listeners.get(i)).downloadWillBeRemoved(this);

			}catch( DownloadRemovalVetoException e ){

				throw( e );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected void
	destroy()
	{
		download_manager.removeListener( this );
	}


	// DownloadManagerListener methods

	@Override
	public void
	stateChanged(
		DownloadManager manager,
		int				state )
	{
		int	prev_state 	= latest_state;

		latest_state	= convertState(state);

		// System.out.println("Plug: dl = " + getName() + ", prev = " + prev_state + ", curr = " + latest_state + ", signalled state = " + state);

		boolean curr_forcedStart = isForceStart();

		// Copy reference in case any attempts to remove or add listeners are tried.
		List listeners_to_use = listeners;

		if ( prev_state != latest_state || latest_forcedStart != curr_forcedStart ){

			latest_forcedStart = curr_forcedStart;

			for (int i=0;i<listeners_to_use.size();i++){

				try{
					long startTime = SystemTime.getCurrentTime();
					DownloadListener listener = (DownloadListener)listeners_to_use.get(i);

					listener.stateChanged( this, prev_state, latest_state );

					long diff = SystemTime.getCurrentTime() - startTime;
					if (diff > 1000) {
						System.out.println("Plugin should move long processes (" + diff
								+ "ms) off of Download's stateChanged listener trigger. "
								+ listener);
					}

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}
	}

	@Override
	public void
	downloadComplete(DownloadManager manager)
	{
		if (this.completion_listeners.isEmpty()) {return;}
		Iterator itr = this.completion_listeners.iterator();
		DownloadCompletionListener dcl;
		while (itr.hasNext()) {
			dcl = (DownloadCompletionListener)itr.next();
			long startTime = SystemTime.getCurrentTime();
			try {dcl.onCompletion(this);}
			catch (Throwable t) {Debug.printStackTrace(t);}
			long diff = SystemTime.getCurrentTime() - startTime;
			if (diff > 1000) {
				System.out.println("Plugin should move long processes (" + diff + "ms) off of Download's onCompletion listener trigger. " + dcl);
			}
		}
	}

	@Override
	public void
	completionChanged(
		DownloadManager 	manager,
		boolean 			bCompleted)
	{
	}

	@Override
	public void
	filePriorityChanged( DownloadManager download, com.biglybt.core.disk.DiskManagerFileInfo file )
	{
	}

  @Override
  public void
  positionChanged(
  	DownloadManager download,
    int oldPosition,
	int newPosition)
  {
	for (int i = 0; i < listeners.size(); i++) {
		try {
			long startTime = SystemTime.getCurrentTime();
			DownloadListener listener = (DownloadListener)listeners.get(i);

			listener.positionChanged(this, oldPosition, newPosition);

			long diff = SystemTime.getCurrentTime() - startTime;
			if (diff > 1000) {
				System.out.println("Plugin should move long processes (" + diff
						+ "ms) off of Download's positionChanged listener trigger. "
						+ listener);
			}
		} catch (Throwable e) {
			Debug.printStackTrace( e );
		}
	}
  }

	@Override
	public void
	addListener(
		DownloadListener	l )
	{
		try{
			listeners_mon.enter();

			List	new_listeners = new ArrayList( listeners );

			new_listeners.add(l);

			listeners	= new_listeners;
		}finally{

			listeners_mon.exit();
		}
	}


	@Override
	public void
	removeListener(
		DownloadListener	l )
	{
		try{
			listeners_mon.enter();

			List	new_listeners	= new ArrayList(listeners);

			new_listeners.remove(l);

			listeners	= new_listeners;
		}finally{

			listeners_mon.exit();
		}
	}

	@Override
	public void addAttributeListener(DownloadAttributeListener listener, TorrentAttribute attr, int event_type) {
		String attribute = convertAttribute(attr);
		if (attribute == null) {return;}

		CopyOnWriteMap attr_map = this.getAttributeMapForType(event_type);
		CopyOnWriteList listener_list = (CopyOnWriteList)attr_map.get(attribute);
		boolean add_self = false;

		if (listener_list == null) {
			listener_list = new CopyOnWriteList();
			attr_map.put(attribute, listener_list);
		}
		add_self = listener_list.isEmpty();

		listener_list.add(listener);
		if (add_self) {
			download_manager.getDownloadState().addListener(this, attribute, event_type);
		}
	}

	@Override
	public void removeAttributeListener(DownloadAttributeListener listener, TorrentAttribute attr, int event_type) {
		String attribute = convertAttribute(attr);
		if (attribute == null) {return;}

		CopyOnWriteMap attr_map = this.getAttributeMapForType(event_type);
		CopyOnWriteList listener_list = (CopyOnWriteList)attr_map.get(attribute);
		boolean remove_self = false;

		if (listener_list != null) {
			listener_list.remove(listener);
			remove_self = listener_list.isEmpty();
		}

		if (remove_self) {
			download_manager.getDownloadState().removeListener(this, attribute, event_type);
		}

	}

	@Override
	public DownloadAnnounceResult
	getLastAnnounceResult()
	{
		TRTrackerAnnouncer tc = download_manager.getTrackerClient();

		if ( tc != null ){

			last_announce_result.setContent( tc.getLastResponse());
		}

		return( last_announce_result );
	}

	@Override
	public DownloadScrapeResult
	getLastScrapeResult()
	{
		TRTrackerScraperResponse response = download_manager.getTrackerScrapeResponse();

		if ( response != null ){

				// don't notify plugins of intermediate (initializing, scraping) states as they would be picked up as errors

			if ( response.getStatus() == TRTrackerScraperResponse.ST_ERROR || response.getStatus() == TRTrackerScraperResponse.ST_ONLINE ){

				last_scrape_result.setContent( response );
			}
		}

		return( last_scrape_result );
	}
	
	@Override
	public DownloadScrapeResult
	getAggregatedScrapeResult(
		boolean		allow_caching )
	{
		updateAggregatedScrapeResult( allow_caching );
	
		int	new_seeds 		= aggregate_scrape.getSeedCount();
		int new_leechers	= aggregate_scrape.getNonSeedCount();

		if ( new_seeds >= 0 || new_leechers >= 0 ){
			
			String cache = download_manager.getDownloadState().getAttribute( DownloadManagerState.AT_AGGREGATE_SCRAPE_CACHE );

			boolean	do_update = true;

			long	mins = SystemTime.getCurrentTime()/(1000*60);

			int	old_seeds 		= -2;
			int old_leechers 	= -2;
			
			if ( cache != null ){

				String[]	bits = cache.split(",");

				if ( bits.length == 3 ){

					long	updated_mins	= 0;

					try{
						updated_mins 	= Long.parseLong( bits[0] );
						old_seeds 		= Integer.parseInt( bits[1] );
						old_leechers 	= Integer.parseInt( bits[2] );

					}catch( Throwable e ){

					}
					
						// rate limit updates - important to reduce the need to serialise download state
						// frequently, especially for inactive torrents

					if ( mins - updated_mins < 15 ){

							// too recent
							
						do_update = false;
						
					}else{
						
						int dl_state = download_manager.getState();
						
						if ( dl_state != DownloadManager.STATE_DOWNLOADING && dl_state != DownloadManager.STATE_SEEDING ){

							if ( mins - updated_mins < 3*60 ){

									// too recent
							
								do_update = false;
							}
						}
					}
				}
			}
				
			if ( new_seeds != old_seeds || new_leechers != old_leechers ){
					
				String str = mins + "," + new_seeds + "," + new_leechers;
						
					// Converted the do_update to a setDirty indicator. This results in the scrape update being
					// persisted if anything else causes the state to be persisted as opposed to throwing the
					// update away if do_update = false
				
				download_manager.getDownloadState().setAttribute( DownloadManagerState.AT_AGGREGATE_SCRAPE_CACHE, str, do_update  );
			}
		}

		return( aggregate_scrape );
	}

	private volatile long 					last_asr_calc = -1;
	
	private void
	updateAggregatedScrapeResult(
		boolean	allow_caching )
	{
		long	now = SystemTime.getMonotonousTime();
				
		if ( allow_caching && now - last_asr_calc < 10*1000 ){
			
			return;
		}
		
		updateAggregatedScrapeResult();
		
		last_asr_calc = now;
	}
	
	private void
	updateAggregatedScrapeResult()
	{
		List<TRTrackerScraperResponse> responses = download_manager.getGoodTrackerScrapeResponses();

		int	best_peers 	= -1;
		int best_seeds	= -1;
		int	best_time	= -1;

		TRTrackerScraperResponse	best_resp	= null;

		if ( responses != null ){

			for ( TRTrackerScraperResponse response: responses ){

				int	peers = response.getPeers();
				int seeds = response.getSeeds();

				if ( 	peers > best_peers ||
						( peers == best_peers && seeds > best_seeds )){

					best_peers	= peers;
					best_seeds	= seeds;

					best_resp = response;
				}
			}
		}

			// if no good real tracker responses then use less reliable DHT ones

		if ( best_peers == -1 ){

			try{
				TrackerPeerSource our_dht = null;

				List<TrackerPeerSource> peer_sources = download_manager.getTrackerPeerSources();

				for ( TrackerPeerSource ps: peer_sources ){

					if ( ps.getType() == TrackerPeerSource.TP_DHT ){

						our_dht = ps;

						break;
					}
				}

				peer_listeners_mon.enter();

				if ( announce_response_map != null ){

					int	total_seeds = 0;
					int total_peers	= 0;
					int	latest_time	= 0;

					int	num = 0;

					if ( our_dht != null && our_dht.getStatus() == TrackerPeerSource.ST_ONLINE ){

						total_seeds = our_dht.getSeedCount();
						total_peers	= our_dht.getLeecherCount();
						latest_time = our_dht.getLastUpdate();

						num = 1;
					}

					for ( int[] entry: announce_response_map.values()){

						num++;

						int	seeds 	= entry[0];
						int	peers 	= entry[1];
						int time	= entry[3];

						total_seeds += seeds;
						total_peers += peers;

						if ( time > latest_time ){

							latest_time	= time;
						}
					}

					if ( total_peers >= 0 ){

						best_peers	= Math.max( 1, total_peers / num );
						best_seeds	= total_seeds / num;

						if ( total_seeds > 0 && best_seeds == 0 ){

							best_seeds = 1;
						}
						best_time	= latest_time;
						best_resp	= null;
					}
				}

			}finally{

				peer_listeners_mon.exit();
			}
		}

		if ( best_peers >= 0 ){

			// System.out.println( download_manager.getDisplayName() + ": " + best_peers + "/" + best_seeds + "/" + best_resp );

			aggregate_scrape.update( best_resp, best_seeds, best_peers, best_time );
		}
	}

	@Override
	public void
	scrapeResult(
		TRTrackerScraperResponse	response )
	{
		// don't notify plugins of intermediate (initializing, scraping) states as they would be picked up as errors
		if(response.getStatus() != TRTrackerScraperResponse.ST_ERROR && response.getStatus() != TRTrackerScraperResponse.ST_ONLINE)
			return;

		last_scrape_result.setContent( response );

		for (int i=0;i<tracker_listeners.size();i++){

			try{
				((DownloadTrackerListener)tracker_listeners.get(i)).scrapeResult( last_scrape_result );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	// Used by DownloadEventNotifierImpl.
	void announceTrackerResultsToListener(DownloadTrackerListener l) {
		l.announceResult(last_announce_result);
		l.scrapeResult(last_scrape_result);
	}

	@Override
	public void
	announceResult(
		TRTrackerAnnouncerResponse			response )
	{
		last_announce_result.setContent( response );

		List	tracker_listeners_ref = tracker_listeners;

		for (int i=0;i<tracker_listeners_ref.size();i++){

			try{
				((DownloadTrackerListener)tracker_listeners_ref.get(i)).announceResult( last_announce_result );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	public TrackerPeerSource[]
	getTrackerPeerSources()
	{
		List<String> names = null;
		
		try{
			peer_listeners_mon.enter();
		
			if ( announce_response_map != null ){
			
				names = new ArrayList<>( announce_response_map.keySet());
			}
		}finally{

			peer_listeners_mon.exit();
		}
		
		if ( names == null || names.size() == 0 ){
			
			return( new TrackerPeerSource[0] );
			
		}else{
		
			TrackerPeerSourceAdapter[] result = new TrackerPeerSourceAdapter[names.size()];
			
			for ( int i=0;i<result.length;i++){
				
				String name = names.get(i);
				
				result[i] =
					new TrackerPeerSourceAdapter()
					{
						private long	fixup;
						private int		state;
						private String 	details 	= "";
						private int		seeds		= -1;
						private int		leechers	= -1;
						private int		peers		= -1;
						private int		time		= -1;
						private int		next_time	= -1;
		
						private void
						fixup()
						{
							long	now = SystemTime.getCurrentTime();
		
							if ( now - fixup > 1000 ){
										
								if ( !download_manager.getDownloadState().isPeerSourceEnabled( PEPeerSource.PS_PLUGIN )){
		
									state = ST_DISABLED;
		
								}else{
		
									int s = getState();
		
									if ( s == ST_DOWNLOADING || s == ST_SEEDING ){
		
										state = ST_ONLINE;
		
									}else{
		
										state = ST_STOPPED;
									}
								}
		
								try{
									peer_listeners_mon.enter();
	
												
									int[] data =  announce_response_map.get(name);
	
									if ( data != null ){

										seeds		= data[0];
										leechers	= data[1];
										peers		= data[2];

										if ( peers == -1 ){
											
												// scrape if valid
											
											if ( seeds >= 0 ){
												
												details = name + " " +seeds + "/" + leechers;
												
											}else{
												
												details = name;
											}

										}else{
											
												// announce if valid
											
											if ( seeds >= 0 ){
												
												details = name + " " +seeds + "/" + leechers + "/" + peers;
												
											}else{
												
												details = name;
											}
										}

										
										time 		= data[3];
										next_time	= data[4];
										
									}else{
									
										details 	=  name;
		
										seeds		= -1;
										leechers	= -1;
										peers		= -1;
										time		= -1;
										next_time	= -1;
									}
								}finally{
	
									peer_listeners_mon.exit();
								}
		
								fixup = now;
							}
						}
		
						@Override
						public int
						getType()
						{
							return( TP_PLUGIN );
						}
		
						@Override
						public int
						getStatus()
						{
							fixup();
		
							return( state );
						}
		
						@Override
						public String
						getName()
						{
							fixup();
				
							return( details );
						}
		
						@Override
						public String 
						getDetails()
						{
							return( getName());
						}
						
						@Override
						public int
						getSeedCount()
						{
							fixup();
				
							return( seeds );

						}
		
						@Override
						public int
						getLeecherCount()
						{
							fixup();
				
							return( leechers );
						}
		
						@Override
						public int
						getPeers()
						{
							fixup();
				
							return( peers );
						}
						
						@Override
						public int 
						getLastUpdate()
						{
							return( time );
						}
						
						@Override
						public int 
						getSecondsToUpdate()
						{
							if ( next_time == 0 ){
								
								return( -1 );
								
							}else{
								
								int	now_secs = (int)(SystemTime.getCurrentTime()/1000);
								
								int	rem = next_time - now_secs;
								
								if ( rem < 0 ){
									
									return( 0 );
									
								}else{
									
									return( rem );
								}
							}
						}
					};
			}
		
			return( result );
		}
	}

	private String
	getTrackingName(
		Object		obj )
	{
		String	name = obj.getClass().getName();

		int	pos = name.lastIndexOf( '.' );

		name = name.substring( pos+1 );

		pos = name.indexOf( '$' );

		if ( pos != -1 ){

			name = name.substring( 0, pos );
		}

			// hack alert - could use classloader to find plugin I guess

		pos = name.indexOf( "DHTTrackerPlugin" );

		if ( pos == 0 ){

				// built in

			name = null;

		}else if ( pos > 0 ){

			name = name.substring( 0, pos );

		}else if ( name.equals( "DHTAnnounceResult") || name.equals( "DHTScrapeResult")){

			name = "mlDHT";
		}

		return( name );
	}

	@Override
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
		String class_name = getTrackingName( result );

		String plugin_id;
		
		if ( class_name == null ){
			
			plugin_id = "azbpdhdtracker";
			
		}else{
			
			if ( class_name.equalsIgnoreCase( "I2P" )){
				
				plugin_id = "azneti2phelper";
				
			}else{
				
				plugin_id = class_name;
			}
		}
		
		boolean skip_announce = false;
		
		if ( plugin_id != null ){
			
			Map all_opts = download_manager.getDownloadState().getMapAttribute( DownloadManagerState.AT_PLUGIN_OPTIONS );
			
			if ( all_opts != null ){
				
				Map opts = (Map)all_opts.get( plugin_id.toLowerCase( Locale.US ));
				
				if ( opts != null ){
					
					Number e = (Number)opts.get( "enableannounce" );
					
					if ( e != null && e.intValue() == 0 ){
						
						skip_announce = true;
					}
				}
			}
		}
		
		boolean new_entry = false;
		
		if ( class_name != null ){

			int	seeds 		= result.getSeedCount();
			int	leechers 	= result.getNonSeedCount();

			DownloadAnnounceResultPeer[] peers = result.getPeers();

			int	peer_count = peers==null?0:peers.length;

			try{
				peer_listeners_mon.enter();

				if ( announce_response_map == null ){

					announce_response_map = new HashMap<>();

				}else{

					if ( announce_response_map.size() > 32 ){

						Debug.out( "eh?" );

						announce_response_map.clear();
					}
				}

				int[]	data = (int[])announce_response_map.get( class_name );

				if ( data == null ){

					data = new int[5];

					announce_response_map.put( class_name, data );
					
					new_entry = true;
				}

				data[0]	= seeds;
				data[1]	= leechers;
				data[2]	= peer_count;
				data[3] = (int)(SystemTime.getCurrentTime()/1000);
				data[4] = (int)(SystemTime.getCurrentTime()/1000 + result.getTimeToWait());
			}finally{

				peer_listeners_mon.exit();
			}
		}

		if ( !skip_announce ){
		
			download_manager.setAnnounceResult( result );
		}
		
		if ( new_entry ){
			
			download_manager.informTPSChanged();
		}
	}

	@Override
	public void
	setScrapeResult(
		DownloadScrapeResult	result )
	{
		String class_name = getTrackingName( result );

		boolean new_entry = false;

		if ( class_name != null ){

			int	seeds 		= result.getSeedCount();
			int	leechers 	= result.getNonSeedCount();

			try{
				peer_listeners_mon.enter();

				if ( announce_response_map == null ){

					announce_response_map = new HashMap<>();

				}else{

					if ( announce_response_map.size() > 32 ){

						Debug.out( "eh?" );

						announce_response_map.clear();
					}
				}

				int[]	data = (int[])announce_response_map.get( class_name );

				if ( data == null ){

					data = new int[5];

					data[2] = -1;	// peers, no data from a scrape
					
					announce_response_map.put( class_name, data );
					
					new_entry = true;
				}

				data[0]	= seeds;
				data[1]	= leechers;
				
				if ( result.getScrapeStartTime() <= 0 ){
					data[3] = -1;
				}else{
					data[3] = (int)(SystemTime.getCurrentTime()/1000);
				}
				data[4] = (int)(result.getNextScrapeStartTime()/1000);
			}finally{

				peer_listeners_mon.exit();
			}
		}

		download_manager.setScrapeResult( result );
		
		if ( new_entry ){
			
			download_manager.informTPSChanged();
		}
	}

	public void
	torrentChanged()
	{
		TRTrackerAnnouncer	client = download_manager.getTrackerClient();

		if ( client != null ){

			client.resetTrackerUrl(true);
		}
	}

	@Override
	public void
	addTrackerListener(
		DownloadTrackerListener	l )
	{
		addTrackerListener(l, true);
	}

	@Override
	public void
	addTrackerListener(
		DownloadTrackerListener	l,
		boolean immediateTrigger )
	{
		try{
			tracker_listeners_mon.enter();

			List	new_tracker_listeners = new ArrayList( tracker_listeners );

			new_tracker_listeners.add( l );

			tracker_listeners	= new_tracker_listeners;

			if ( tracker_listeners.size() == 1 ){

				download_manager.addTrackerListener( this );
			}
		}finally{

			tracker_listeners_mon.exit();
		}

		if (immediateTrigger) {this.announceTrackerResultsToListener(l);}
	}

	@Override
	public void
	removeTrackerListener(
		DownloadTrackerListener	l )
	{
		try{
			tracker_listeners_mon.enter();

			List	new_tracker_listeners	= new ArrayList( tracker_listeners );

			new_tracker_listeners.remove( l );

			tracker_listeners	= new_tracker_listeners;

			if ( tracker_listeners.size() == 0 ){

				download_manager.removeTrackerListener( this );
			}
		}finally{

			tracker_listeners_mon.exit();
		}
	}

	@Override
	public void
	addDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l )
	{
		try{
			removal_listeners_mon.enter();

			List	new_removal_listeners	= new ArrayList( removal_listeners );

			new_removal_listeners.add(l);

			removal_listeners	= new_removal_listeners;

		}finally{

			removal_listeners_mon.exit();
		}
	}

	@Override
	public void
	removeDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l )
	{
		try{
			removal_listeners_mon.enter();

			List	new_removal_listeners	= new ArrayList( removal_listeners );

			new_removal_listeners.remove(l);

			removal_listeners	= new_removal_listeners;

		}finally{

			removal_listeners_mon.exit();
		}
	}

	@Override
	public void
	addPeerListener(
		final DownloadPeerListener	listener )
	{
		DownloadManagerPeerListener delegate =
			new DownloadManagerPeerListener()
			{

				@Override
				public void
				peerManagerAdded(
					PEPeerManager	manager )
				{
					PeerManager pm = PeerManagerImpl.getPeerManager( manager);

					listener.peerManagerAdded( DownloadImpl.this, pm );
				}

				@Override
				public void
				peerManagerRemoved(
					PEPeerManager	manager )
				{
					PeerManager pm = PeerManagerImpl.getPeerManager( manager);

					listener.peerManagerRemoved( DownloadImpl.this, pm );

				}

				@Override
				public void
				peerManagerWillBeAdded(
					PEPeerManager	manager )
				{
				}

				@Override
				public void
				peerAdded(
					PEPeer 	peer )
				{
				}

				@Override
				public void
				peerRemoved(
					PEPeer	peer )
				{
				}
			};

		try{
			peer_listeners_mon.enter();

			peer_listeners.put( listener, delegate );

		}finally{

			peer_listeners_mon.exit();
		}

		download_manager.addPeerListener( delegate );
	}


	@Override
	public void
	removePeerListener(
		DownloadPeerListener	listener )
	{
		DownloadManagerPeerListener delegate;

		try{
			peer_listeners_mon.enter();

			delegate = (DownloadManagerPeerListener)peer_listeners.remove( listener );

		}finally{

			peer_listeners_mon.exit();
		}

		if ( delegate == null ){

			// sometimes we end up with double removal so don't bother spewing about this
			// Debug.out( "Listener not found for removal" );

		}else{

			download_manager.removePeerListener( delegate );
		}
	}

	@Override
	public boolean
	activateRequest(
		final int		count )
	{
		DownloadActivationEvent event =
			new DownloadActivationEvent()
		{
			@Override
			public Download
			getDownload()
			{
				return( DownloadImpl.this );
			}

			@Override
			public int
			getActivationCount()
			{
				return( count );
			}
		};

		for (Iterator it=activation_listeners.iterator();it.hasNext();){

			try{
				DownloadActivationListener	listener = (DownloadActivationListener)it.next();

				if ( listener.activationRequested( event )){

					return( true );
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		return( false );
	}

	@Override
	public DownloadActivationEvent
	getActivationState()
	{
		return( activation_state );
	}

	@Override
	public void
	addActivationListener(
		DownloadActivationListener		l )
	{
		try{
			peer_listeners_mon.enter();

			activation_listeners.add( l );

			if ( activation_listeners.size() == 1 ){

				download_manager.addActivationListener( this );
			}
		}finally{

			peer_listeners_mon.exit();
		}
	}

	@Override
	public void
	removeActivationListener(
		DownloadActivationListener		l )
	{
		try{
			peer_listeners_mon.enter();

			activation_listeners.remove( l );

			if ( activation_listeners.size() == 0 ){

				download_manager.removeActivationListener( this );
			}
		}finally{

			peer_listeners_mon.exit();
		}
	}

	@Override
	public void	addCompletionListener(DownloadCompletionListener l) {
		try {
			listeners_mon.enter();
			this.completion_listeners.add(l);
		}
		finally{
			listeners_mon.exit();
		}
	}

	@Override
	public void	removeCompletionListener(DownloadCompletionListener l) {
		try {
			listeners_mon.enter();
			this.completion_listeners.remove(l);
		}
		finally{
			listeners_mon.exit();
		}
	}

 	@Override
  public PeerManager
	getPeerManager()
 	{
 		PEPeerManager	pm = download_manager.getPeerManager();

 		if ( pm == null ){

 			return( null );
 		}

 		return( PeerManagerImpl.getPeerManager( pm));
 	}

	@Override
	public DiskManager
	getDiskManager()
	{
		PeerManager	pm = getPeerManager();

		if ( pm != null ){

			return( pm.getDiskManager());
		}

		return( null );
	}

	@Override
	public int getDiskManagerFileCount() {
		return download_manager.getNumFileInfos();
	}

	@Override
	public DiskManagerFileInfo getDiskManagerFileInfo(int index) {
		com.biglybt.core.disk.DiskManagerFileInfo[] info = download_manager.getDiskManagerFileInfo();

		if (info == null) {
			return null;
		}
		if (index < 0 || index >= info.length) {
			return null;
		}

		return new DiskManagerFileInfoImpl(this, info[index]);
	}

	@Override
	public DiskManagerFileInfo
	getPrimaryFile() {
		com.biglybt.core.disk.DiskManagerFileInfo primaryFile = download_manager.getDownloadState().getPrimaryFile();

		if (primaryFile == null) {
			return null;
		}
		return new DiskManagerFileInfoImpl(this, primaryFile);
	}

	@Override
	public DiskManagerFileInfo[]
	getDiskManagerFileInfo()
	{
		com.biglybt.core.disk.DiskManagerFileInfo[] info = download_manager.getDiskManagerFileInfo();

		if ( info == null ){

			return( new DiskManagerFileInfo[0] );
		}

		DiskManagerFileInfo[]	res = new DiskManagerFileInfo[info.length];

		for (int i=0;i<res.length;i++){

			res[i] = new DiskManagerFileInfoImpl( this, info[i] );
		}

		return( res );
	}

 	@Override
  public void
	setMaximumDownloadKBPerSecond(
		int		kb )
 	{
         if(kb==-1){
            Debug.out("setMaximiumDownloadKBPerSecond got value (-1) ZERO_DOWNLOAD. (-1)"+
                "does not work through this method, use getDownloadRateLimitBytesPerSecond() instead.");
         }//if

         download_manager.getStats().setDownloadRateLimitBytesPerSecond( kb < 0 ? 0 : kb*1024 );
 	}

	@Override
	public int getMaximumDownloadKBPerSecond() {
		int bps = download_manager.getStats().getDownloadRateLimitBytesPerSecond();
		return bps <= 0 ? bps : (bps < 1024 ? 1 : bps / 1024);
	}

  	@Override
	  public int getUploadRateLimitBytesPerSecond() {
      return download_manager.getStats().getUploadRateLimitBytesPerSecond();
  	}

  	@Override
	  public void setUploadRateLimitBytesPerSecond(int max_rate_bps ) {
      download_manager.getStats().setUploadRateLimitBytesPerSecond( max_rate_bps );
  	}

  	@Override
	  public int getDownloadRateLimitBytesPerSecond() {
  		return download_manager.getStats().getDownloadRateLimitBytesPerSecond();
  	}

  	@Override
	  public void setDownloadRateLimitBytesPerSecond(int max_rate_bps ) {
  		download_manager.getStats().setDownloadRateLimitBytesPerSecond( max_rate_bps );
  	}

	@Override
	public void
	addRateLimiter(
		RateLimiter		limiter,
		boolean			is_upload )
	{
		download_manager.addRateLimiter( UtilitiesImpl.wrapLimiter( limiter, false ), is_upload );
	}

	@Override
	public void
	removeRateLimiter(
		RateLimiter		limiter,
		boolean			is_upload )
	{
		download_manager.removeRateLimiter( UtilitiesImpl.wrapLimiter( limiter, false ), is_upload );
	}

  @Override
  public SeedingRank getSeedingRank() {
    return download_manager.getSeedingRank();
  }

	@Override
	public void setSeedingRank(SeedingRank rank) {
		download_manager.setSeedingRank(rank);
	}

	@Override
	public String
	getSavePath()
 	{
		return( download_manager.getSaveLocation().toString());
 	}

	@Override
	public void
  	moveDataFiles(
  		File	new_parent_dir )

  		throws DownloadException
  	{
 		try{
 			download_manager.moveDataFiles( new_parent_dir );

 		}catch( DownloadManagerException e ){

 			throw( new DownloadException("move operation failed", e ));
 		}
  	}

	@Override
	public void moveDataFiles(File new_parent_dir, String new_name)

  		throws DownloadException
  	{
 		try{
 			download_manager.moveDataFiles( new_parent_dir, new_name );

 		}catch( DownloadManagerException e ){

 			throw( new DownloadException("move / rename operation failed", e ));
 		}
  	}

	@Override
	public void renameDownload(String new_name) throws DownloadException {
		try {download_manager.renameDownload(new_name);}
		catch (DownloadManagerException e) {
			throw new DownloadException("rename operation failed", e);
		}
	}

  	@Override
	  public void
  	moveTorrentFile(
  		File	new_parent_dir )

  		throws DownloadException
 	{
		try{
 			download_manager.moveTorrentFile( new_parent_dir );

 		}catch( DownloadManagerException e ){

 			throw( new DownloadException("move operation failed", e ));
 		}
 	}

	@Override
  public void
	requestTrackerAnnounce()
 	{
 		download_manager.requestTrackerAnnounce( false );
 	}

	@Override
	public void
	requestTrackerAnnounce(
		boolean		immediate )
	{
		download_manager.requestTrackerAnnounce( immediate );
	}

	@Override
	public void
	requestTrackerScrape(
		boolean		immediate )
	{
		download_manager.requestTrackerScrape( immediate );
	}

  @Override
  public byte[] getDownloadPeerId() {
    TRTrackerAnnouncer announcer = download_manager.getTrackerClient();
    if(announcer == null) return null;
    return announcer.getPeerId();
  }


  @Override
  public boolean isMessagingEnabled() {  return download_manager.getExtendedMessagingMode() == BTHandshake.AZ_RESERVED_MODE;  }

  @Override
  public void setMessagingEnabled(boolean enabled ) {
	  throw new RuntimeException("setMessagingEnabled is in the process of being removed - if you are seeing this error, let the Azureus developers know that you need this method to stay!");
    //download_manager.setAZMessagingEnabled( enabled );
  }


 	// Deprecated methods

	@Override
  public boolean isRemoved() {
	return( download_manager.isDestroyed());
  }
  // Pass LogRelation off to core objects

	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#getLogRelationText()
	 */
	@Override
	public String getRelationText() {
		return propogatedRelationText(download_manager);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#getQueryableInterfaces()
	 */
	@Override
	public Object[] getQueryableInterfaces() {
		return new Object[] { download_manager };
	}

	private CopyOnWriteMap getAttributeMapForType(int event_type) {
		return event_type == DownloadAttributeListener.WILL_BE_READ ? read_attribute_listeners_map_cow : write_attribute_listeners_map_cow;
	}

	@Override
	public boolean canMoveDataFiles() {
		return download_manager.canMoveDataFiles();
	}

	@Override
	public void attributeEventOccurred(DownloadManager download, String attribute, int event_type) {
		CopyOnWriteMap attr_listener_map = getAttributeMapForType(event_type);

		TorrentAttribute attr = convertAttribute(attribute);
		if (attr == null) {return;}

		List listeners = null;
		listeners = ((CopyOnWriteList)attr_listener_map.get(attribute)).getList();

		if (listeners == null) {return;}

		for (int i=0; i<listeners.size(); i++) {
			DownloadAttributeListener dal = (DownloadAttributeListener)listeners.get(i);
			try {dal.attributeEventOccurred(this, attr, event_type);}
			catch (Throwable t) {Debug.printStackTrace(t);}
		}
	}

	@Override
	public SaveLocationChange calculateDefaultDownloadLocation() {
		return DownloadManagerMoveHandler.recalculatePath(this.download_manager);
	}

	 @Override
	 public Object getUserData(Object key ){
		 return( download_manager.getUserData(key));
	 }

	 @Override
	 public void setUserData(Object key, Object data ){
		 download_manager.setUserData(key, data);
	 }

	 @Override
	 public void startDownload(boolean force) {
		if (force) {
			this.setForceStart(true);
			return;
		}
		this.setForceStart(false);

		int state = this.getState();
		if (state == DownloadManager.STATE_STOPPED ||	state == DownloadManager.STATE_QUEUED) {
			download_manager.setStateWaiting();
		}

	 }

	 @Override
	 public void stopDownload() {
		 if (download_manager.getState() == DownloadManager.STATE_STOPPED) {return;}
		 download_manager.stopIt(DownloadManager.STATE_STOPPED, false, false);
	 }

	 	// stub stuff

	 @Override
	 public boolean
	 isStub()
	 {
		 return( false );
	 }

	 @Override
	 public boolean
	 canStubbify()
	 {
		 return( manager.canStubbify( this ));
	 }

	 @Override
	 public DownloadStub
	 stubbify()

	 	throws DownloadException, DownloadRemovalVetoException
	 {
		return( manager.stubbify( this ));
	 }

	 @Override
	 public Download
	 destubbify()

	 	throws DownloadException
	 {
		 return( this );
	 }

	 @Override
	 public List<DistributedDatabase>
	 getDistributedDatabases()
	 {
		 return( DDBaseImpl.getDDBs( this ));
	 }

	 @Override
	 public byte[]
	 getTorrentHash()
	 {
		 Torrent t = getTorrent();

		 if ( t == null ){

			 return( null );
		 }

		 return( t.getHash());
	 }


	 @Override
	 public long
	 getTorrentSize()
	 {
		 Torrent t = getTorrent();

		 if ( t == null ){

			 return( 0 );
		 }

		 return( t.getSize());
	 }

	 @Override
	 public DownloadStubFile[]
	 getStubFiles()
	 {
		 DiskManagerFileInfo[] dm_files = getDiskManagerFileInfo();

		 DownloadStubFile[] files = new DownloadStubFile[dm_files.length];

		 for ( int i=0;i<files.length;i++){

			 final DiskManagerFileInfo dm_file = dm_files[i];

			 files[i] =
				new	DownloadStubFile()
			 	{
					@Override
					public File
					getFile()
					{
						return( dm_file.getFile( true ));
					}

					@Override
					public long
					getLength()
					{
						if ( dm_file.getDownloaded() == dm_file.getLength() && !dm_file.isSkipped()){

							return( dm_file.getLength());

						}else{

							return( -dm_file.getLength());
						}
					}
			 	};
		 }

		 return( files );
	 }


	 @Override
	 public void changeLocation(SaveLocationChange slc) throws DownloadException {

		 // No change in the file.
		 boolean has_change = slc.hasDownloadChange() || slc.hasTorrentChange();
		 if (!has_change) {return;}

		 // Test that one of the locations is actually different.
		 has_change = slc.isDifferentDownloadLocation(FileUtil.newFile(this.getSavePath()));
		 if (!has_change) {
			 has_change = slc.isDifferentTorrentLocation(FileUtil.newFile(this.getTorrentFileName()));
		 }

		 if (!has_change) {return;}

		 boolean try_to_resume = !this.isPaused();
		 try {
			 try {
				 if (slc.hasDownloadChange()) {download_manager.moveDataFiles(slc.download_location, slc.download_name);}
				 if (slc.hasTorrentChange()) {download_manager.moveTorrentFile(slc.torrent_location, slc.torrent_name);}
			 }
			 catch (DownloadManagerException e) {
				 throw new DownloadException(e.getMessage(), e);
			 }
		 }
		 finally {
			 if (try_to_resume) {this.resume();}
		 }

	 }

	 private static class
	 AggregateScrapeResult
	 	implements DownloadScrapeResult
	 {
		private Download	dl;

		private TRTrackerScraperResponse		response;

		private int		seeds;
		private int		leechers;

		private int		time_secs;

		private
		AggregateScrapeResult(
			Download		_dl,
			DownloadManager	_dm )
		{
			dl	= _dl;

			try{			
				DownloadManagerState dm_state = _dm.getDownloadState();
				
				String ag_cache = dm_state.getAttribute( DownloadManagerState.AT_AGGREGATE_SCRAPE_CACHE );

				if ( ag_cache != null ){
	
					String[]	bits = ag_cache.split(",");
	
					if ( bits.length == 3 ){
	
						time_secs = Integer.parseInt( bits[0] )*60;
							
						seeds 		= Integer.parseInt( bits[1] );
						leechers 	= Integer.parseInt( bits[2] );
					}
				}else{
					
					long cache = dm_state.getLongAttribute( DownloadManagerState.AT_SCRAPE_CACHE );

					if ( cache != -1 ){

						seeds		= (int)((cache>>32)&0x00ffffff);
						leechers	= (int)(cache&0x00ffffff);
					}			
				}
			}catch( Throwable e ){
			}
		}

		private void
		update(
			TRTrackerScraperResponse		_response,
			int								_seeds,
			int								_peers,
			int								_time_secs )
		{
			response			= _response;
			seeds				= _seeds;
			leechers			= _peers;
			time_secs			= _time_secs;
		}

		@Override
		public Download
		getDownload()
		{
			return( dl );
		}

		@Override
		public int
		getResponseType()
		{
			return( RT_SUCCESS );
		}

		@Override
		public int
		getSeedCount()
		{
			return( seeds );
		}

		@Override
		public int
		getNonSeedCount()
		{
			return( leechers );
		}

		@Override
		public long
		getScrapeStartTime()
		{
			TRTrackerScraperResponse r = response;

			if ( r != null ){

				return( r.getScrapeStartTime());
			}

			if ( time_secs <= 0 ){

				return( -1 );

			}else{

				return( time_secs * 1000L );
			}
		}

		@Override
		public void
		setNextScrapeStartTime(
			long nextScrapeStartTime)
		{
			Debug.out( "Not Supported" );
		}

		@Override
		public long
		getNextScrapeStartTime()
		{
			TRTrackerScraperResponse r = response;

			return( r == null?-1:r.getScrapeStartTime());
		}

		@Override
		public String
		getStatus()
		{
			return( "Aggregate Scrape" );
		}

		@Override
		public URL
		getURL()
		{
			TRTrackerScraperResponse r = response;

			return( r == null?null:r.getURL());
		}
	 }
}
