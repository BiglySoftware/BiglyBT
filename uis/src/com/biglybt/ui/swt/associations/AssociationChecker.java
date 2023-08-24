/*
 * Created on 23-Apr-2004
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

package com.biglybt.ui.swt.associations;

/**
 * @author parg
 *
 */

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

import com.biglybt.pif.platform.PlatformManagerException;

public class
AssociationChecker
{
	public static void
	checkAssociations()
	{
		try{

		    PlatformManager	platform  = PlatformManagerFactory.getPlatformManager();

		    if ( platform.hasCapability(PlatformManagerCapabilities.RegisterFileAssociations) ){

		    	if ( COConfigurationManager.getBooleanParameter( "config.interface.checkassoc")){

		    		if ( !platform.isApplicationRegistered()){

		    			new AssociationChecker(  platform );
		    		}
		    	}
		    }
		}catch( Throwable e ){

			// Debug.printStackTrace( e );
		}
	}

	protected PlatformManager	platform;
	protected Display			display;
	protected Shell				shell;


	protected
	AssociationChecker(
		final PlatformManager		_platform )
	{
		platform	= _platform;

		display = Utils.getDisplay();

		if ( display.isDisposed()){

			return;
		}

		Utils.execSWTThread(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						check();
					}
				});
	}

	protected void
	check()
	{
		if (display.isDisposed())
			return;

 		shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM);

 		Utils.setShellIcon(shell);
	 	shell.setText(MessageText.getString("dialog.associations.title"));

	 	GridLayout layout = new GridLayout();
	 	layout.numColumns = 3;

	 	shell.setLayout (layout);

	 	GridData gridData;

	 		// text

		Label user_label = new Label(shell,SWT.NULL);
		Messages.setLanguageText(user_label, "dialog.associations.prompt");
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 3;
		user_label.setLayoutData(gridData);


		final Button checkBox = new Button(shell, SWT.CHECK);
	    checkBox.setSelection(true);
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 3;
		checkBox.setLayoutData(gridData);
		Messages.setLanguageText(checkBox, "dialog.associations.askagain");

		// line

		Control labelSeparator = Utils.createSkinnedLabelSeparator(shell, SWT.HORIZONTAL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		labelSeparator.setLayoutData(gridData);

		// buttons

		new Label(shell,SWT.NULL);

		Button bYes = new Button(shell,SWT.PUSH);
	 	bYes.setText(MessageText.getString("Button.yes"));
	 	gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
	 	gridData.grabExcessHorizontalSpace = true;
	 	gridData.widthHint = 70;
		bYes.setLayoutData(gridData);
		bYes.addListener(SWT.Selection,new Listener() {
	  		@Override
			  public void handleEvent(Event e) {
		 		close(true, checkBox.getSelection());
	   		}
		 });

	 	Button bNo = new Button(shell,SWT.PUSH);
	 	bNo.setText(MessageText.getString("Button.no"));
	 	gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
	 	gridData.grabExcessHorizontalSpace = false;
	 	gridData.widthHint = 70;
		bNo.setLayoutData(gridData);
		bNo.addListener(SWT.Selection,new Listener() {
	 		@Override
		  public void handleEvent(Event e) {
		 		close(false, checkBox.getSelection());
	   		}
	 	});

	 	bYes.setFocus();
		shell.setDefaultButton( bYes );

		shell.addListener(SWT.Traverse, new Listener() {
			@Override
			public void handleEvent(Event e) {
				if ( e.character == SWT.ESC){
					close( false, true );
				}
			}
		});


	 	shell.pack ();

		Utils.centreWindow( shell );

		shell.open ();

		/*
		 * parg - removed this as causing UI hang when assoc checker and key-unlock dialogs
		 * open together
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		*/
	}

	protected void
	close(
		boolean		ok,
		boolean		check_on_startup )
 	{
    	if ( check_on_startup != COConfigurationManager.getBooleanParameter( "config.interface.checkassoc" )){

    		COConfigurationManager.setParameter( "config.interface.checkassoc",check_on_startup );

    		COConfigurationManager.save();
    	}

 		if ( ok ){

 			try{
 				platform.registerApplication();

 			}catch( PlatformManagerException e ){

 				Debug.printStackTrace( e );
 			}
 		}

 		shell.dispose();
 	}
}
