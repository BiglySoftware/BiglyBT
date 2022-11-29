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

package com.biglybt.ui.swt.views;

import java.net.InetAddress;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.speedmanager.SpeedManager;

/**
 * @author TuxPaper
 * @created Apr 7, 2007
 *
 */
public class ViewQuickNetInfo
	implements UISWTViewCoreEventListener
{
	private UISWTView swtView;

	private Composite			composite;
	private BufferedLabel		asn;
	private BufferedLabel		current_ip;

	private SpeedManager		speed_manager;

	public
	ViewQuickNetInfo()
	{
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				speed_manager = core.getSpeedManager();
			}
		});
	}


	private void
	initialize(
		Composite parent)
	{
		parent.setLayout( new GridLayout());

		composite = new Composite( parent, SWT.BORDER );

		GridData gridData = new GridData(GridData.FILL_BOTH);

		composite.setLayoutData(gridData);

		GridLayout layout = new GridLayout(4, false);

		composite.setLayout(layout);

			// ASN

		Label label = new Label(composite,SWT.NONE);
		Messages.setLanguageText(label,"SpeedView.stats.asn");
		asn = new BufferedLabel(composite,SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		asn.setLayoutData(gridData);

			// IP

		label = new Label(composite,SWT.NONE);
		Messages.setLanguageText(label,"label.current_ip");
		current_ip = new BufferedLabel(composite,SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		current_ip.setLayoutData(gridData);


	}

	private void
	delete()
	{
		Utils.disposeComposite(composite);
	}

	private String
	getFullTitle()
	{
		return( MessageText.getString( "label.quick.net.info" ));
	}

	private Composite
	getComposite()
	{
		return composite;
	}

	private void refresh()
	{
		if ( speed_manager != null ){

			asn.setText(speed_manager.getASN());

		}

		InetAddress ip = NetworkAdmin.getSingleton().getDefaultPublicAddress( true );

		InetAddress ip_v6 = NetworkAdmin.getSingleton().getDefaultPublicAddressV6();

		String str = ip==null?"":ip.getHostAddress();

		if ( ip_v6 != null && !ip_v6.equals( ip )){

			str += (str.isEmpty()?"":", ") + ip_v6.getHostAddress();
		}

		current_ip.setText( str );
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = event.getView();
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;
      case UISWTViewEvent.TYPE_FOCUSGAINED:{
    	  composite.traverse( SWT.TRAVERSE_TAB_NEXT);
    	  break;
      }
    }

    return true;
  }
}
