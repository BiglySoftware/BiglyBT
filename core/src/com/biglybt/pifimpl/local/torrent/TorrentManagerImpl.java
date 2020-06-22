/*
 * File    : TorrentManagerImpl.java
 * Created : 28-Feb-2004
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

package com.biglybt.pifimpl.local.torrent;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.URL;
import java.util.*;

import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.LocaleUtilEncodingException;
import com.biglybt.core.torrent.*;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.tag.Tag;
import com.biglybt.pif.torrent.*;
import com.biglybt.pifimpl.local.PluginCoreUtils;

public class
TorrentManagerImpl
	implements TorrentManager, TOTorrentProgressListener
{
	private static TorrentManagerImpl	singleton;
	private static AEMonitor 			class_mon 	= new AEMonitor( "TorrentManager" );

	private static TorrentAttribute	category_attribute = new TorrentAttributeCategoryImpl();
	private static TorrentAttribute	share_properties_attribute = new TorrentAttributeSharePropertiesImpl();
	private static TorrentAttribute	networks_attribute = new TorrentAttributeNetworksImpl();
	private static TorrentAttribute	peer_sources_attribute = new TorrentAttributePeerSourcesImpl();
	private static TorrentAttribute	tr_ext_attribute = new TorrentAttributeTrackerClientExtImpl();
	private static TorrentAttribute disp_name_attribute = new TorrentAttributeDisplayNameImpl();
	private static TorrentAttribute comment_attribute = new TorrentAttributeUserCommentImpl();
	private static TorrentAttribute relative_save_path_attribute = new TorrentAttributeRelativeSavePathImpl();

	private static Map<String,TorrentAttribute>	attribute_map = new HashMap<>();

	static{
		attribute_map.put( TorrentAttribute.TA_CATEGORY, 				category_attribute );
		attribute_map.put( TorrentAttribute.TA_SHARE_PROPERTIES, 		share_properties_attribute );
		attribute_map.put( TorrentAttribute.TA_NETWORKS, 				networks_attribute );
		attribute_map.put( TorrentAttribute.TA_PEER_SOURCES, 			peer_sources_attribute );
		attribute_map.put( TorrentAttribute.TA_TRACKER_CLIENT_EXTENSIONS, tr_ext_attribute );
		attribute_map.put( TorrentAttribute.TA_DISPLAY_NAME,            disp_name_attribute );
		attribute_map.put( TorrentAttribute.TA_USER_COMMENT,            comment_attribute);
		attribute_map.put( TorrentAttribute.TA_RELATIVE_SAVE_PATH,      relative_save_path_attribute);
	}

	public static TorrentManagerImpl
	getSingleton()
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

					// default singleton not attached to a plugin

				singleton = new TorrentManagerImpl(null);
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	protected static CopyOnWriteList<TorrentManagerListener>		listeners = new CopyOnWriteList<>();

	protected PluginInterface	plugin_interface;

	protected
	TorrentManagerImpl(
		PluginInterface		_pi )
	{
		plugin_interface	= _pi;
	}

	public TorrentManager
	specialise(
		PluginInterface		_pi )
	{
			// specialised one attached to plugin

		return( new TorrentManagerImpl( _pi ));
	}

	@Override
	public TorrentDownloader
	getURLDownloader(
		URL		url )

		throws TorrentException
	{
		return( new TorrentDownloaderImpl( this, url ));
	}

	@Override
	public TorrentDownloader
	getURLDownloader(
		URL		url,
		String	user_name,
		String	password )

		throws TorrentException
	{
		return( new TorrentDownloaderImpl( this, url, user_name, password ));
	}

	@Override
	public Torrent
	createFromBEncodedFile(
		File		file )

		throws TorrentException
	{
		return( createFromBEncodedFile( file, false ));
	}

	@Override
	public Torrent
	createFromBEncodedFile(
		File		file,
		boolean		for_seeding )

		throws TorrentException
	{
		try{
			TOTorrent	torrent;

			if ( for_seeding ){

				torrent = TorrentUtils.readFromFile( file, true, true );

			}else{

				torrent = TorrentUtils.readFromFile( file, false );
			}

			return( new TorrentImpl(plugin_interface,torrent));

		}catch( TOTorrentException e ){

			throw( new TorrentException( "TorrentManager::createFromBEncodedFile Fails", e ));
		}
	}

	@Override
	public Torrent
	createFromBEncodedInputStream(
		InputStream		data )

		throws TorrentException
	{
		try{
			return( new TorrentImpl(plugin_interface,TorrentUtils.readFromBEncodedInputStream( data )));

		}catch( TOTorrentException e ){

			throw( new TorrentException( "TorrentManager::createFromBEncodedFile Fails", e ));
		}
	}

	@Override
	public Torrent
	createFromBEncodedData(
		byte[]		data )

		throws TorrentException
	{
		ByteArrayInputStream	is = null;

		try{
			is = new ByteArrayInputStream( data );

			return( new TorrentImpl(plugin_interface,TorrentUtils.readFromBEncodedInputStream(is)));

		}catch( TOTorrentException e ){

			throw( new TorrentException( "TorrentManager::createFromBEncodedData Fails", e ));

		}finally{

			try{
				is.close();

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	@Override
	public Torrent
	createFromDataFile(
		File		data,
		URL			announce_url )

		throws TorrentException
	{
		return( createFromDataFile( data, announce_url, false ));
	}

	@Override
	public Torrent
	createFromDataFile(
		File		data,
		URL			announce_url,
		boolean		include_other_hashes )

		throws TorrentException
	{
		try{
			TOTorrentCreator c = TOTorrentFactory.createFromFileOrDirWithComputedPieceLength( TOTorrent.TT_V1, data, announce_url, include_other_hashes);

			c.addListener( this );

			return( new TorrentImpl(plugin_interface,c.create()));

		}catch( TOTorrentException e ){

			throw( new TorrentException( "TorrentManager::createFromDataFile Fails", e ));
		}
	}

	@Override
	public TorrentCreator
	createFromDataFileEx(
		File					data,
		URL						announce_url,
		boolean					include_other_hashes )

		throws TorrentException
	{
		try{
			final TOTorrentCreator c = TOTorrentFactory.createFromFileOrDirWithComputedPieceLength( TOTorrent.TT_V1, data, announce_url, include_other_hashes);

			return(
				new TorrentCreator()
				{
					private CopyOnWriteList<TorrentCreatorListener>	listeners = new CopyOnWriteList<>();

					@Override
					public void
					start()
					{
						c.addListener(
							new TOTorrentProgressListener()
							{
								@Override
								public void
								reportProgress(
									int		percent_complete )
								{
									for (Iterator<TorrentCreatorListener> it=listeners.iterator();it.hasNext();){

										it.next().reportPercentageDone( percent_complete );
									}
								}

								@Override
								public void
								reportCurrentTask(
									String	task_description )
								{
									for (Iterator<TorrentCreatorListener> it=listeners.iterator();it.hasNext();){

										it.next().reportActivity( task_description );
									}
								}
							});

						new AEThread2( "TorrentManager::create" )
						{
							@Override
							public void
							run()
							{
								try{
									TOTorrent	t = c.create();

									Torrent	torrent = new TorrentImpl( plugin_interface, t );

									for (Iterator<TorrentCreatorListener> it=listeners.iterator();it.hasNext();){

										it.next().complete( torrent );
									}

								}catch( TOTorrentException e ){

									for (Iterator<TorrentCreatorListener> it=listeners.iterator();it.hasNext();){

										it.next().failed( new TorrentException( e ));
									}

								}
							}
						}.start();
					}

					@Override
					public void
					cancel()
					{
						c.cancel();
					}

					@Override
					public void
					addListener(
						TorrentCreatorListener listener )
					{
						listeners.add( listener );
					}

					@Override
					public void
					removeListener(
						TorrentCreatorListener listener )
					{
						listeners.remove( listener );
					}
				});

		}catch( TOTorrentException e ){

			throw( new TorrentException( "TorrentManager::createFromDataFile Fails", e ));
		}
	}

	@Override
	public TorrentAttribute[]
	getDefinedAttributes()
	{
		try{
			class_mon.enter();

			Collection<TorrentAttribute>	entries = attribute_map.values();

			TorrentAttribute[]	res = new TorrentAttribute[entries.size()];

			entries.toArray( res );

			return( res );

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public TorrentAttribute
	getAttribute(
		String		name )
	{
		try{
			class_mon.enter();

			TorrentAttribute	res = (TorrentAttribute)attribute_map.get(name);

			if ( res == null && name.startsWith( "Plugin." )){

				res = new TorrentAttributePluginImpl( name );

				attribute_map.put( name, res );
			}

			if (res == null) {throw new IllegalArgumentException("No such attribute: \"" + name + "\"");}
			return( res );

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public TorrentAttribute
	getPluginAttribute(
		String		name )
	{
			// this prefix is RELIED ON ELSEWHERE!!!!

		name	= "Plugin." + plugin_interface.getPluginID() + "." + name;

		try{
			class_mon.enter();

			TorrentAttribute	res = (TorrentAttribute)attribute_map.get(name);

			if ( res != null ){

				return( res );
			}

			res = new TorrentAttributePluginImpl( name );

			attribute_map.put( name, res );

			return( res );

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public Torrent
	createFromBEncodedData(
			byte[] data,
			int preserve)

			throws TorrentException
	{
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		try {
			TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedInputStream( bais );
			return new TorrentImpl(plugin_interface, preserveFields(torrent,preserve));
		} catch (TOTorrentException e) {
			throw new TorrentException ("Failed to read TorrentData", e);
		} finally {
			try {
				bais.close();
			} catch (IOException e) {}
		}
	}


	@Override
	public Torrent
	createFromBEncodedFile(
			File file,
			int preserve)

			throws TorrentException
	{
		FileInputStream fis = null;
		try {
			fis = FileUtil.newFileInputStream (file);
			TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedInputStream( fis );
			return new TorrentImpl(plugin_interface, preserveFields(torrent,preserve));
		} catch (FileNotFoundException e) {
			throw new TorrentException ("Failed to read from TorrentFile", e);
		} catch (TOTorrentException e) {
			throw new TorrentException ("Failed to read TorrentData", e);
		} finally {
			if (fis != null)
				try {
					fis.close();
				} catch (IOException e) {}
		}
	}


	@Override
	public Torrent createFromBEncodedInputStream(InputStream data, int preserve) throws TorrentException
	{
		try {
			TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedInputStream( data );
			return new TorrentImpl(plugin_interface, preserveFields(torrent,preserve));
		} catch (TOTorrentException e) {
			throw new TorrentException ("Failed to read TorrentData", e);
		}

	}

	private TOTorrent
	preserveFields (
			TOTorrent torrent,
			int preserve)
	{
		if (preserve == TorrentManager.PRESERVE_ALL) {
			return torrent;
		} else if ((preserve & TorrentManager.PRESERVE_ENCODING) > 0) {
			String encoding = torrent.getAdditionalStringProperty("encoding");
			torrent.removeAdditionalProperties();
			if (encoding != null)
				torrent.setAdditionalStringProperty("encoding", encoding);
		} else if (preserve == TorrentManager.PRESERVE_NONE) {
			torrent.removeAdditionalProperties();
		}
		return torrent;
	}

	@Override
	public void
	reportProgress(
		int		percent_complete )
	{
	}

	@Override
	public void
	reportCurrentTask(
		final String	task_description )
	{
		for (Iterator<TorrentManagerListener> it = listeners.iterator();it.hasNext();){

			it.next().event(
					new TorrentManagerEvent()
					{
						@Override
						public int
						getType()
						{
							return( ET_CREATION_STATUS );
						}

						@Override
						public Object
						getData()
						{
							return( task_description );
						}
					});
		}
	}

	protected void
	tryToSetTorrentEncoding(
		TOTorrent	torrent,
		String		encoding )

		throws TorrentEncodingException
	{
		try{
			LocaleTorrentUtil.setTorrentEncoding( torrent, encoding );

		}catch( LocaleUtilEncodingException e ){

			String[]	charsets = e.getValidCharsets();

			if ( charsets == null ){

				throw( new TorrentEncodingException("Failed to set requested encoding", e));

			}else{

				throw( new TorrentEncodingException(charsets,e.getValidTorrentNames()));
			}
		}
	}

	protected void
	tryToSetDefaultTorrentEncoding(
		TOTorrent		torrent )

		throws TorrentException
	{
		try{
			LocaleTorrentUtil.setDefaultTorrentEncoding( torrent );

		}catch( LocaleUtilEncodingException e ){

			String[]	charsets = e.getValidCharsets();

			if ( charsets == null ){

				throw( new TorrentEncodingException("Failed to set default encoding", e));

			}else{

				throw( new TorrentEncodingException(charsets,e.getValidTorrentNames()));
			}
		}
	}

	private Map<TorrentOpenOptions,TorrentOptionsImpl>	too_state = new HashMap<>();

	private void
	fireEvent(
		final int		type,
		final Object	data )
	{
		TorrentManagerEvent	ev =
			new TorrentManagerEvent() {

				@Override
				public int getType() {
					return( type );
				}

				@Override
				public Object getData() {
					return( data );
				}
			};

		for ( TorrentManagerListener l: listeners ){

			try{
				l.event( ev );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	private static class
	TorrentOptionsImpl
		implements TorrentOptions
	{
		private	TorrentOpenOptions		options;

		private
		TorrentOptionsImpl(
			TorrentOpenOptions		_options )
		{
			options	= _options;
		}

		@Override
		public Torrent
		getTorrent()
		{
			return( PluginCoreUtils.wrap( options.getTorrent()));
		}

		@Override
		public void
		accept()
		{
			options.setCompleteAction( TorrentOpenOptions.CA_ACCEPT );
		}

		@Override
		public void
		cancel()
		{
			options.setCompleteAction( TorrentOpenOptions.CA_REJECT );
		}

		@Override
		public List<Tag>
		getTags()
		{
			List<Tag> tags = new ArrayList<Tag>( options.getInitialTags());

			return( tags );
		}

		@Override
		public void
		addTag(
			Tag		tag )
		{
			List<com.biglybt.core.tag.Tag> tags = options.getInitialTags();

			if ( !tags.contains( tag )){

				tags.add((com.biglybt.core.tag.Tag)tag );

				options.setInitialTags(tags);

				options.setDirty();
			}
		}

		@Override
		public void
		removeTag(
			Tag		tag )
		{
			List<com.biglybt.core.tag.Tag> tags = options.getInitialTags();

			if ( tags.contains( tag )){

				tags.remove((com.biglybt.core.tag.Tag)tag );

				options.setInitialTags(tags);

				options.setDirty();
			}
		}
	}

	public void
	optionsAdded(
		TorrentOpenOptions	options )
	{
		TorrentOptionsImpl my_options =  new TorrentOptionsImpl( options );

		synchronized( too_state ){

			too_state.put( options, my_options );

			fireEvent( TorrentManagerEvent.ET_TORRENT_OPTIONS_CREATED, my_options );
		}
	}

	public void
	optionsAccepted(
		TorrentOpenOptions	options )
	{
		synchronized( too_state ){

			TorrentOptionsImpl my_options = too_state.remove( options );

			if ( my_options != null ){

				fireEvent( TorrentManagerEvent.ET_TORRENT_OPTIONS_ACCEPTED, my_options );
			}
		}
	}

	public void
	optionsRemoved(
		TorrentOpenOptions	options )
	{
		synchronized( too_state ){

			TorrentOptionsImpl my_options = too_state.remove( options );

			if ( my_options != null ){

				fireEvent( TorrentManagerEvent.ET_TORRENT_OPTIONS_CANCELLED, my_options );
			}
		}
	}

	@Override
	public void
	addListener(
		TorrentManagerListener	l )
	{
		listeners.add( l );
	}

	@Override
	public void
	removeListener(
		TorrentManagerListener	l )
	{
		listeners.remove( l );
	}
}
