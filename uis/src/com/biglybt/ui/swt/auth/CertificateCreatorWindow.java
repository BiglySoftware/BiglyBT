/*
 * Created on 08-Jun-2004
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

package com.biglybt.ui.swt.auth;

/**
 * @author parg
 *
 */

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;


public class
CertificateCreatorWindow
{
	public
	CertificateCreatorWindow()
	{
		createCertificate();
	}

	public void
	createCertificate()
	{
		final Display	display = Utils.getDisplay();

		if ( display.isDisposed()){

			return;
		}

		try{
			display.asyncExec(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							 new createDialog( display );
						}
					});
		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	protected static class
	createDialog
	{
		protected Shell			shell;

		protected
		createDialog(
			Display				display )
		{
			if ( display.isDisposed()){

				return;
			}

			shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);

			Utils.setShellIcon(shell);
			Messages.setLanguageText(shell, "security.certcreate.title");

			GridLayout layout = new GridLayout();
			layout.numColumns = 3;

			shell.setLayout (layout);

			GridData gridData;

			// info

			Label info_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(info_label, "security.certcreate.intro");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 3;
			info_label.setLayoutData(gridData);

			// alias

			Label alias_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(alias_label, "security.certcreate.alias");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			alias_label.setLayoutData(gridData);

			final Text alias_field =new Text(shell,SWT.BORDER);

			alias_field.setText( SESecurityManager.DEFAULT_ALIAS );

			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			alias_field.setLayoutData(gridData);

			// strength

			Label strength_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(strength_label, "security.certcreate.strength");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			strength_label.setLayoutData(gridData);

			final Combo strength_combo = new Combo(shell, SWT.SINGLE | SWT.READ_ONLY);

			final int[] strengths = { 512, 1024, 1536, 2048 };

			for (int i=0;i<strengths.length;i++){

				strength_combo.add(""+strengths[i]);
			}

			strength_combo.select(1);

			new Label(shell,SWT.NULL);

			// first + last name

			String[]	field_names = {
									"security.certcreate.firstlastname",
									"security.certcreate.orgunit",
									"security.certcreate.org",
									"security.certcreate.city",
									"security.certcreate.state",
									"security.certcreate.country"
								};

			final String[]		field_rns = {"CN", "OU", "O", "L", "ST", "C" };

			final Text[]		fields = new Text[field_names.length];

			for (int i=0;i<fields.length;i++){

				Label resource_label = new Label(shell,SWT.NULL);
				Messages.setLanguageText(resource_label, field_names[i]);
				gridData = new GridData(GridData.FILL_BOTH);
				gridData.horizontalSpan = 1;
				resource_label.setLayoutData(gridData);

				Text field = fields[i] = new Text(shell,SWT.BORDER);
				gridData = new GridData(GridData.FILL_BOTH);
				gridData.horizontalSpan = 2;
				field.setLayoutData(gridData);
			}

				// line

			Control labelSeparator = Utils.createSkinnedLabelSeparator(shell, SWT.HORIZONTAL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 3;
			labelSeparator.setLayoutData(gridData);

				// buttons

			new Label(shell,SWT.NULL);

			Composite comp = new Composite(shell,SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.horizontalSpan = 2;
			comp.setLayoutData(gridData);
			GridLayout layoutButtons = new GridLayout();
			layoutButtons.numColumns = 2;
			comp.setLayout(layoutButtons);



			Button bYes = new Button(comp,SWT.PUSH);
			Messages.setLanguageText(bYes, "security.certcreate.ok");
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.widthHint = 70;
			bYes.setLayoutData(gridData);
			bYes.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event e) {

					String	alias	= alias_field.getText().trim();

					int		strength	= strengths[strength_combo.getSelectionIndex()];

					String	dn = "";

					for (int i=0;i<fields.length;i++){

						String	rn = fields[i].getText().trim();

						if ( rn.length() == 0 ){

							rn = "Unknown";
						}

						dn += (dn.length()==0?"":",") + field_rns[i] + "=" + rn;
					}

					try{
						SESecurityManager.createSelfSignedCertificate( alias, dn, strength );

						close(true );

						Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
								LogAlert.AT_INFORMATION, MessageText
										.getString("security.certcreate.createok")
										+ "\n" + alias + ":" + strength + "\n"
										+ dn + "\n" + SystemTime.getCurrentTime()));

					}catch( Throwable f ){

						Logger.log(new LogAlert(LogAlert.UNREPEATABLE, MessageText
								.getString("security.certcreate.createfail")
								+ "\n" + SystemTime.getCurrentTime(), f));
					}
				}
			});

			Button bNo = new Button(comp,SWT.PUSH);
			Messages.setLanguageText(bNo, "security.certcreate.cancel");
			gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
			gridData.grabExcessHorizontalSpace = false;
			gridData.widthHint = 70;
			bNo.setLayoutData(gridData);
			bNo.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event e) {
					close(false);
				}
			});

			shell.setDefaultButton( bYes );

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
		}

		protected void
		close(
			boolean		ok )
		{
			shell.dispose();
		}
	}
}