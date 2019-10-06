/*
 * Created on May 24, 2010
 * Created by Paul Gardner
 *
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


package com.biglybt.ui.swt.beta;

import org.eclipse.swt.SWT;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.update.UpdateCheckInstanceListener;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.update.UpdateMonitor;
import com.biglybt.ui.swt.wizard.Wizard;

import com.biglybt.core.CoreFactory;

public class
BetaWizard
	extends Wizard
{
	private boolean beta_enabled = COConfigurationManager.getBooleanParameter( "Beta Programme Enabled" );

	private boolean beta_was_enabled = beta_enabled;

	private boolean	finished;

	public
	BetaWizard()
	{
		super( "beta.wizard.title", false );

		BetaWizardStart panel = new BetaWizardStart( this );

		setFirstPanel( panel );
	}

	@Override
	public void
	onClose()
	{
		super.onClose();

		if (!finished) {
			return;
		}

		COConfigurationManager.setParameter( "Beta Programme Enabled", beta_enabled );

		UIFunctions uif = UIFunctionsManager.getUIFunctions();
		if (uif != null) {
			MultipleDocumentInterface mdi = uif.getMDI();
			if (mdi != null) {
				if (beta_enabled) {
					mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_BETAPROGRAM);
				} else {
					mdi.closeEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_BETAPROGRAM
					);
				}
			}
		}

		if ( !beta_enabled && Constants.IS_CVS_VERSION ){

			MessageBoxShell mb = new MessageBoxShell(
					SWT.ICON_INFORMATION | SWT.OK,
					MessageText.getString( "beta.wizard.disable.title" ),
					MessageText.getString( "beta.wizard.disable.text" ));

			mb.open(null);

		}else if ( beta_enabled && !beta_was_enabled ){

			UpdateMonitor.getSingleton(
				CoreFactory.getSingleton()).performCheck(
					true, false, false,
					new UpdateCheckInstanceListener() {
						@Override
						public void
						cancelled(
							UpdateCheckInstance instance)
						{
						}

						@Override
						public void
						complete(
							UpdateCheckInstance instance)
						{
						}
					});
		}
	}

	protected boolean
	getBetaEnabled()
	{
		return( beta_enabled );
	}

	protected void
	setBetaEnabled(
		boolean b )
	{
		beta_enabled = b;
	}

	public void
	finish()
	{
		finished = true;

		close();
	}
}
