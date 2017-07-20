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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

import com.biglybt.ui.common.RememberedDecisionsManager;

/**
 * @author TuxPaper
 * @created Sep 3, 2009
 *
 */
public class AdvRenameWindow
{
	private DownloadManager dm;

	private Shell shell;

	private String newName = null;

	protected int renameDecisions;

	private static final int RENAME_DISPLAY = 0x1;

	private static final int RENAME_SAVEPATH = 0x2;

	private static final int RENAME_TORRENT = 0x4;

	public static void main(String[] args) {
		AdvRenameWindow window = new AdvRenameWindow();
		window.open(null);
		window.waitUntilDone();
	}

	public AdvRenameWindow() {
	}

	public void open(DownloadManager dm) {
		this.dm = dm;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				openInSWT();
			}
		});
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

		Messages.setLanguageText(shell, "AdvRenameWindow.title");

		Label lblMessage = new Label(shell, SWT.WRAP);
		Messages.setLanguageText(lblMessage, "AdvRenameWindow.message");

		final Text txtInput = new Text(shell, SWT.BORDER);
		txtInput.setText(dm == null ? "" : dm.getDisplayName());

		final Button btnDisplayName = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnDisplayName,
				"MyTorrentsView.menu.rename.displayed");

		final Button btnSavePath = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnSavePath,
				"MyTorrentsView.menu.rename.save_path");

		final Button btnTorrent = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnTorrent, "AdvRenameWindow.rename.torrent");

		Composite cButtons = new Composite(shell, SWT.NONE);
		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		rowLayout.fill = true;
		rowLayout.spacing = 5;
		Utils.setLayout(cButtons, rowLayout);

		Button btnReset = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnReset, "Button.reset");
		btnReset.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				txtInput.setText(TorrentUtils.getLocalisedName(dm.getTorrent()));
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnOk = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnOk, "Button.ok");
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

				shell.dispose();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnCancel = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnCancel, "Button.cancel");
		btnCancel.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.dispose();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

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

		fd = new FormData();
		fd.top = new FormAttachment(txtInput, 5);
		fd.left = new FormAttachment(0, 8);
		fd.right = new FormAttachment(100, -3);
		btnDisplayName.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(btnDisplayName, 2);
		fd.left = new FormAttachment(0, 8);
		fd.right = new FormAttachment(100, -3);
		btnSavePath.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(btnSavePath, 2);
		fd.left = new FormAttachment(0, 8);
		fd.right = new FormAttachment(100, -3);
		btnTorrent.setLayoutData(fd);

		int renameDecisions = RememberedDecisionsManager.getRememberedDecision("adv.rename");
		if ((renameDecisions & RENAME_DISPLAY) > 0) {
			btnDisplayName.setSelection(true);
		}
		if ((renameDecisions & RENAME_SAVEPATH) > 0) {
			btnSavePath.setSelection(true);
		}
		if ((renameDecisions & RENAME_TORRENT) > 0) {
			btnTorrent.setSelection(true);
		}

		fd = new FormData();
		fd.top = new FormAttachment(btnTorrent, 5);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(100, -3);
		cButtons.setLayoutData(fd);

		shell.pack();
		Utils.centreWindow(shell);
		shell.open();
	}

	private void waitUntilDone() {
		while (shell != null && !shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch()) {
				shell.getDisplay().sleep();
			}
		}
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
