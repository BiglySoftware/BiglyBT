/*
 * File    : RPPluginInterface.java
 * Created : 28-Jan-2004
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

package com.biglybt.pifimpl.remote;

/**
 * @author parg
 *
 */

import java.util.Iterator;
import java.util.Properties;
import java.util.Random;

import com.biglybt.core.util.Constants;
import com.biglybt.pif.*;
import com.biglybt.pif.clientid.ClientIDManager;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.dht.mainline.MainlineDHTManager;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pif.ipfilter.IPFilter;
import com.biglybt.pif.logging.Logger;
import com.biglybt.pif.messaging.MessageManager;
import com.biglybt.pif.network.ConnectionManager;
import com.biglybt.pif.platform.PlatformManager;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.update.UpdateManager;
import com.biglybt.pif.utils.ShortCuts;
import com.biglybt.pif.utils.Utilities;
import com.biglybt.pifimpl.remote.download.RPDownloadManager;
import com.biglybt.pifimpl.remote.ipfilter.RPIPFilter;
import com.biglybt.pifimpl.remote.torrent.RPTorrentManager;
import com.biglybt.pifimpl.remote.tracker.RPTracker;
import com.biglybt.pifimpl.remote.utils.RPShortCuts;

public class
RPPluginInterface
	extends		RPObject
	implements 	PluginInterface
{
	protected transient static long		connection_id_next		= new Random().nextLong();

	protected transient PluginInterface		delegate;
	protected transient long				request_id_next;

	// don't change these field names as they are visible on XML serialisation

	public String			azureus_name		= Constants.BIGLYBT_NAME;
	public String			azureus_version		= Constants.BIGLYBT_VERSION;

	// **** Don't try using AEMOnitor for synchronisations here as this object is serialised

	public static RPPluginInterface
	create(
		PluginInterface		_delegate )
	{
		RPPluginInterface	res =(RPPluginInterface)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPPluginInterface( _delegate );
		}

		return( res );
	}

	public long	_connection_id;

	protected
	RPPluginInterface(
		PluginInterface _delegate )
	{
		super( _delegate );

		synchronized( RPPluginInterface.class ){

			_connection_id = connection_id_next++;

				// avoid 0 as it has special meaning (-> no connection for singleton calls);

			if ( _connection_id == 0 ){

				_connection_id = connection_id_next++;
			}
		}
	}

	protected long
	_getConectionId()
	{
		return( _connection_id );
	}

	protected long
	_getNextRequestId()
	{
		synchronized( this ){

			return( request_id_next++ );
		}
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (PluginInterface)_delegate;
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		return( _fixupLocal());
	}


	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String	method = request.getMethod();

		if ( method.equals( "getPluginProperties")){

				// must copy properties as actual return is subtype + non serialisable

			Properties p = new Properties();

			Properties x = delegate.getPluginProperties();

			Iterator	it = x.keySet().iterator();

			while(it.hasNext()){

				Object	key = it.next();

				p.put( key, x.get(key));
			}

			return( new RPReply( p ));

		}else if ( method.equals( "getDownloadManager")){

			return( new RPReply( RPDownloadManager.create(delegate.getDownloadManager())));

		}else if ( method.equals( "getTorrentManager")){

			return( new RPReply( RPTorrentManager.create(delegate.getTorrentManager())));

		}else if ( method.equals( "getPluginconfig")){

			return( new RPReply( RPPluginConfig.create(delegate.getPluginconfig())));

		}else if ( method.equals( "getIPFilter")){

			return( new RPReply( RPIPFilter.create(delegate.getIPFilter())));

		}else if ( method.equals( "getShortCuts")){

			return( new RPReply( RPShortCuts.create(delegate.getShortCuts())));

		}else if ( method.equals( "getTracker")){

			return( new RPReply( RPTracker.create(delegate.getTracker())));
		}

		throw( new RPException( "Unknown method: " + method ));
	}

		// ******************************************

	@Override
	public PluginManager
	getPluginManager()
	{
		notSupported();

		return( null );
	}

	@Override
	public Plugin
	getPlugin()
	{
		notSupported();

		return( null );
	}

	@Override
	public String
	getAzureusName()
	{
		return( azureus_name );
	}

	@Override
	public String
	getApplicationVersion()
	{
		return( azureus_version );
	}

  	@Override
	  public String getApplicationName() {
  		return Constants.APP_NAME;
  	}

	@Override
	public Tracker
	getTracker()
	{
		RPTracker	res = (RPTracker)_dispatcher.dispatch( new RPRequest( this, "getTracker", null )).getResponse();

		res._setRemote( _dispatcher );

		return( res );
	}

	@Override
	public Logger getLogger()
	{
		notSupported();

		return( null );
	}

	@Override
	public IPFilter
	getIPFilter()
	{
		RPIPFilter	res = (RPIPFilter)_dispatcher.dispatch( new RPRequest( this, "getIPFilter", null )).getResponse();

		res._setRemote( _dispatcher );

		return( res );
	}

	@Override
	public DownloadManager
	getDownloadManager()
	{
		RPDownloadManager	res = (RPDownloadManager)_dispatcher.dispatch( new RPRequest( this, "getDownloadManager", null )).getResponse();

		res._setRemote( _dispatcher );

		return( res );
	}


	@Override
	public ShareManager
	getShareManager()

		throws ShareException
	{
		notSupported();

		return( null );
	}

	@Override
	public Utilities
	getUtilities()
	{
		notSupported();

		return( null );
	}

	@Override
	public ShortCuts
	getShortCuts()
	{
		RPShortCuts	res = (RPShortCuts)_dispatcher.dispatch( new RPRequest( this, "getShortCuts", null )).getResponse();

		res._setRemote( _dispatcher );

		return( res );
	}

	 @Override
	 public UIManager
	 getUIManager()
	 {
		notSupported();

		return( null );
	 }

	 @Override
	 public TorrentManager
	 getTorrentManager()
	 {
		RPTorrentManager	res = (RPTorrentManager)_dispatcher.dispatch( new RPRequest( this, "getTorrentManager", null )).getResponse();

		res._setRemote( _dispatcher );

		return( res );
	 }

	@Override
	public Properties getPluginProperties()
	{
		return((Properties)_dispatcher.dispatch( new RPRequest( this, "getPluginProperties", null )).getResponse());
	}

	@Override
	public String getPluginDirectoryName()
	{
		notSupported();

		return( null );
	}

	@Override
	public String getPerUserPluginDirectoryName()
	{
		notSupported();

		return( null );
	}

	@Override
    public String getPluginName()
	{
		notSupported();

		return( null );
	}

    @Override
    public String getPluginID()
	{
		notSupported();

		return( null );
	}

	public boolean
	isSigned()
	{
		notSupported();

		return( false );
	}

	@Override
    public String getPluginVersion()
	{
		notSupported();

		return( null );
	}

	@Override
	public PluginConfig getPluginconfig()
	{
		RPPluginConfig	res = (RPPluginConfig)_dispatcher.dispatch( new RPRequest( this, "getPluginconfig", null )).getResponse();

		res._setRemote( _dispatcher );

		return( res );
	}

	@Override
	public ClassLoader
	getPluginClassLoader()
	{
		notSupported();

		return( null );
	}

	@Override
	public PluginInterface
	getLocalPluginInterface(
		Class		plugin,
		String		id )
	{
		notSupported();

		return( null );
	}

	@Override
	public IPCInterface
	getIPC ()
	{
		notSupported();

		return( null );
	}

	@Override
	public UpdateManager
	getUpdateManager()
	{
		notSupported();

		return( null );
	}


	@Override
	public boolean
	isInitialisationThread()
	{
		notSupported();

		return( false );
	}

	 @Override
	 public ClientIDManager
	 getClientIDManager()
	 {
	 	notSupported();

	 	return( null );
	 }


   @Override
   public ConnectionManager getConnectionManager() {
     notSupported();
     return null;
   }

   @Override
   public MessageManager getMessageManager() {
     notSupported();
     return null;
   }


   @Override
   public DistributedDatabase
   getDistributedDatabase()
   {
    notSupported();
    return null;
   }
   @Override
   public PlatformManager
   getPlatformManager()
   {
    notSupported();
    return null;
   }

	@Override
	public void
	addListener(
			PluginListener l )
	{
		notSupported();
	}

	@Override
	public void
	removeListener(
			PluginListener	l )
	{
		notSupported();
	}

	@Override
	public void
	firePluginEvent(
		PluginEvent		event )
	{
	  notSupported();
	}

	@Override
	public void
	addEventListener(
		PluginEventListener	l )
	{
		notSupported();
	}

	@Override
	public void
	removeEventListener(
		PluginEventListener	l )
	{
		notSupported();
	}

	@Override
	public MainlineDHTManager getMainlineDHTManager() {notSupported(); return null;}
	@Override
	public PluginState getPluginState() {notSupported(); return null;}

}
