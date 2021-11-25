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

package com.biglybt.ui.swt.utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.Core;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.swt.*;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.PlayUtils;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.RememberedDecisionsManager;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.views.skin.TorrentListViewsUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.torrentdownloader.TorrentDownloader;
import com.biglybt.core.torrentdownloader.TorrentDownloaderCallBackInterface;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.shells.MessageSlideShell;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;
import com.biglybt.ui.swt.browser.listener.DownloadUrlInfoSWT;

/**
 * @author TuxPaper
 * @created Sep 16, 2007
 *
 */
public class TorrentUIUtilsV3
{
	private final static String MSG_ALREADY_EXISTS = "OpenTorrentWindow.mb.alreadyExists";

	private final static String MSG_ALREADY_EXISTS_NAME = MSG_ALREADY_EXISTS + ".default.name";

	private static final Pattern hashPattern = Pattern.compile("download/([A-Z0-9]{32})\\.torrent");

	static ImageLoader imageLoaderThumb;

	public static void disposeStatic() {
		if (imageLoaderThumb != null) {
			imageLoaderThumb.dispose();
			imageLoaderThumb = null;
		}
	}

	public static void loadTorrent(	final DownloadUrlInfo dlInfo,
			final boolean playNow, // open player
			final boolean playPrepare, // as for open player but don't actually open it
			final boolean bringToFront) {
		CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				_loadTorrent(core, dlInfo, playNow, playPrepare, bringToFront);
			}
		});
	}

	private static void _loadTorrent(final Core core,
			final DownloadUrlInfo dlInfo,
			final boolean playNow, // open player
			final boolean playPrepare, // as for open player but don't actually open it
			final boolean bringToFront) {
		if (dlInfo instanceof DownloadUrlInfoSWT) {
			DownloadUrlInfoSWT dlInfoSWT = (DownloadUrlInfoSWT) dlInfo;
			dlInfoSWT.invoke(playNow ? "play" : "download");
			return;
		}

		String url = dlInfo.getDownloadURL();
		try {
			Matcher m = hashPattern.matcher(url);
			if (m.find()) {
				String hash = m.group(1);
				GlobalManager gm = core.getGlobalManager();
				final DownloadManager dm = gm.getDownloadManager(new HashWrapper(
						Base32.decode(hash)));
				if (dm != null) {
					if (playNow || playPrepare) {
						new AEThread2("playExisting", true) {

							@Override
							public void run() {
								if (playNow) {
									Debug.outNoStack("loadTorrent already exists.. playing",
											false);

									TorrentListViewsUtils.playOrStream(dm, -1);
								} else {
									Debug.outNoStack("loadTorrent already exists.. preparing",
											false);

									PlayUtils.prepareForPlay(dm);
								}
							}

						}.start();
					} else {
						showTorrentAlreadyAdded(
							" ",
							dm.getDisplayName());
					}
					return;
				}
			}

			UIFunctionsSWT uiFunctions = (UIFunctionsSWT) UIFunctionsManager.getUIFunctions();
			if (uiFunctions != null) {
				//if (!COConfigurationManager.getBooleanParameter("add_torrents_silently")) { not used 11/30/2015
					if (bringToFront) {
						uiFunctions.bringToFront();
					}
				//}

				Shell shell = uiFunctions.getMainShell();
				if (shell != null) {
					new FileDownloadWindow(shell, url, dlInfo.getReferer(),
							dlInfo.getRequestProperties(), null,
							new TorrentDownloaderCallBackInterface() {

								@Override
								public void TorrentDownloaderEvent(int state,
								                                   TorrentDownloader inf) {
									if (state == TorrentDownloader.STATE_FINISHED) {

										File file = inf.getFile();
										file.deleteOnExit();

										// Do a quick check to see if it's a torrent
										if (!TorrentUtil.isFileTorrent(dlInfo.getDownloadURL(), file, file.getName(), true)) {
											return;
										}

										TOTorrent torrent;
										try {
											torrent = TorrentUtils.readFromFile(file, false);
										} catch (TOTorrentException e) {
											Debug.out(e);
											return;
										}
										// Security: Only allow torrents from whitelisted trackers
										if (playNow
												&& !PlatformTorrentUtils.isPlatformTracker(torrent)) {
											Debug.out("stopped loading torrent because it's not in whitelist");
											return;
										}

										HashWrapper hw;
										try {
											hw = torrent.getHashWrapper();
										} catch (TOTorrentException e1) {
											Debug.out(e1);
											return;
										}

										GlobalManager gm = core.getGlobalManager();

										if (playNow || playPrepare) {
											DownloadManager existingDM = gm.getDownloadManager(hw);
											if (existingDM != null) {
												if (playNow) {
													TorrentListViewsUtils.playOrStream(existingDM, -1);
												} else {
													PlayUtils.prepareForPlay(existingDM);
												}
												return;
											}
										}

										final HashWrapper fhw = hw;

										GlobalManagerListener l = new GlobalManagerAdapter() {
											@Override
											public void downloadManagerAdded(DownloadManager dm) {

												try {
													core.getGlobalManager().removeListener(this);

													handleDMAdded(dm, playNow, playPrepare, fhw);
												} catch (Exception e) {
													Debug.out(e);
												}
											}

										};
										gm.addListener(l, false);

										TorrentOpener.openTorrent(file.getAbsolutePath());
									}
								}
							});
				}
			}
		} catch (Exception e) {
			Debug.out(e);
		}
	}

	private static void handleDMAdded(final DownloadManager dm,
			final boolean playNow, final boolean playPrepare, final HashWrapper fhw) {
		new AEThread2("playDM", true) {
			@Override
			public void run() {
				try {
					HashWrapper hw = dm.getTorrent().getHashWrapper();
					if (!hw.equals(fhw)) {
						return;
					}

					if (playNow || playPrepare) {
						if (playNow) {
							TorrentListViewsUtils.playOrStream(dm, -1);
						} else {
							PlayUtils.prepareForPlay(dm);
						}
					}
				} catch (Exception e) {
					Debug.out(e);
				}
			}
		}.start();
	}

	/**
	 * No clue if we have a easy way to add a TOTorrent to the GM, so here it is
	 * @param torrent
	 * @return
	 *
	 * @since 3.0.5.3
	 */
	public static void addTorrentToGM(final TOTorrent torrent) {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {

				File tempTorrentFile;
				try {
					tempTorrentFile = File.createTempFile("AZU", ".torrent");
					tempTorrentFile.deleteOnExit();
					String filename = tempTorrentFile.getAbsolutePath();
					torrent.serialiseToBEncodedFile(tempTorrentFile);

					String savePath = COConfigurationManager.getStringParameter("Default save path");
					if (savePath == null || savePath.length() == 0) {
						savePath = ".";
					}

					core.getGlobalManager().addDownloadManager(filename, savePath);
				} catch (Throwable t) {
					Debug.out(t);
				}
			}
		});
	}

	/**
	 * Retrieves the thumbnail for the content, pulling it from the web if
	 * it can
	 *
	 * @param datasource
	 * @param l When the thumbnail is available, this listener is triggered
	 * @return If the image is immediately available, the image will be returned
	 *         as well as the trigger being fired.  If the image isn't available
	 *         null will be returned and the listener will trigger when avail
	 *
	 * @since 4.0.0.5
	 */
	public static Image[] getContentImage(Object datasource, boolean big,
			final ContentImageLoadedListener l) {
		if (l == null) {
			return null;
		}
		TOTorrent torrent = DataSourceUtils.getTorrent(datasource);
		if (torrent == null) {
			l.contentImageLoaded(null, true);
			return null;
		}

		if (imageLoaderThumb == null) {
			imageLoaderThumb = new ImageLoader(null, null);
		}

		String thumbnailUrl = PlatformTorrentUtils.getContentThumbnailUrl(torrent);

		//System.out.println("thumburl= " + thumbnailUrl);
		if (thumbnailUrl != null && imageLoaderThumb.imageExists(thumbnailUrl)) {
			//System.out.println("return thumburl");
			Image image = imageLoaderThumb.getImage(thumbnailUrl);
			l.contentImageLoaded(image, true);
			return new Image[] { image };
		}

		String hash = null;
		try {
			hash = torrent.getHashWrapper().toBase32String();
		} catch (TOTorrentException e) {
		}
		if (hash == null) {
			l.contentImageLoaded(null, true);
			return null;
		}

		int thumbnailVersion = PlatformTorrentUtils.getContentVersion(torrent);

			// add torrent size here to differentiate meta-data downloads from actuals

		final String id = "Thumbnail." + hash + "." + torrent.getSize() + "." + thumbnailVersion + (big ? ".big" : "");

		Image image = imageLoaderThumb.imageAdded(id) ? imageLoaderThumb.getImage(id) : null;
		//System.out.println("image = " + image);
		if (image != null && !image.isDisposed()) {
			l.contentImageLoaded(image, true);
			return new Image[] { image };
		}

		final byte[] imageBytes = PlatformTorrentUtils.getContentThumbnail(torrent);
		//System.out.println("imageBytes = " + imageBytes);
		if (imageBytes != null) {
			image = (Image) Utils.execSWTThreadWithObject("thumbcreator",
					new AERunnableObject() {
						@Override
						public Object runSupport() {
							try{
								ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
								Image image = new Image(Display.getDefault(), bis);

								return image;

							}catch( Throwable e ){

								return( null );
							}
						}
					}, 500);
		}
/**
		if ((image == null || image.isDisposed()) && thumbnailUrl != null) {
			//System.out.println("get image from " + thumbnailUrl);
			image = imageLoader.getUrlImage(thumbnailUrl,
					new ImageDownloaderListener() {
						public void imageDownloaded(Image image, boolean returnedImmediately) {
							l.contentImageLoaded(image, returnedImmediately);
							//System.out.println("got image from thumburl");
						}
					});
			//System.out.println("returning " + image + " (url loading)");
			return image == null ? null : new Image[] { image };
		}
**/
		if (image == null || image.isDisposed()) {
			//System.out.println("build image from files");
			DownloadManager dm = DataSourceUtils.getDM(datasource);
			/*
			 * Try to get an image from the OS
			 */

			String path = null;
			if (dm == null) {
				TOTorrentFile[] files = torrent.getFiles();
				if (files.length > 0) {
					path = files[0].getRelativePath();
				}
			} else {
				DiskManagerFileInfo primaryFile = dm.getDownloadState().getPrimaryFile();
				path = primaryFile == null ? null : primaryFile.getFile(true).getName();
				if ( path == null ){
					path = dm.getSaveLocation().getAbsolutePath();
				}
			}
			if (path != null) {
				image = ImageRepository.getPathIcon(path, big, false);

				if (image != null && !torrent.isSimpleTorrent()) {
					Image parentPathIcon = ImageRepository.getPathIcon(new File(path).getParent(), false, false);
					Image[] images = parentPathIcon == null || parentPathIcon.isDisposed()
							? new Image[] {
								image
							} : new Image[] {
								image,
								parentPathIcon
							};
					return images;
				}
			}

			if (image == null) {
				imageLoaderThumb.addImageNoDipose(id, ImageLoader.noImage);
			} else {
				imageLoaderThumb.addImageNoDipose(id, image);
			}
		} else {
			//System.out.println("has mystery image");
			imageLoaderThumb.addImage(id, image);
		}

		l.contentImageLoaded(image, true);
		return new Image[] { image };
	}

	public static void releaseContentImage(Object datasource) {
		if (imageLoaderThumb == null) {
			return;
		}

		TOTorrent torrent = DataSourceUtils.getTorrent(datasource);
		if (torrent == null) {
			return;
		}

		String thumbnailUrl = PlatformTorrentUtils.getContentThumbnailUrl(torrent);

		if (thumbnailUrl != null) {
			imageLoaderThumb.releaseImage(thumbnailUrl);
		} else {
			String hash = null;
			try {
				hash = torrent.getHashWrapper().toBase32String();
			} catch (TOTorrentException e) {
			}
			if (hash == null) {
				return;
			}

			String id = "Thumbnail." + hash + "." + torrent.getSize();

			imageLoaderThumb.releaseImage(id);
		}
	}

	public static interface ContentImageLoadedListener
	{
		/**
		 * @param image
		 * @param wasReturned  Image was also returned from getContentImage
		 *
		 * @since 4.0.0.5
		 */
		public void contentImageLoaded(Image image, boolean wasReturned);
	}
	
	private static List<MessageBoxShell>	active_aa_dialogs = new ArrayList<>();
	
	public static void
	showTorrentAlreadyAdded(
		String		originating_loc,
		String		name )
	{
		String remember_id = "OpenTorrentWindow.mb.alreadyExists";
		
		if ( RememberedDecisionsManager.getRememberedDecision( remember_id ) < 0 ){
		
			MessageBoxShell mb = new MessageBoxShell(SWT.OK,
				MSG_ALREADY_EXISTS, 
				new String[] {
					originating_loc,
					name,
					MessageText.getString(MSG_ALREADY_EXISTS_NAME),
				});
			
			mb.setRemember(
				remember_id, false,
				MessageText.getString("MessageBoxWindow.nomoreprompting"));	
			
			synchronized( active_aa_dialogs ){
				
				active_aa_dialogs.add( mb );
			}		
			
			mb.open(
				(e)->{
			
					boolean kill_em = RememberedDecisionsManager.getRememberedDecision( remember_id ) >= 0;
					
					synchronized( active_aa_dialogs ){
						
						active_aa_dialogs.remove( mb );
						
						if ( kill_em ){
							
							Utils.execSWTThread(()->{
								
								for ( MessageBoxShell x: active_aa_dialogs ){
									
									x.close();
								}
							});
							
							active_aa_dialogs.clear();
						}
					}
				});
		}else{
			
			new MessageSlideShell(
					Display.getCurrent(), 
					SWT.ICON_INFORMATION,
					MSG_ALREADY_EXISTS, 
					null, 
					new String[]{
						originating_loc,
						name,
						MessageText.getString(MSG_ALREADY_EXISTS_NAME),
					}, 
					3 );
		}
	}
}
