/*
 * Created on Sep 3, 2009 3:12:13 PM
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
 */
package com.biglybt.ui.swt.shells;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.common.RememberedDecisionsManager;

/**
 * @author TuxPaper
 * @created Sep 3, 2009
 *
 */
public class AdvRenameWindow
{
	private final DownloadManager dm;
	
	private Shell shell;

	private String newName = null;

	protected int renameDecisions;

	private static final int RENAME_DISPLAY = 0x1;

	private static final int RENAME_SAVEPATH = 0x2;

	private static final int RENAME_TORRENT = 0x4;

	private UserPrompterResultListener resultListener;
	
	private int result = -1;

	public static void main(String[] args) {
		AdvRenameWindow window = new AdvRenameWindow(null);
		window.open(null);
		window.waitUntilDone();
	}

	public AdvRenameWindow(DownloadManager dm) {
		this.dm = dm;
	}

	public void open() {
		open( null );
	}
	
	public void open(UserPrompterResultListener l) {
		resultListener = l;
		Utils.execSWTThread( this::openInSWT );
	}

	private void openInSWT() {
		shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.RESIZE);
		Utils.setShellIcon(shell);
		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					shell.dispose();
				}
			}
		});
		
		shell.addDisposeListener(e -> {
			resultListener.prompterClosed(result);
		});
		
		Messages.setLanguageText(shell, "AdvRenameWindow.title");

		Label lblMessage = new Label(shell, SWT.WRAP);
		Messages.setLanguageText(lblMessage, "AdvRenameWindow.message");

		Color faded = Colors.dark_grey;
		
			// input
		
		Text txtInput = new Text(shell, SWT.BORDER);
				
		Consumer<String> text_setter = str->{
						
			txtInput.setText( str );
			
			if ( dm.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
			
					// name is made up in this case, ignore suffix
				
				txtInput.selectAll();
				
			}else{
				
				int	pos = str.lastIndexOf( '.' );
				
				if ( pos <= 0 ){
					
					txtInput.selectAll();
					
				}else{
					
					String ext = str.substring( pos+1 );
					
					if ( ext.contains( " " )){
						
						txtInput.selectAll();
						
					}else{
					
						txtInput.setSelection( 0, pos );
					}
				}
			}
			
			txtInput.setFocus();
		};
		
		
		String display_name 	=  dm.getDisplayName();
		String save_path		= dm.getSaveLocation().getName();
		String torrent_name		= new File(dm.getTorrentFileName()).getName();
		
		text_setter.accept( display_name );

		
		if ( torrent_name.toLowerCase( Locale.US ).endsWith( ".torrent" )){
			
			torrent_name = torrent_name.substring( 0, torrent_name.length() - 8 );
		}
		
			// options
		
				// display 

		Button btnDisplayName = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnDisplayName,"MyTorrentsView.menu.rename.displayed");
		
		Label btnDisplayValue = new Label( shell, SWT.NONE );
		btnDisplayValue.setForeground( faded );
		btnDisplayValue.setText( display_name );
		
		Label btnDisplayPad = new Label( shell, SWT.NONE );
		
		Button btnDisplayUse = new Button(shell, SWT.PUSH);
		//btnDisplayUse.setForeground( faded );
		Messages.setLanguageText(btnDisplayUse,"label.use");
		btnDisplayUse.setData( display_name );
		
				// save
		
		Button btnSavePath = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnSavePath,"MyTorrentsView.menu.rename.save_path");

		Label btnSavePathValue = new Label( shell, SWT.NONE );
		btnSavePathValue.setText( save_path );
		btnSavePathValue.setForeground( faded );
		
		Label btnSavePathPad = new Label( shell, SWT.NONE );
		
		Button btnSavePathUse = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnSavePathUse,"label.use");
		//btnSavePathUse.setForeground( faded );
		btnSavePathUse.setData( save_path );
		
				// torrent
		
		Button btnTorrent = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnTorrent, "AdvRenameWindow.rename.torrent");

		Label btnTorrentValue = new Label( shell, SWT.NONE );
		btnTorrentValue.setText( torrent_name );
		btnTorrentValue.setForeground( faded );
		
		Label btnTorrentPad = new Label( shell, SWT.NONE );

		Button btnTorrentUse = new Button(shell, SWT.PUSH);
		//btnTorrentUse.setForeground( faded );
		Messages.setLanguageText(btnTorrentUse,"label.use");
		btnTorrentUse.setData( torrent_name );
		
			// separator
		
		Label separator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
		
			// buttons
		
		Composite cButtons = new Composite(shell, SWT.NONE);
		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		rowLayout.fill = true;
		rowLayout.spacing = 5;
		cButtons.setLayout(rowLayout);

		Button btnReset = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnReset, "Button.reset");
		btnReset.setLayoutData(new RowData());
		btnReset.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				text_setter.accept(TorrentUtils.getLocalisedName(dm.getTorrent()));
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button[] buttons = Utils.createOKCancelButtons( cButtons );
		
		Button btnOk 		= buttons[0];
		Button btnCancel 	= buttons[1];
		
		if ( resultListener != null ){
			btnCancel.setToolTipText( MessageText.getString( "long.press.cancel.tt" ));
			
			btnCancel.addListener(
				SWT.MouseDown,
				new Listener()
				{
					boolean 	mouseDown 	= false;
					TimerEvent	timerEvent 	= null;
					
					public void 
					handleEvent(
						Event event )
					{
						if ( event.button != 1 ){
							
							return;
						}
						
						if (timerEvent == null) {
							timerEvent = SimpleTimer.addEvent("MouseHold",
									SystemTime.getOffsetTime(1000), te -> {
										timerEvent = null;
										if (!mouseDown) {
											return;
										}
	
										Utils.execSWTThread(() -> {
											if (!mouseDown) {
												return;
											}
	
											if ( event.display.getCursorControl() != btnCancel ) {
												return;
											}
	
											mouseDown = false;
	
											shell.dispose();
										});
									});
						}
	
						mouseDown = true;
					}
				});
		}
		
		shell.setDefaultButton(btnOk);
		btnOk.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				newName = txtInput.getText();

				renameDecisions = 0;
				if (btnDisplayName.getSelection()) {
					renameDecisions |= RENAME_DISPLAY;
				}
				if (btnSavePath.getSelection()) {
					renameDecisions |= RENAME_SAVEPATH;
				}
				if (btnTorrent.getSelection()) {
					renameDecisions |= RENAME_TORRENT;
				}
				RememberedDecisionsManager.setRemembered("adv.rename", renameDecisions);

				Utils.getOffOfSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						doRename();
					}
				});

				result = SWT.OK;
				shell.dispose();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		
		btnCancel.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				result = SWT.CANCEL;
				shell.dispose();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		for ( Button b: new Button[]{ btnDisplayUse, btnSavePathUse, btnTorrentUse }){
			
			b.addListener(
				SWT.Selection,
					e->{
						text_setter.accept((String)e.widget.getData());
					});
		}
		
		shell.setLayout(new FormLayout());

		FormData fd;
		fd = new FormData();
		fd.top = new FormAttachment(0, 3);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(100, -3);
		lblMessage.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(lblMessage, 5);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(100, -3);
		fd.width = 300;
		txtInput.setLayoutData(fd);

			// display value
		
		fd = new FormData();
		fd.top = new FormAttachment(btnDisplayUse, 0, SWT.CENTER );
		fd.left = new FormAttachment(0, 8);
		btnDisplayName.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(btnDisplayUse, 0, SWT.CENTER );
		fd.left = new FormAttachment(btnDisplayName, 40);
		btnDisplayValue.setLayoutData(fd);
	
		fd = new FormData();
		fd.left = new FormAttachment(btnDisplayValue, 0);
		fd.right = new FormAttachment(btnDisplayUse, -8);
		btnDisplayPad.setLayoutData(fd);
	
		fd = new FormData();
		fd.top = new FormAttachment(txtInput, 5 );
		fd.right = new FormAttachment(100, -3);
		fd.width = btnTorrentUse.computeSize( SWT.DEFAULT, SWT.DEFAULT ).x;
		btnDisplayUse.setLayoutData(fd);	
		
			// save path
		
		fd = new FormData();
		fd.top = new FormAttachment(btnSavePathUse, 0, SWT.CENTER );
		fd.left = new FormAttachment(0, 8);
		btnSavePath.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(btnSavePathUse, 0, SWT.CENTER );
		fd.left = new FormAttachment(btnDisplayValue, 0, SWT.LEFT );
		btnSavePathValue.setLayoutData(fd);
	
		fd = new FormData();
		fd.left = new FormAttachment(btnSavePathValue, 0);
		fd.right = new FormAttachment(btnSavePathUse, -8);
		btnSavePathPad.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(btnDisplayUse, 5 );
		fd.right = new FormAttachment(100, -3);
		fd.width = btnTorrentUse.computeSize( SWT.DEFAULT, SWT.DEFAULT ).x;
		btnSavePathUse.setLayoutData(fd);
		
			// torrent
		
		fd = new FormData();
		fd.top = new FormAttachment(btnTorrentUse, 0, SWT.CENTER );
		fd.left = new FormAttachment(0, 8);
		btnTorrent.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(btnTorrentUse, 0, SWT.CENTER );
		fd.left = new FormAttachment(btnSavePathValue, 0, SWT.LEFT );
		btnTorrentValue.setLayoutData(fd);
	
		fd = new FormData();
		fd.left = new FormAttachment(btnTorrentValue, 0);
		fd.right = new FormAttachment(btnTorrentUse, -8);
		btnTorrentPad.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(btnSavePathUse, 5 );
		fd.right = new FormAttachment(100, -3);
		fd.width = btnTorrentUse.computeSize( SWT.DEFAULT, SWT.DEFAULT ).x;
		btnTorrentUse.setLayoutData(fd);

		
		
		
		
		
		int renameDecisions = RememberedDecisionsManager.getRememberedDecision("adv.rename");
		if ((renameDecisions & RENAME_DISPLAY) > 0) {
			btnDisplayName.setSelection(true);
		}
		
		if ( dm.canMoveDataFiles()){
			if ((renameDecisions & RENAME_SAVEPATH) > 0) {
				btnSavePath.setSelection(true);
			}
			if ((renameDecisions & RENAME_TORRENT) > 0) {
				btnTorrent.setSelection(true);
			}
		}else{
			btnSavePath.setEnabled( false );
			btnTorrent.setEnabled( false );
		}
		
			// separator
		
		fd = new FormData();
		fd.top = new FormAttachment(btnTorrentUse, 2);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(100, -3);
		separator.setLayoutData(fd);
		
			// buttons
		
		fd = new FormData();
		fd.top = new FormAttachment(separator, 5);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(100, -3);
		cButtons.setLayoutData(fd);

		Utils.makeButtonsEqualWidth( Arrays.asList(btnReset, btnOk,btnCancel ));

		shell.pack();
		Utils.centreWindow(shell);
		shell.open();
	}

	public void
	cancel()
	{
		Utils.execSWTThread(()->{ shell.dispose(); });
	}
	
	private void waitUntilDone() {
		Utils.readAndDispatchLoop(shell);
	}

	private void doRename() {
		if (dm == null) {
			return;
		}

		boolean saveLocationIsFolder = dm.getSaveLocation().isDirectory();

		String newDisplayName 	= newName;
		String newSavePath		= FileUtil.convertOSSpecificChars( newName, saveLocationIsFolder );
		String newTorrentName	= FileUtil.convertOSSpecificChars( newName, false );

		if ((renameDecisions & RENAME_DISPLAY) > 0) {
			dm.getDownloadState().setDisplayName(newDisplayName);
		}
		if ( dm.canMoveDataFiles()){
			
			if ((renameDecisions & RENAME_SAVEPATH) > 0) {
				try {

					try{
						if ( dm.getTorrent().isSimpleTorrent()){

							String dnd_sf = dm.getDownloadState().getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

							if ( dnd_sf != null ){

								dnd_sf = dnd_sf.trim();

								String existing_name = dm.getSaveLocation().getName();

								if ( existing_name.endsWith( dnd_sf )){

									if ( !newSavePath.endsWith( dnd_sf )){

										newSavePath += dnd_sf;
									}
								}
							}
						}
					}catch( Throwable e ){
					}
					dm.renameDownload(newSavePath);
				} catch (Exception e) {
					Logger.log(new LogAlert(dm, LogAlert.REPEATABLE,
							"Download data rename operation failed", e));
				}
			}
			
			if ((renameDecisions & RENAME_TORRENT) > 0) {
				try {
					dm.renameTorrentSafe(newTorrentName);
				} catch (Exception e) {
					Logger.log(new LogAlert(dm, LogAlert.REPEATABLE,
							"Torrent rename operation failed", e));
				}
			}
		}
	}
}
