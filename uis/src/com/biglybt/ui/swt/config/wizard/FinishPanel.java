/*
 * File    : FinishPanel.java
 * Created : 13 oct. 2003 02:37:31
 * By      : Olivier
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

package com.biglybt.ui.swt.config.wizard;

import com.biglybt.core.CoreFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;

/**
 * @author Olivier
 *
 */
public class FinishPanel extends AbstractWizardPanel<ConfigureWizard> {

  public FinishPanel(ConfigureWizard wizard, IWizardPanel<ConfigureWizard> previous) {
    super(wizard, previous);
  }

  @Override
  public void show() {
    wizard.setTitle(MessageText.getString("configureWizard.finish.title"));
    //wizard.setCurrentInfo(MessageText.getString("configureWizard.nat.hint"));
    Composite rootPanel = wizard.getPanel();
    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    rootPanel.setLayout(layout);

    Composite panel = new Composite(rootPanel, SWT.NULL);
    GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
    panel.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 3;
    panel.setLayout(layout);

    Label label = new Label(panel, SWT.WRAP);
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    gridData.widthHint = 380;
    label.setLayoutData(gridData);
    Messages.setLanguageText(label, "configureWizard.finish.message");
  }

  @Override
  public void finish() {

	wizard.completed = true;

    int	upLimit = wizard.getUploadLimit();
    if (upLimit > 0 ){
    	COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, false );
    	COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false );
    	COConfigurationManager.setParameter( "Max Upload Speed KBs", upLimit/DisplayFormatters.getKinB());
    	COConfigurationManager.setParameter( "enable.seedingonly.upload.rate", false );
	    COConfigurationManager.setParameter( "max active torrents",wizard.maxActiveTorrents);
	    COConfigurationManager.setParameter( "max downloads",wizard.maxDownloads);

	    try{
	    	SpeedManager sm = CoreFactory.getSingleton().getSpeedManager();

	    	boolean is_manual = wizard.isUploadLimitManual();

	    	sm.setEstimatedUploadCapacityBytesPerSec( upLimit, is_manual?SpeedManagerLimitEstimate.TYPE_MANUAL:SpeedManagerLimitEstimate.TYPE_MEASURED );

	    }catch( Throwable e ){

	    	Debug.out( e );
	    }

	    	// toggle to ensure listeners get the message that they should recalc things

        COConfigurationManager.setParameter( "Auto Adjust Transfer Defaults", false );
        COConfigurationManager.setParameter( "Auto Adjust Transfer Defaults", true );
    }

    if ( wizard.getWizardMode() != ConfigureWizard.WIZARD_MODE_FULL ){

    	wizard.close();

    }else{
	    COConfigurationManager.setParameter("TCP.Listen.Port",wizard.serverTCPListenPort);
	    COConfigurationManager.setParameter("UDP.Listen.Port",wizard.serverUDPListenPort);
	    COConfigurationManager.setParameter("UDP.NonData.Listen.Port",wizard.serverUDPListenPort);
	    COConfigurationManager.setParameter("General_sDefaultTorrent_Directory",wizard.torrentPath);

	    if ( wizard.hasDataPathChanged()){
	    	COConfigurationManager.setParameter( "Default save path", wizard.getDataPath());
	    }

	    COConfigurationManager.setParameter("Wizard Completed",true);
	    COConfigurationManager.save();
	    wizard.switchToClose();
    }
  }

  @Override
  public boolean isPreviousEnabled() {
    return false;
  }
}
