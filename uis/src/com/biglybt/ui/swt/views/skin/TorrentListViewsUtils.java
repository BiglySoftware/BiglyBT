/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.skin;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.ui.swt.player.PlayerInstallWindow;
import com.biglybt.ui.swt.player.PlayerInstaller;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;

import com.biglybt.activities.ActivitiesEntry;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.*;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.swt.utils.TorrentUIUtilsV3;
import com.biglybt.util.DLReferals;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.PlayUtils;

/**
 * @author TuxPaper
 * @created Oct 12, 2006
 *
 */
public class TorrentListViewsUtils
{
	private static StreamManagerDownload	current_stream;
	private static TextViewerWindow			stream_viewer;

	public static void playOrStreamDataSource(Object ds,
			boolean launch_already_checked) {
		String referal = DLReferals.DL_REFERAL_UNKNOWN;
		if (ds instanceof ActivitiesEntry) {
			referal = DLReferals.DL_REFERAL_PLAYDASHACTIVITY;
		} else if (ds instanceof DownloadManager) {
			referal = DLReferals.DL_REFERAL_PLAYDM;
		} else if (ds instanceof ISelectedContent) {
			referal = DLReferals.DL_REFERAL_SELCONTENT;
		}
		playOrStreamDataSource(ds, referal, launch_already_checked, true );
	}

	public static void playOrStreamDataSource(Object ds, String referal,
			boolean launch_already_checked, boolean complete_only) {

		DiskManagerFileInfo fileInfo = DataSourceUtils.getFileInfo(ds);
		if (fileInfo != null) {
			playOrStream(fileInfo.getDownloadManager(), fileInfo.getIndex(),
					complete_only, launch_already_checked, referal);
		}else{

			DownloadManager dm = DataSourceUtils.getDM(ds);
			if (dm == null) {
				downloadDataSource(ds, true, referal);
			} else {
				playOrStream(dm, -1, complete_only, launch_already_checked, referal);
			}
		}
	}

	public static void downloadDataSource(Object ds, boolean playNow,
			String referal) {
		TOTorrent torrent = DataSourceUtils.getTorrent(ds);

		if ( torrent != null ){
				// handle encapsulated vuze file
			try{
				Map torrent_map = torrent.serialiseToMap();

				torrent_map.remove( "info" );

				VuzeFile vf = VuzeFileHandler.getSingleton().loadVuzeFile( torrent_map );

				if ( vf != null ){

					VuzeFileHandler.getSingleton().handleFiles( new VuzeFile[]{ vf }, VuzeFileComponent.COMP_TYPE_NONE );

					return;
				}
			}catch( Throwable e ){

			}
		}

		// we want to re-download the torrent if it's ours, since the existing
		// one is likely stale
		if (torrent != null) {
			TorrentUIUtilsV3.addTorrentToGM(torrent);
		} else {
			DownloadUrlInfo dlInfo = DataSourceUtils.getDownloadInfo(ds);
			if (dlInfo != null) {
				TorrentUIUtilsV3.loadTorrent(dlInfo, playNow, false, true);
				return;
			}

			String hash = DataSourceUtils.getHash(ds);
			if (hash != null) {
				dlInfo = new DownloadUrlInfo(UrlUtils.parseTextForMagnets(hash));
				dlInfo.setReferer(referal);
				TorrentUIUtilsV3.loadTorrent(dlInfo, playNow, false, true);
				return;
			}
		}
	}

	// VuzePlayer (2011) calls this
	public static void playOrStream(final DownloadManager dm,
			final int file_index, final boolean complete_only,
			boolean launch_already_checked) {
		playOrStream(dm, file_index, complete_only, launch_already_checked, null);
	}


	private static void playOrStream(final DownloadManager dm,
			final int file_index, final boolean complete_only,
			boolean launch_already_checked, final String referal) {

		if (dm == null) {
			return;
		}

		if ( launch_already_checked ){

			_playOrStream(dm, file_index, complete_only, referal);

		}else{

			LaunchManager	launch_manager = LaunchManager.getManager();

			LaunchManager.LaunchTarget target = launch_manager.createTarget( dm );

			launch_manager.launchRequest(
				target,
				new LaunchManager.LaunchAction()
				{
					@Override
					public void
					actionAllowed()
					{
						Utils.execSWTThread(
							new Runnable()
							{
								@Override
								public void
								run()
								{
									_playOrStream(dm, file_index, complete_only, referal);
								}
							});
					}

					@Override
					public void
					actionDenied(
						Throwable		reason )
					{
						Debug.out( "Launch request denied", reason );
					}
				});
		}
	}

