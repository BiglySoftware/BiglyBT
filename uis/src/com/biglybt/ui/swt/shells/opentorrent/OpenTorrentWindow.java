/*
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

package com.biglybt.ui.swt.shells.opentorrent;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.torrentdownloader.TorrentDownloader;
import com.biglybt.core.torrentdownloader.TorrentDownloaderCallBackInterface;
import com.biglybt.core.util.*;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pif.utils.subscriptions.SubscriptionManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.xml.rss.RSSUtils;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.UIConfigDefaultsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.shells.main.UIFunctionsImpl;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.StandardButtonsArea;

public class OpenTorrentWindow
	implements TorrentDownloaderCallBackInterface, UIUpdatable
{

	protected static String CONFIG_REFERRER_DEFAULT = "openUrl.referrer.default";

	private Shell shellForChildren;

	private Shell parent;

	private SkinnedDialog dlg;

	private StandardButtonsArea buttonsArea;

	private Button 	btnBrowseTorrent;
	private Button 	btnBrowseFolder;
	private Button 	btnPasteOrClear;
	private boolean	btnPasteOrClearIsPaste;
	
	private SWTSkinObjectTextbox soTextArea;

	private SWTSkinObject soReferArea;

	private Combo referrer_combo;

	private String last_referrer;

	private java.util.List<String> referrers;

	private SWTSkinObjectCheckbox soShowAdvanced;

	private String lastCopiedFromClip;
	
	public OpenTorrentWindow(Shell parent) {
		this.parent = parent;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_createWindow();
			}
		});
	}

	private void swt_createWindow() {
		dlg = new SkinnedDialog("skin3_dlg_opentorrent", "shell", SWT.RESIZE
				| SWT.DIALOG_TRIM);

		shellForChildren = dlg.getShell();
		SWTSkin skin = dlg.getSkin();

		soTextArea = (SWTSkinObjectTextbox) skin.getSkinObject("text-area");
		
		Text tb = ((Text) soTextArea.getControl());
				
		Clipboard clipboard = new Clipboard(Display.getDefault());

		String sClipText = (String) clipboard.getContents(TextTransfer.getInstance());
		
		if ( sClipText != null && !sClipText.trim().isEmpty()){
			
			sClipText = sClipText.trim();
			
			if ( addTorrentsFromTextList( sClipText, true ) > 0 ){
		
				tb.setText( sClipText );
				
				tb.setSelection( 0, sClipText.length());
				
				lastCopiedFromClip = sClipText;
			}
		}
		
		tb.setFocus();
		tb.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				int userMode = COConfigurationManager.getIntParameter("User Mode");
				if (userMode > 0) {
					if (soReferArea != null) {
						String text = ((Text) e.widget).getText();
						boolean hasURL = UrlUtils.parseTextForURL(text, false, true) != null;
						soReferArea.setVisible(hasURL);
					}
				}
			}
		});

		tb.addListener(SWT.KeyDown,(ev)->{

			if ( ev.keyCode == SWT.CR || ev.keyCode == SWT.KEYPAD_CR ){

				if (( ev.stateMask & ( SWT.SHIFT | SWT.CTRL | SWT.ALT )) != 0 ){
					
					openTorrent( SWT.OK );
				}
			}
		});

		SWTSkinObject soTopBar = skin.getSkinObject("add-buttons");
		if (soTopBar instanceof SWTSkinObjectContainer) {
			swt_addButtons(((SWTSkinObjectContainer) soTopBar).getComposite());
		}

		SWTSkinObject so;

		so = skin.getSkinObject("show-advanced");
		if (so instanceof SWTSkinObjectCheckbox) {
			soShowAdvanced = (SWTSkinObjectCheckbox) so;
			soShowAdvanced.setChecked(COConfigurationManager.getBooleanParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS));
		}

		soReferArea = skin.getSkinObject("refer-area");

		if ( lastCopiedFromClip != null && UrlUtils.parseTextForURL(lastCopiedFromClip, false, true) != null ){
			
			soReferArea.setVisible( true );
		}
		
		last_referrer = COConfigurationManager.getStringParameter(CONFIG_REFERRER_DEFAULT, "");
		if ( last_referrer == null ){
			last_referrer = "";
		}

		so = skin.getSkinObject("refer-combo");
		if (so instanceof SWTSkinObjectContainer) {
			referrer_combo = new Combo(((SWTSkinObjectContainer) so).getComposite(),
					SWT.BORDER);
			referrer_combo.setLayoutData(Utils.getFilledFormData());
			referrers = COConfigurationManager.getStringListParameter("url_open_referrers");
			
			if ( !last_referrer.isEmpty()){
				referrer_combo.add(last_referrer);
			}
			
			for (String referrer : referrers) {
				if ( !referrer.equals( last_referrer)){
					referrer_combo.add(referrer);
				}
			}
		}

		SWTSkinObject soButtonArea = skin.getSkinObject("button-area");
		if (soButtonArea instanceof SWTSkinObjectContainer) {
			buttonsArea = new StandardButtonsArea() {
				@Override
				protected void clicked(int intValue) {
					openTorrent( intValue );
				}
			};
			buttonsArea.setButtonIDs(new String[] {
				MessageText.getString("Button.ok"),
				MessageText.getString("Button.cancel")
			});
			buttonsArea.setButtonVals(new Integer[] {
				SWT.OK,
				SWT.CANCEL
			});
			buttonsArea.swt_createButtons(((SWTSkinObjectContainer) soButtonArea).getComposite());
		}

		UIUpdaterSWT.getInstance().addUpdater(this);

		/*
		 * The bring-to-front logic for torrent addition is controlled by other parts of the code so we don't
		 * want the dlg to override this behaviour (main example here is torrents passed from, say, a browser,
		 * and the user has disabled the 'show vuze on external torrent add' feature)
		 */

		dlg.open("otw",false);

		dlg.addCloseListener(new SkinnedDialog.SkinnedDialogClosedListener() {
			@Override
			public void skinDialogClosed(SkinnedDialog dialog) {
				dispose();
			}
		});
	}
	
	private void
	openTorrent(
		int	intValue )
	{
		String referrer = null;
		if (referrer_combo != null) {
			referrer = referrer_combo.getText().trim();
		}

		if (dlg != null) {
			dlg.close();
		}
		if (intValue == SWT.OK && soTextArea != null
				&& soTextArea.getText().length()>0) {
			openTorrent(soTextArea.getText(), referrer);
		}
	}

	protected void openTorrent(String text, String newReferrer) {
		if (newReferrer != null && newReferrer.length() > 0) {

			if (!referrers.contains(newReferrer)) {
				referrers.add(newReferrer);
				COConfigurationManager.setParameter("url_open_referrers", referrers);
				COConfigurationManager.save();
			}

			COConfigurationManager.setParameter(CONFIG_REFERRER_DEFAULT, newReferrer);
			COConfigurationManager.save();
		}
		final String[] splitters = {
			"\r\n",
			"\n",
			"\r",
			"\t"
		};

		String lines[] = null;

		for (int i = 0; i < splitters.length; i++) {
			if (text.contains(splitters[i])) {
				lines = text.split(splitters[i]);
				break;
			}
		}

		if (lines == null) {
			lines = new String[] {
				text
			};
		}

		TorrentOpener.openTorrentsFromStrings(new TorrentOpenOptions( null ), parent, null, lines, newReferrer,
				this, false);
	}

	protected void dispose() {
		UIUpdaterSWT.getInstance().removeUpdater(this);
	}

	private void swt_addButtons(Composite parent) {
		Composite cButtons = new Composite(parent, SWT.NONE);
		RowLayout rLayout = new RowLayout(SWT.HORIZONTAL);
		rLayout.marginBottom = 0;
		rLayout.marginLeft = 0;
		rLayout.marginRight = 0;
		rLayout.marginTop = 0;
		cButtons.setLayout(rLayout);
		cButtons.setLayoutData(Utils.getFilledFormData());

		// Buttons for tableTorrents

		btnBrowseTorrent = new Button(cButtons, SWT.PUSH);
		btnBrowseTorrent.setLayoutData( new RowData());
		Messages.setLanguageText(btnBrowseTorrent, "OpenTorrentWindow.addFiles");
		btnBrowseTorrent.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				FileDialog fDialog = new FileDialog(shellForChildren, SWT.OPEN
						| SWT.MULTI);
				fDialog.setFilterExtensions(new String[] {
					"*.torrent",
					"*.tor",
					Constants.FILE_WILDCARD
				});
				fDialog.setFilterNames(new String[] {
					"*.torrent",
					"*.tor",
					Constants.FILE_WILDCARD
				});
				fDialog.setFilterPath(TorrentOpener.getFilterPathTorrent());
				fDialog.setText(MessageText.getString("MainWindow.dialog.choose.file"));
				String fileName = TorrentOpener.setFilterPathTorrent(fDialog.open());
				if (fileName != null) {
					addTorrentsToWindow(fDialog.getFilterPath(), fDialog.getFileNames());
				}
			}
		});

		btnBrowseFolder = new Button(cButtons, SWT.PUSH);
		btnBrowseFolder.setLayoutData( new RowData());
		Messages.setLanguageText(btnBrowseFolder, "OpenTorrentWindow.addFiles.Folder");
		btnBrowseFolder.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				DirectoryDialog fDialog = new DirectoryDialog(shellForChildren,
						SWT.NULL);
				fDialog.setFilterPath(TorrentOpener.getFilterPathTorrent());
				fDialog.setMessage(MessageText.getString("MainWindow.dialog.choose.folder"));
				String path = TorrentOpener.setFilterPathTorrent(fDialog.open());
				if (path != null) {
					addTorrentsToWindow(path, null);
				}
			}
		});

			// if we have pasted from clip then start off as a 'clear' button
		
		btnPasteOrClearIsPaste = lastCopiedFromClip == null;
		
		btnPasteOrClear = new Button(cButtons, SWT.PUSH);
		btnPasteOrClear.setLayoutData( new RowData());
		
		Messages.setLanguageText(btnPasteOrClear, btnPasteOrClearIsPaste?"OpenTorrentWindow.addFiles.Clipboard":"Button.clear");
		
		btnPasteOrClear.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if ( btnPasteOrClearIsPaste ){
					Clipboard clipboard = new Clipboard(shellForChildren.getDisplay());
	
					String sClipText = (String) clipboard.getContents(TextTransfer.getInstance());
					
					if ( sClipText != null ){
						
						sClipText = sClipText.trim();
						
						lastCopiedFromClip = sClipText;
						
						addTorrentsFromTextList( sClipText, false );
					}
				}else{
					soTextArea.setText( "" );	
				}
			}
		});

		btnPasteOrClear.setVisible( !btnPasteOrClearIsPaste );
		
		Utils.makeButtonsEqualWidth( Arrays.asList( btnBrowseTorrent, btnBrowseFolder, btnPasteOrClear ));
	}

	private String ensureTrailingSeparator(String sPath) {
		if (sPath == null || sPath.length() == 0 || sPath.endsWith(File.separator))
			return sPath;
		return sPath + File.separator;
	}

	private int addTorrentsToWindow(String sTorrentFilePath,
			String[] sTorrentFilenames) {
		String text = soTextArea.getText();

		sTorrentFilePath = ensureTrailingSeparator(sTorrentFilePath);

		// Process Directory
		if (sTorrentFilePath != null && sTorrentFilenames == null) {
			File dir = new File(sTorrentFilePath);
			if (!dir.isDirectory())
				return 0;

			final File[] files = dir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File arg0) {
					if (FileUtil.getCanonicalFileName(arg0.getName()).endsWith(".torrent"))
						return true;
					if (FileUtil.getCanonicalFileName(arg0.getName()).endsWith(".tor"))
						return true;
					return false;
				}
			});

			if (files.length == 0)
				return 0;

			sTorrentFilenames = new String[files.length];
			for (int i = 0; i < files.length; i++)
				sTorrentFilenames[i] = files[i].getName();
		}

		int numAdded = 0;

		if ( sTorrentFilenames != null ){
			for (int i = 0; i < sTorrentFilenames.length; i++) {
				if (sTorrentFilenames[i] == null || sTorrentFilenames[i].length() == 0)
					continue;

				// Process File
				String sFileName = ((sTorrentFilePath == null) ? "" : sTorrentFilePath)
						+ sTorrentFilenames[i];

				File file = new File(sFileName);

				try {
					if (UrlUtils.isURL(sFileName)
							|| (file.exists() && TorrentUtils.isTorrentFile(sFileName))) {
						if (text.length() > 0) {
							text += "\n";
						}
						text += sFileName;
						numAdded++;
					}
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				}
			}

			if (numAdded > 0) {
				soTextArea.setText(text);
			}
		}

		return numAdded;
	}

	/**
	 * Add Torrent(s) to Window using a text list of files/urls/torrents
	 *
	 * @param sClipText Text to parse
	 * @param bVerifyOnly Only check if there's potential torrents in the text,
	 *                     do not try to add the torrents.
	 *
	 * @return Number of torrents added or found.  When bVerifyOnly, this number
	 *          may not be exact.
	 */
	private int addTorrentsFromTextList(String sClipText, boolean bVerifyOnly) {
		String[] lines = null;
		int iNumFound = 0;
		// # of consecutive non torrent lines
		int iNoTorrentLines = 0;
		// no use checking the whole clipboard (which may be megabytes)
		final int MAX_CONSECUTIVE_NONTORRENT_LINES = 100;

		final String[] splitters = {
			"\r\n",
			"\n",
			"\r",
			"\t"
		};

		for (int i = 0; i < splitters.length; i++)
			if (sClipText.contains(splitters[i])) {
				lines = sClipText.split(splitters[i]);
				break;
			}

		if (lines == null)
			lines = new String[] {
				sClipText
			};

		// Check if URL, 20 byte hash, Dir, or file
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.startsWith("\"") && line.endsWith("\"")) {
				if (line.length() < 3) {
					line = "";
				} else {
					line = line.substring(1, line.length() - 2);
				}
			}

			boolean ok;

			if (line.length()==0) {
				ok = false;
			} else if (UrlUtils.isURL(line)) {
				ok = true;
			} else {
				File file = new File(line);

				if (!file.exists()) {
					ok = false;
				} else if (file.isDirectory()) {
					if (bVerifyOnly) {
						// XXX Could do a file count here, but the number found is not
						//     expected to be an exact number anyway, since we aren't
						//     event verifying if they are torrents.
						ok = true;
					} else {
						iNumFound += addTorrentsToWindow(lines[i], null);
						ok = false;
					}
				} else {
					ok = true;
				}
			}

			if (!ok) {
				iNoTorrentLines++;
				lines[i] = null;
				if (iNoTorrentLines > MAX_CONSECUTIVE_NONTORRENT_LINES)
					break;
			} else {
				iNumFound++;
				iNoTorrentLines = 0;
			}
		}

		if (bVerifyOnly) {
			return iNumFound;
		}

		return addTorrentsToWindow(null, lines);
	}

	public static void main(String[] args) {
		Core core = CoreFactory.create();
		core.start();

		UIConfigDefaultsSWT.initialize();

		//		try {
		//			SWTThread.createInstance(null);
		//		} catch (SWTThreadAlreadyInstanciatedException e) {
		//			e.printStackTrace();
		//		}
		Display display = Display.getDefault();

		Colors.getInstance();

		COConfigurationManager.setParameter("User Mode", 2);

		UIFunctionsImpl uiFunctions = new UIFunctionsImpl(null);
		UIFunctionsManager.setUIFunctions(uiFunctions);

		//		invoke(null, core.getGlobalManager());
		OpenTorrentWindow window = new OpenTorrentWindow(null);
		while (!window.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}

		core.stop();
	}

	private boolean isDisposed() {
		if (dlg == null) {
			return false;
		}
		return dlg.isDisposed();
	}

	@Override
	public void TorrentDownloaderEvent(int state, TorrentDownloader inf) {

		// This method is run even if the window is closed.

		// The default is to delete file on cancel
		// We set this flag to false if we detected the file was not a torrent
		if (!inf.getDeleteFileOnCancel() &&
				(	state == TorrentDownloader.STATE_CANCELLED ||
					state == TorrentDownloader.STATE_ERROR ||
					state == TorrentDownloader.STATE_DUPLICATE ||
					state == TorrentDownloader.STATE_FINISHED)){

			File file = inf.getFile();

			// we already know it isn't a torrent.. we are just using the call
			// to popup the message

			boolean	done = false;

			if ( RSSUtils.isRSSFeed( file )){

				try{
					URL url = new URL( inf.getURL() );

					UIManager ui_manager = StaticUtilities.getUIManager( 10*1000 );

					if ( ui_manager != null ){

						String details = MessageText.getString(
								"subscription.request.add.message",
								new String[]{ inf.getURL() });

						long res = ui_manager.showMessageBox(
								"subscription.request.add.title",
								"!" + details + "!",
								UIManagerEvent.MT_YES | UIManagerEvent.MT_NO );

						if ( res == UIManagerEvent.MT_YES ){

							SubscriptionManager sm = PluginInitializer.getDefaultInterface().getUtilities().getSubscriptionManager();

							sm.requestSubscription( url );

							done = true;
						}
					}
				}catch( Throwable e ){

					Debug.out( e );
				}
			}

			if ( !done ){
				TorrentUtil.isFileTorrent(inf.getURL(), file, inf.getURL(), true );
			}

			if (file.exists()) {
				file.delete();
			}

			return;
		}

		if (state == TorrentDownloader.STATE_INIT) {
		} else if (state == TorrentDownloader.STATE_FINISHED) {
			File file = inf.getFile();
			TorrentOpenOptions torrentOptions = new TorrentOpenOptions( null );
			if (!TorrentOpener.mergeFileIntoTorrentInfo(file.getAbsolutePath(),
					inf.getURL(), torrentOptions)) {
				if (file.exists())
					file.delete();
			} else {
				UIFunctions uif = UIFunctionsManager.getUIFunctions();
				boolean b = uif.addTorrentWithOptions(false, torrentOptions);
				if (!b && file.exists()) {
					file.delete();
				}
			}
		} else if (state == TorrentDownloader.STATE_CANCELLED
				|| state == TorrentDownloader.STATE_ERROR
				|| state == TorrentDownloader.STATE_DUPLICATE) {

		} else if (state == TorrentDownloader.STATE_DOWNLOADING) {
			int count = inf.getLastReadCount();
			int numRead = inf.getTotalRead();

				// some weird logic here that seems to want to bail early on a download if it doesn't look like it is a torrent (bnencode always starts with 'd'
				// and using 'delete file on cancel' as some crazy marker to control this...

				// PARG - added '<' to prevent early abandoning of RSS feed content

			if (!inf.getDeleteFileOnCancel() && numRead >= 16384) {
				inf.cancel();
			} else if (numRead == count && count > 0) {
				final byte[] bytes = inf.getLastReadBytes();
				if (bytes[0] != 'd' && bytes[0] != '<' ) {
					inf.setDeleteFileOnCancel(false);
				}
			}
		} else {
			return;
		}
	}

	@Override
	public void updateUI() {

		Clipboard clipboard = new Clipboard(Display.getDefault());

		String sClipText = (String) clipboard.getContents(TextTransfer.getInstance());
		
		if (sClipText != null){
			
			if ( lastCopiedFromClip != null && sClipText.equals( lastCopiedFromClip )){
				
				return;
			}
		
			boolean bTorrentInClipboard = addTorrentsFromTextList(sClipText, true) > 0;

			if ( btnPasteOrClear != null && !btnPasteOrClear.isDisposed()){
				
				btnPasteOrClear.setVisible(bTorrentInClipboard);
				
				if ( !btnPasteOrClearIsPaste ){
					
					Messages.setLanguageText(btnPasteOrClear, "OpenTorrentWindow.addFiles.Clipboard" );
					
					Utils.makeButtonsEqualWidth( Arrays.asList( btnBrowseTorrent, btnBrowseFolder, btnPasteOrClear ));
					
					btnPasteOrClear.getParent().layout( true );
					
					btnPasteOrClearIsPaste = true;
				}
			}
			
			if (bTorrentInClipboard) {
				Utils.setTT(btnPasteOrClear,sClipText);
			}
		}
		
		clipboard.dispose();
	}

	@Override
	public String getUpdateUIName() {
		return null;
	}
}
