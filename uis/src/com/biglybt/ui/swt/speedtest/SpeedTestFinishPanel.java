package com.biglybt.ui.swt.speedtest;

import com.biglybt.core.CoreFactory;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.Wizard;
import com.biglybt.ui.swt.wizard.IWizardPanel;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.stats.TransferStatsView;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.SWT;
import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;

/*
 * Created on May 3, 2007
 * Created by Alan Snyder
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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
 */

public class SpeedTestFinishPanel extends AbstractWizardPanel
{

    SpeedManager speedManager;
    TransferStatsView.limitToTextHelper helper;



    public SpeedTestFinishPanel(Wizard wizard, IWizardPanel previousPanel) {
        super(wizard, previousPanel);

        speedManager = CoreFactory.getSingleton().getSpeedManager();
        helper = new TransferStatsView.limitToTextHelper();
    }


    /**
     *
     */
    @Override
    public void show() {

        String title = MessageText.getString("SpeedTestWizard.finish.panel.title");
        wizard.setTitle(title);

        Composite rootPanel = wizard.getPanel();
        GridLayout layout = new GridLayout();
        layout.numColumns = 1;
        rootPanel.setLayout(layout);

        Composite panel = new Composite(rootPanel, SWT.NULL);
        GridData gridData = new GridData( GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL );
        Utils.setLayoutData(panel, gridData);
        layout = new GridLayout();
        layout.numColumns = 3;
        layout.makeColumnsEqualWidth=true;
        panel.setLayout(layout);

        Label label = new Label(panel, SWT.WRAP);
        gridData = new GridData();
        gridData.horizontalSpan = 3;
        gridData.widthHint = 380;
        Utils.setLayoutData(label, gridData);
        Messages.setLanguageText(label,"SpeedTestWizard.finish.panel.click.close");

        //show the setting for upload speed
        SpeedManagerLimitEstimate upEst = speedManager.getEstimatedUploadCapacityBytesPerSec();
        int maxUploadKbs = upEst.getBytesPerSec()/1024;
        SpeedManagerLimitEstimate downEst = speedManager.getEstimatedDownloadCapacityBytesPerSec();
        int maxDownloadKbs = downEst.getBytesPerSec()/1024;

        //boolean setting.
        boolean autoSpeedEnabled = COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY );
        boolean autoSpeedSeedingEnabled = COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY );

        //spacer 2
        Label s2 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 3;
        s2.setLayoutData(gridData);

        String autoSpeed = MessageText.getString("SpeedTestWizard.finish.panel.auto.speed");
        createStatusLine(panel, autoSpeed, autoSpeedEnabled);

        String autoSpeedWhileSeeding = MessageText.getString("SpeedTestWizard.finish.panel.auto.speed.seeding");
        createStatusLine(panel, autoSpeedWhileSeeding, autoSpeedSeedingEnabled);

        //spacer 1
        Label s1 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 3;
        s1.setLayoutData(gridData);

        //displays a bytes/sec column and a bits/sec column
        createHeaderLine(panel);

        String maxUploadLbl = MessageText.getString("SpeedView.stats.estupcap");
        createDataLine(panel, maxUploadLbl, maxUploadKbs, upEst);

        String maxDownloadLbl = MessageText.getString("SpeedView.stats.estdowncap");
        createDataLine(panel, maxDownloadLbl, maxDownloadKbs, downEst);

    }//show

    //private static final String colSpace = "  ";

    private void createHeaderLine(Composite panel){
        GridData gridData;
        Label c1 = new Label(panel, SWT.NULL);//label
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        c1.setLayoutData(gridData);
        c1.setText(" ");


        Label c2 = new Label(panel,SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.CENTER;
        c2.setLayoutData(gridData);
        c2.setText( MessageText.getString("SpeedTestWizard.set.upload.bytes.per.sec") );


        Label c3 = new Label(panel,SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.BEGINNING;
        c3.setLayoutData(gridData);
        c3.setText( MessageText.getString("SpeedTestWizard.set.upload.bits.per.sec") );
    }

    /**
     *
     * @param panel -
     * @param label - label
     * @param enabled - is enabled
     */
    private void createStatusLine(Composite panel, String label, boolean enabled){
        GridData gridData;
        Label r3c1 = new Label(panel, SWT.NULL);//label
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.END;
        r3c1.setLayoutData(gridData);
        r3c1.setText(label);

        Label c3 = new Label(panel,SWT.NULL);//enabled or disabled
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.CENTER;
        c3.setLayoutData(gridData);
        if(enabled){
            c3.setText( MessageText.getString("SpeedTestWizard.finish.panel.enabled","enabled") );
        }else{
            c3.setText( MessageText.getString("label.disabled","disabled") );
        }

        Label c2 = new Label(panel,SWT.NULL);//space.
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.BEGINNING;
        c2.setLayoutData(gridData);
        String maxUploadBitsSec = "       ";
        c2.setText(maxUploadBitsSec);

    }//createStatusLine

    /**
     * One line of data in the UI
     * @param panel -
     * @param label - label
     * @param maxKbps - bits/sec
     * @param estimate -
     */
    private void createDataLine(Composite panel, String label, int maxKbps, SpeedManagerLimitEstimate estimate) {
        GridData gridData;
        Label c1 = new Label(panel, SWT.NULL);//max upload
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.END;
        c1.setLayoutData(gridData);
        c1.setText(label+"  ");

        Label c2 = new Label(panel,SWT.NULL);//kbytes/sec
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.CENTER;
        c2.setLayoutData(gridData);
        String estString  = helper.getLimitText( estimate );
        c2.setText(estString);

        Label c3 = new Label(panel,SWT.NULL);//kbits/sec
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.BEGINNING;
        c3.setLayoutData(gridData);

        String maxBitsPerSec;
        if(maxKbps==0){
            maxBitsPerSec = MessageText.getString("ConfigView.unlimited");
        }else{
            maxBitsPerSec = DisplayFormatters.formatByteCountToBitsPerSec( maxKbps * 1024 );
        }

        c3.setText(maxBitsPerSec);
    }


    @Override
    public boolean isPreviousEnabled(){
        return false;
    }

}