	private static void _playOrStream(final DownloadManager dm,
			final int file_index, final boolean complete_only, final String referal) {

		if (dm == null) {
			return;
		}

		//		if (!canPlay(dm)) {
		//			return false;
		//		}

		final TOTorrent torrent = dm.getTorrent();
		if (torrent == null) {
			return;
		}

		if (file_index == -1) {
			final int[] playableFileIndexes = PlayUtils.getExternallyPlayableFileIndexes(
					PluginCoreUtils.wrap(dm), complete_only);
			if (playableFileIndexes.length == 1) {

				int open_result = openInEMP(dm,file_index,complete_only,referal);

				if ( open_result == 0 ){
					PlatformTorrentUtils.setHasBeenOpened(dm, true);
				}
			} else if (playableFileIndexes.length > 1) {
				VuzeMessageBox mb = new VuzeMessageBox(MessageText.getString("ConfigView.option.dm.dblclick.play"), null,
						new String[] {
							MessageText.getString("iconBar.play"),
							MessageText.getString("Button.cancel")
						}, 0);
				final Map<Integer, Integer> mapPositionToFileInfo = new HashMap<>();
				final int[] selectedIndex = {
					0		// default selection is first entry
				};

				mb.setSubTitle(MessageText.getString("play.select.content"));
				mb.setListener(new VuzeMessageBoxListener() {

					@Override
					public void shellReady(Shell shell, SWTSkinObjectContainer soExtra) {
						SWTSkin skin = soExtra.getSkin();

						Composite composite = soExtra.getComposite();
						final Table table = new Table(composite, SWT.V_SCROLL
								| SWT.H_SCROLL | SWT.FULL_SELECTION | SWT.SINGLE);
						table.setBackground(composite.getBackground());
						table.addSelectionListener(new SelectionListener() {

							@Override
							public void widgetSelected(SelectionEvent e) {
								selectedIndex[0] = table.getSelectionIndex();
							}

							@Override
							public void widgetDefaultSelected(SelectionEvent e) {
							}
						});
						FormData formData = Utils.getFilledFormData();
						formData.bottom.offset = -20;
						table.setLayoutData(formData);
						table.setHeaderVisible(false);
						table.addListener(SWT.MeasureItem, new Listener() {
							@Override
							public void handleEvent(Event event) {
								int w = table.getClientArea().width - 5;
								if (w == 0) {
									return;
								}
								if (event.width < w) {
									event.width = w;
								}
							}
						});

						String prefix = dm.getSaveLocation().toString();
						int i = 0;
						DiskManagerFileInfo[] fileInfos = dm.getDiskManagerFileInfoSet().getFiles();
						for (int fileIndex : playableFileIndexes) {
							if (fileIndex < 0 || fileIndex >= fileInfos.length) {
								continue;
							}
							File f = fileInfos[fileIndex].getFile(true);
							String path = f.getParent();
							if (path.startsWith(prefix)) {
								path = path.length() > prefix.length() ? path.substring(prefix.length() + 1) : "";
							}
							String s = f.getName();
							if (path.length() > 0) {
								s += " in " + path;
							}
							TableItem item = new TableItem(table, SWT.NONE);
							item.setText(s);
							mapPositionToFileInfo.put(i++, fileIndex);
						}

						Image alphaImage = Utils.createAlphaImage(table.getDisplay(), 1, 25, (byte) 255);
						TableItem item = table.getItem(0);
						item.setImage(alphaImage);
						item.setImage((Image) null);
						alphaImage.dispose();


						table.setSelection(0);
					}
				});
				mb.open(new UserPrompterResultListener() {
					@Override
					public void prompterClosed(int result) {
						if (result != 0 || selectedIndex[0] < 0) {
							return;
						}
						Integer file_index = mapPositionToFileInfo.get(selectedIndex[0]);

						if (file_index != null) {
							int open_result = openInEMP(dm,file_index,complete_only,referal);

							if ( open_result == 0 ){
								PlatformTorrentUtils.setHasBeenOpened(dm, file_index, true);
							}
						}
					}
				});
			}
			return;
		}

		if (PlayUtils.canUseEMP(torrent, file_index,complete_only)) {
			debug("Can use EMP");

			int open_result = openInEMP(dm,file_index,complete_only,referal);

			if ( open_result == 0 ){
				PlatformTorrentUtils.setHasBeenOpened(dm, file_index, true);
				return;
			}else if ( open_result == 2 ){
				debug( "Open in EMP abandoned" );
				return;
			} else {
				debug("Open EMP Failed");
			}
			// fallback to normal
		} else {
			debug("Can't use EMP.");
		}

		// We used to pop up a dialog saying we didn't know how to play the file
		// But now the play toolbar and play 'default' action aren't even enabled
		// if we can't use EMP.  So there's no point.
	}


