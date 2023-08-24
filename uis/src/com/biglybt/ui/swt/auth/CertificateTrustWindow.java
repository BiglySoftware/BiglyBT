/*
 * File    : CertificateTrustWindow.java
 * Created : 29-Dec-2003
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

package com.biglybt.ui.swt.auth;

/**
 * @author parg
 *
 */

import java.security.cert.X509Certificate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.security.SECertificateListener;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;


public class
CertificateTrustWindow
	implements SECertificateListener
{
	public
	CertificateTrustWindow()
	{
		SESecurityManager.addCertificateListener( this );
	}

	@Override
	public boolean
	trustCertificate(
		final String			_resource,
		final X509Certificate	cert )
	{
		final Display	display = Utils.getDisplay();

		if ( display.isDisposed()){

			return( false );
		}

		TOTorrent		torrent = TorrentUtils.getTLSTorrent();

		final String resource;

		if ( torrent != null ){


			resource	= TorrentUtils.getLocalisedName( torrent ) + "\n" + _resource;
		}else{

			resource	= _resource;
		}

		final trustDialog[]	dialog = new trustDialog[1];

		try{
			Utils.execSWTThread(new AERunnable() {
						@Override
						public void
						runSupport()
						{
							dialog[0] = new trustDialog( display, resource, cert );
						}
					}, false);
		}catch( Throwable e ){

			Debug.printStackTrace( e );

			return( false );
		}

		return(dialog[0].getTrusted());
	}

	protected static class
	trustDialog
	{
		protected Shell			shell;

		protected boolean		trusted;

		protected
		trustDialog(
				Display				display,
				String				resource,
				X509Certificate		cert )
		{
			if ( display.isDisposed()){

				return;
			}

			shell =  ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);

			Utils.setShellIcon(shell);
			shell.setText(MessageText.getString("security.certtruster.title"));

			GridLayout layout = new GridLayout();
			layout.numColumns = 3;

			shell.setLayout (layout);

			GridData gridData;

			// info

			Label info_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(info_label, "security.certtruster.intro");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 3;
			info_label.setLayoutData(gridData);

			// resource

			Label resource_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(resource_label, "security.certtruster.resource");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			resource_label.setLayoutData(gridData);

			Label resource_value = new Label(shell,SWT.WRAP);
			resource_value.setText(resource.replaceAll("&", "&&"));
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			resource_value.setLayoutData(gridData);

			// issued by

			Label issued_by_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(issued_by_label, "security.certtruster.issuedby");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			issued_by_label.setLayoutData(gridData);

			Label issued_by_value = new Label(shell,SWT.NULL);
			issued_by_value.setText(extractCN(cert.getIssuerX500Principal().getName()).replaceAll("&", "&&"));
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			issued_by_value.setLayoutData(gridData);

			// issued to

			Label issued_to_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(issued_to_label, "security.certtruster.issuedto");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			issued_to_label.setLayoutData(gridData);

			Label issued_to_value = new Label(shell,SWT.NULL);
			issued_to_value.setText(extractCN(cert.getSubjectX500Principal().getName()).replaceAll("&", "&&"));
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			issued_to_value.setLayoutData(gridData);

			// prompt

			Label prompt_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(prompt_label, "security.certtruster.prompt");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 3;
			prompt_label.setLayoutData(gridData);

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
			bYes.setText(MessageText.getString("security.certtruster.yes"));
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.widthHint = 70;
			bYes.setLayoutData(gridData);
			bYes.addListener(SWT.Selection,new Listener() {
				@Override
				public void handleEvent(Event e) {
					close(true);
				}
			});

			Button bNo = new Button(comp,SWT.PUSH);
			bNo.setText(MessageText.getString("security.certtruster.no"));
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

			Utils.readAndDispatchLoop( shell );
		}

		protected void
		close(
			boolean		ok )
		{
			trusted = ok;

			shell.dispose();
		}

		protected String
		extractCN(
			String		dn )
		{
			int	p1 = dn.indexOf( "CN=");

			if ( p1 == -1 ){
				return( dn );
			}

			int	p2 = dn.indexOf(",", p1 );

			if ( p2 == -1 ){

				return( dn.substring(p1+3).trim());
			}

			return( dn.substring(p1+3,p2).trim());
		}

		public boolean
		getTrusted()
		{
			return( trusted );
		}
	}
}
