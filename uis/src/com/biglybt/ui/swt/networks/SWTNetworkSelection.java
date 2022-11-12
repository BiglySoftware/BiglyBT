/*
 * Created on 14-Jan-2005
 * Created by Paul Gardner
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

package com.biglybt.ui.swt.networks;


import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AENetworkClassifierListener;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;


/**
 * @author parg
 *
 */

public class
SWTNetworkSelection
	implements AENetworkClassifierListener
{
	public
	SWTNetworkSelection()
	{
		AENetworkClassifier.addListener( this );
	}

	@Override
	public String[]
	selectNetworks(
		final String	description,
		final String[]	tracker_networks )
	{
		final Display	display = Utils.getDisplay();

		if ( display.isDisposed()){

			return( null );
		}

		final AESemaphore	sem = new AESemaphore("NetworkClassifier");

		final classifierDialog[]	dialog = new classifierDialog[1];

		try{
			Utils.execSWTThread(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						dialog[0] = new classifierDialog( sem, display, description, tracker_networks );
					}
				});
		}catch( Throwable e ){

			Debug.printStackTrace( e );

			return( null );
		}

		sem.reserve();

		return( dialog[0].getSelection());
	}

	protected static class
	classifierDialog
	{
		protected Shell			shell;
		protected AESemaphore	sem;

		protected String[]		selection;

		private Button[]		checkboxes;


		protected
		classifierDialog(
			AESemaphore		_sem,
			Display			display,
			String			description,
			String[]		tracker_networks )
		{
			sem	= _sem;

			if ( display.isDisposed()){

				sem.releaseForever();

				return;
			}

	 		shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);

	 		Utils.setShellIcon(shell);

		 	shell.setText(MessageText.getString("window.networkselection.title"));

		 	GridLayout layout = new GridLayout();
		 	layout.numColumns = 3;

		 	shell.setLayout (layout);

		 	GridData gridData;

			Label info_label = new Label(shell,SWT.NULL);
			info_label.setText(MessageText.getString("window.networkselection.info"));
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 3;
			info_label.setLayoutData(gridData);

				// line

			Label labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 3;
			labelSeparator.setLayoutData(gridData);

	    		// description

			Label desc_label = new Label(shell,SWT.NULL);
			desc_label.setText(MessageText.getString("window.networkselection.description"));
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			desc_label.setLayoutData(gridData);

			Label desc_value = new Label(shell,SWT.NULL);
			desc_value.setText(description);
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			desc_value.setLayoutData(gridData);

				// networks

			checkboxes = new Button[AENetworkClassifier.AT_NETWORKS.length];

			for (int i=0;i<AENetworkClassifier.AT_NETWORKS.length;i++){

				String	network		= AENetworkClassifier.AT_NETWORKS[i];

				String	msg_text	= "ConfigView.section.connection.networks." + network;

			    Label label = new Label(shell, SWT.NULL);
				gridData = new GridData(GridData.FILL_BOTH);
				gridData.horizontalSpan = 1;
				label.setLayoutData(gridData);
				Messages.setLanguageText(label, msg_text);

			    final Button checkBox = new Button(shell, SWT.CHECK);
			    checkBox.setSelection(false);
				gridData = new GridData(GridData.FILL_BOTH);
				gridData.horizontalSpan = 2;
				checkBox.setLayoutData(gridData);

				checkboxes[i]	= checkBox;

				for ( int j=0;j<tracker_networks.length;j++ ){

					if ( tracker_networks[j] == network ){

						checkBox.setSelection( true );
					}
				}
			}

				// line

			labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 3;
			labelSeparator.setLayoutData(gridData);

				// buttons

			new Label(shell,SWT.NULL);

			Button bOk = new Button(shell,SWT.PUSH);
		 	bOk.setText(MessageText.getString("Button.ok"));
		 	gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
		 	gridData.grabExcessHorizontalSpace = true;
		 	gridData.widthHint = 70;
			bOk.setLayoutData(gridData);
		 	bOk.addListener(SWT.Selection,new Listener() {
		  		@Override
				  public void handleEvent(Event e) {
			 		close(true);
		   		}
			 });

		 	Button bCancel = new Button(shell,SWT.PUSH);
		 	bCancel.setText(MessageText.getString("Button.cancel"));
		 	gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
		 	gridData.grabExcessHorizontalSpace = false;
		 	gridData.widthHint = 70;
			bCancel.setLayoutData(gridData);
		 	bCancel.addListener(SWT.Selection,new Listener() {
		 		@Override
			  public void handleEvent(Event e) {
			 		close(false);
		   		}
		 	});

			shell.setDefaultButton( bOk );

			shell.addListener(SWT.Traverse, new Listener() {
				@Override
				public void handleEvent(Event e) {
					if ( e.character == SWT.ESC){
						close( false );
					}
				}
			});


		 	shell.pack ();

			Utils.centreWindow( shell );

			shell.open ();

			Utils.readAndDispatchLoop( shell );
		}

		protected void
		close(
			boolean		ok )
	 	{
	 		if ( !ok ){

	 			selection	= null;

	 		}else{

	 			List	l = new ArrayList();

	 			for (int i=0;i<AENetworkClassifier.AT_NETWORKS.length;i++){

	 				if ( checkboxes[i].getSelection()){

	 					l.add( AENetworkClassifier.AT_NETWORKS[i] );
	 				}
	 			}

	 			selection = new String[ l.size() ];

	 			l.toArray( selection );
	 		}

	 		shell.dispose();
	 		sem.releaseForever();
	 	}

	 	protected String[]
		getSelection()
	 	{
	 		return( selection );
	 	}
	}
}