	/**
	 * @param string
	 *
	 * @since 3.0.3.3
	 */
	private static void debug(String string) {
		if (com.biglybt.core.util.Constants.isCVSVersion()) {
			System.out.println(string);
		}
	}


	private static boolean	emp_installing;

	/**
	 *
	 * @return 0=good, 1 = fail, 2 = abandon
	 */

	private static int
	installEMP(
		String				name,
		final Runnable		target )
	{
		synchronized( TorrentListViewsUtils.class ){

			if ( emp_installing ){

				Debug.out( "EMP is already being installed, secondary launch for " + name + " ignored" );

				return( 2 );
			}

			emp_installing = true;
		}

		boolean	running = false;

		try{
			final PlayerInstaller installer = new PlayerInstaller();

			final PlayerInstallWindow window = new PlayerInstallWindow(installer);

			window.open();

			AEThread2 installerThread = new AEThread2("player installer",true) {
				@Override
				public void
				run()
				{
					try{
						if (installer.install()){
							Utils.execSWTThread(new AERunnable() {

								@Override
								public void runSupport() {
									target.run();

								}
							});
						}
					}finally{

						synchronized( TorrentListViewsUtils.class ){

							emp_installing = false;
						}
					}
				}
			};

			installerThread.start();

			running = true;

			return( 0 );

		}catch( Throwable e ){

			Debug.out( e );

			return( 1 );

		}finally{

			if ( !running ){

				synchronized( TorrentListViewsUtils.class ){

					emp_installing = false;
				}
			}
		}

	}
	/**
	 * New version accepts map with ASX parameters. If the params are null then is uses the
	 * old version to start the player. If the
	 *
	 *
	 * @param dm - DownloadManager
	 * @return - int: 0 = ok, 1 = fail, 2 = abandon, installation in progress
	 * @since 3.0.4.4 -
	 */
	private static int
	openInEMP(
		final DownloadManager dm, final int _file_index,
		final boolean complete_only, final String referal )
	{
		try {
			int file_index = -1;

			if ( _file_index == -1 ){

				EnhancedDownloadManager edm = DownloadManagerEnhancer.getSingleton().getEnhancedDownload( dm );

				if (edm != null) {
					file_index = edm.getPrimaryFileIndex();
				}

			}else{

				file_index = _file_index;
			}

			if ( file_index == -1 ){

				return( 1 );
			}

			final int f_file_index = file_index;

			com.biglybt.pif.disk.DiskManagerFileInfo file = PluginCoreUtils.wrap( dm ).getDiskManagerFileInfo()[ file_index ];

			final URL url;

			if ((! complete_only ) && file.getDownloaded() != file.getLength()){

				url = PlayUtils.getMediaServerContentURL( file );

			}else{

				url = null;
			}

			if ( url != null ){

				if ( PlayUtils.isStreamPermitted()){

					final boolean show_debug_window = false;

					new AEThread2( "stream:async" )
					{
						@Override
						public void
						run()
						{
							StreamManager	sm = StreamManager.getSingleton();

							synchronized( TorrentListViewsUtils.class ){

								if ( current_stream != null && !current_stream.isCancelled()){

									if ( current_stream.getURL().equals( url )){

										current_stream.setPreviewMode( !current_stream.getPreviewMode());

										return;
									}

									current_stream.cancel();

									current_stream = null;
								}

								if ( show_debug_window && ( stream_viewer == null || stream_viewer.isDisposed())){

									Utils.execSWTThread(
										new Runnable()
										{
											@Override
											public void
											run()
											{
												if ( stream_viewer != null ){

													stream_viewer.close();
												}

												stream_viewer = new
													TextViewerWindow( "Stream Status", "Debug information for stream process", "", false );

												stream_viewer.addListener(
													new TextViewerWindow.TextViewerWindowListener()
													{
														@Override
														public void
														closed()
														{
															synchronized( TorrentListViewsUtils.class ){

																if ( current_stream != null ){

																	current_stream.cancel();

																	current_stream = null;
																}
															}
														}
													});
											}
										});
								}

								current_stream =
									sm.stream(
										dm, f_file_index, url, false,
										new StreamManagerDownloadListener()
										{
											private long	last_log = 0;

											@Override
											public void
											updateActivity(
												String		str )
											{
												append( "Activity: " + str );
											}

											@Override
											public void
											updateStats(
												int			secs_until_playable,
												int			buffer_secs,
												long		buffer_bytes,
												int			target_secs )
											{
												long	now = SystemTime.getMonotonousTime();

												if ( now - last_log >= 1000 ){

													last_log = now;

													append( "stats: play in " + secs_until_playable + " sec, buffer=" + DisplayFormatters.formatByteCountToKiBEtc( buffer_bytes ) + "/" + buffer_secs + " sec - target=" + target_secs + " sec" );
												}
											}

											@Override
											public void
											ready()
											{
												append( "ready" );
											}

											@Override
											public void
											failed(
												Throwable 	error )
											{
												append( "failed: " + Debug.getNestedExceptionMessage(error));

												Debug.out( error );
											}

											private void
											append(
												final String	str )
											{
												if ( stream_viewer != null ){

													Utils.execSWTThread(
														new Runnable()
														{
															@Override
															public void
															run()
															{
																if ( stream_viewer != null && !stream_viewer.isDisposed()){

																	stream_viewer.append( str + "\r\n" );
																}
															}
														});
												}
											}
										});
								}
							}
						}.start();

				}else{

					//FeatureManagerUI.openStreamPlusWindow(referal);

				}

				return( 0 );

			}

			synchronized( TorrentListViewsUtils.class ){

				if ( current_stream != null && !current_stream.isCancelled()){

					current_stream.cancel();

					current_stream = null;
				}
			}

			Class epwClass = null;

			try {
				// Assumed we have a core, since we are passed a
				// DownloadManager
				PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(
						"azemp", false );

				if (pi == null) {

					return (installEMP(dm.getDisplayName(), new Runnable(){ @Override
					public void run(){ openInEMP( dm, f_file_index,complete_only,referal ); }}));

				}else if ( !pi.getPluginState().isOperational()){

					return( 1 );
				}

				epwClass = pi.getPlugin().getClass().getClassLoader().loadClass(
						"com.azureus.plugins.azemp.ui.swt.emp.EmbeddedPlayerWindowSWT");

			} catch (ClassNotFoundException e1) {
				return 1;
			}


			try{
				Method method = epwClass.getMethod("openWindow", new Class[] {
						File.class, String.class
					});

				File f = file.getFile( true );

				method.invoke(null, new Object[] {
						f, f.getName()
					});

				return( 0 );

			}catch( Throwable e ){
				debug( "file/name open method missing" );
			}


				// fall through here if old emp

			Method method = epwClass.getMethod("openWindow", new Class[] {
				DownloadManager.class
			});

			method.invoke(null, new Object[] {
				dm
			});

			return 0;
		} catch (Throwable e) {
			e.printStackTrace();
			if (e.getMessage() == null
					|| !e.getMessage().toLowerCase().endsWith("only")) {
				Debug.out(e);
			}
		}

		return 1;
	}//openInEMP

