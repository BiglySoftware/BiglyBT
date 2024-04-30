/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.shells;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;

import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.common.RememberedDecisionsManager;

public class RevertFileLocationsWindow
{
	private static final int REVERT_COPY 			= 0x1;
	private static final int REVERT_RETAIN_NAMES 	= 0x2;
	private static final int REVERT_HARD_LINK	 	= 0x4;

	
	private Shell 			shell;

	private boolean			done;
	
	private ResultListener 	resultListener;
	
	public RevertFileLocationsWindow(){
	}

	public void open(ResultListener l) {
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
			if ( !done ){
				resultListener.closed( false, false, false, false );
			}
		});
		
		Messages.setLanguageText(shell, "MyTorrentsView.menu.revertfiles");

		Label lblMessage = new Label(shell, SWT.WRAP);
		Messages.setLanguageText(lblMessage, "revert.file.locations.message");

			// hard-link
			
		Button btnHardLink = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnHardLink,"revert.file.locations.hardlink");
		
			// copy
			
		Button btnCopy = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnCopy,"revert.file.locations.copy");
						
			// retain names
		
		Button btnRetainNames = new Button(shell, SWT.CHECK);
		Messages.setLanguageText(btnRetainNames,"revert.file.locations.retain.names");
				
			// separator
		
		Label separator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
		
			// buttons
		
		Composite cButtons = new Composite(shell, SWT.NONE);
		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		rowLayout.fill = true;
		rowLayout.spacing = 3;
		cButtons.setLayout(rowLayout);

		Button[] buttons = Utils.createOKCancelButtons( cButtons );
		
		Button btnOk 		= buttons[0];
		Button btnCancel 	= buttons[1];
		

		
		shell.setDefaultButton(btnOk);
		btnOk.addListener( SWT.Selection, (ev)->{
				done = true;
				
				int	decisions = 0;
				
				boolean hard_link 	= btnHardLink.getSelection();
				boolean copy 		= btnCopy.getSelection();
				boolean retain		= btnRetainNames.getSelection();
				
				if ( hard_link ){
					decisions |= REVERT_HARD_LINK;
				}
				if ( copy ){
					decisions |= REVERT_COPY;
				}
				if ( retain ){
					decisions |= REVERT_RETAIN_NAMES;
				}
				
				RememberedDecisionsManager.setRemembered("revert.file.locations", decisions );

				AEThread2.createAndStartDaemon("RevertFiles", ()->{
					
						resultListener.closed( true, hard_link, copy, retain );
				});

				shell.dispose();
		});

		
		btnCancel.addListener( SWT.Selection, (ev)->{
			shell.dispose();
		});


		
		shell.setLayout(new FormLayout());

		FormData fd;
		fd = new FormData();
		fd.top = new FormAttachment(0, 3);
		fd.left = new FormAttachment(0, 8);
		fd.right = new FormAttachment(100, -3);
		fd.width = 300;
		lblMessage.setLayoutData(fd);

			// hard-link
		
		fd = new FormData();
		fd.top = new FormAttachment(lblMessage, 10, SWT.LEFT );
		fd.left = new FormAttachment(0, 8);
		fd.right = new FormAttachment(100, -8);
		btnHardLink.setLayoutData(fd);

			// copy
		
		fd = new FormData();
		fd.top = new FormAttachment(btnHardLink, 0, SWT.LEFT );
		fd.left = new FormAttachment(0, 8);
		fd.right = new FormAttachment(100, -8);
		btnCopy.setLayoutData(fd);

			// retain names
		
		fd = new FormData();
		fd.top = new FormAttachment(btnCopy, 0, SWT.LEFT );
		fd.left = new FormAttachment(0, 8);
		fd.right = new FormAttachment(100, -8);
		btnRetainNames.setLayoutData(fd);
	
		
		int decisions = RememberedDecisionsManager.getRememberedDecision("revert.file.locations");
		
		if ( decisions > 0 ){
			if ((decisions & REVERT_HARD_LINK) > 0) {
				btnHardLink.setSelection(true);
			}
			if ((decisions & REVERT_COPY) > 0) {
				btnCopy.setSelection(true);
			}
			if ((decisions & REVERT_RETAIN_NAMES) > 0) {
				btnRetainNames.setSelection(true);
			}
			
			if ( btnHardLink.getSelection()){
				btnCopy.setEnabled( false );
			}else if ( btnCopy.getSelection()){
				btnHardLink.setEnabled( false );
			}
		}
		
		btnCopy.addListener( SWT.Selection, (ev)->{
			btnHardLink.setEnabled( !btnCopy.getSelection());
		});
		
		btnHardLink.addListener( SWT.Selection, (ev)->{
			btnCopy.setEnabled( !btnHardLink.getSelection());
		});
		
			// separator
		
		fd = new FormData();
		fd.top = new FormAttachment(btnRetainNames, 2);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(100, -3);
		separator.setLayoutData(fd);
		
			// buttons
		
		fd = new FormData();
		fd.top = new FormAttachment(separator, 5);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(100, -3);
		cButtons.setLayoutData(fd);

		shell.pack();
		Utils.centreWindow(shell);
		shell.open();
	}

	public void
	cancel()
	{
		Utils.execSWTThread(()->{ shell.dispose(); });
	}
	
	public interface
	ResultListener
	{
		public void
		closed(
			boolean		ok,
			boolean		hard_link,
			boolean		copy_files,
			boolean		retain_name );
	}
}