	public static int
	openInEMP(
		final String	name,
		final URL		url )
	{
		Class epwClass = null;

		try {

			PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(
					"azemp", false );

			if ( pi == null ){

				return( installEMP( name, new Runnable(){ @Override
				public void run(){ openInEMP( name, url );}} ));

			}else if ( !pi.getPluginState().isOperational()){

				return( 1 );
			}

			epwClass = pi.getPlugin().getClass().getClassLoader().loadClass(
					"com.azureus.plugins.azemp.ui.swt.emp.EmbeddedPlayerWindowSWT");

		} catch (ClassNotFoundException e1) {
			return 1;
		}


		try{
			Method method = epwClass.getMethod("openWindow", new Class[] {
					URL.class, String.class
				});

			method.invoke(null, new Object[] {url, name	});

			return( 0 );

		}catch( Throwable e ){
			debug( "URL/name open method missing" );

			return( 1 );
		}
	}

	/**
	 * Plays or Streams a Download
	 *
	 * @param dm
	 * @param file_index Index of file in torrent to play.  -1 to auto-pick
	 * 				"best" play file.
	 */
	public static void playOrStream(final DownloadManager dm, int file_index ) {
		playOrStream(dm, file_index, PlayUtils.COMPLETE_PLAY_ONLY, false, null );
	}
}
