package com.biglybt.ui.swt.views.configsections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.speedmanager.impl.v2.SpeedLimitMonitor;
import com.biglybt.core.speedmanager.impl.v2.SpeedManagerAlgorithmProviderV2;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.AEDiagnosticsLogger;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.config.BooleanParameter;
import com.biglybt.ui.swt.config.IntListParameter;
import com.biglybt.ui.swt.config.IntParameter;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

/*
 * Created on May 15, 2007
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

public class ConfigSectionTransferAutoSpeedBeta
        implements UISWTConfigSection
{

    /**
     * Returns section you want your configuration panel to be under.
     * See SECTION_* constants.  To add a subsection to your own ConfigSection,
     * return the configSectionGetName result of your parent.<br>
     */
    @Override
    public String configSectionGetParentSection() {
        return "transfer.select";
    }

    /**
     * In order for the plugin to display its section correctly, a key in the
     * Plugin language file will need to contain
     * <TT>ConfigView.section.<i>&lt;configSectionGetName() result&gt;</i>=The Section name.</TT><br>
     *
     * @return The name of the configuration section
     */
    @Override
    public String configSectionGetName() {
        return "transfer.select.v2";
    }

    /**
     * User selected Save.
     * All saving of non-plugin tabs have been completed, as well as
     * saving of plugins that implement com.biglybt.pif.ui.config
     * parameters.
     */
    @Override
    public void configSectionSave() {

    }

    /**
     * Config view is closing
     */
    @Override
    public void configSectionDelete() {

    }

	@Override
	public int maxUserMode() {
		return 0;
	}



    @Override
    public Composite configSectionCreate(final Composite parent) {

        GridData gridData;

        Composite cSection = new Composite(parent, SWT.NULL);

        gridData = new GridData(GridData.VERTICAL_ALIGN_FILL|GridData.HORIZONTAL_ALIGN_FILL);
        cSection.setLayoutData(gridData);
        GridLayout subPanel = new GridLayout();
        subPanel.numColumns = 3;
        cSection.setLayout(subPanel);

        //add a comment to the debug log.
        ///////////////////////////////////
        // Comment group
        ///////////////////////////////////
        Group commentGroup = new Group(cSection, SWT.NULL);
        Messages.setLanguageText(commentGroup,"ConfigTransferAutoSpeed.add.comment.to.log.group");
        GridLayout commentLayout = new GridLayout();
        commentLayout.numColumns = 3;
        commentGroup.setLayout(commentLayout);
        gridData = new GridData(GridData.FILL_HORIZONTAL);
        commentGroup.setLayoutData(gridData);

        //Label
        Label commentLabel = new Label(commentGroup,SWT.NULL);
        Messages.setLanguageText(commentLabel,"ConfigTransferAutoSpeed.add.comment.to.log");
        gridData = new GridData();
        gridData.horizontalSpan=1;
        commentLabel.setLayoutData(gridData);

        //Text-Box
        final Text commentBox = new Text(commentGroup, SWT.BORDER);
        gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.horizontalSpan=1;
        commentBox.setText("");
        commentBox.setLayoutData(gridData);


        //button
        Button commentButton = new Button(commentGroup, SWT.PUSH);
        //Messages.
        gridData = new GridData();
        gridData.horizontalSpan=1;
        commentButton.setLayoutData(gridData);
        Messages.setLanguageText(commentButton,"ConfigTransferAutoSpeed.log.button");
        commentButton.addListener(SWT.Selection, new Listener(){
            @Override
            public void handleEvent(Event event){
                //Add a file to the log.
                AEDiagnosticsLogger dLog = AEDiagnostics.getLogger("AutoSpeed");
                String comment = commentBox.getText();
                if(comment!=null){
                    if( comment.length()>0){
                        dLog.log( "user-comment:"+comment );
                        commentBox.setText("");
                    }
                }
            }
        });

        //spacer
        Label commentSpacer = new Label(cSection, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan=3;
        commentSpacer.setLayoutData(gridData);



        ///////////////////////////
        // Upload Capacity used settings.
        ///////////////////////////
        Group uploadCapGroup = new Group(cSection, SWT.NULL);
        Messages.setLanguageText(uploadCapGroup,"ConfigTransferAutoSpeed.upload.capacity.usage");

        GridLayout uCapLayout = new GridLayout();
        uCapLayout.numColumns = 2;
        uploadCapGroup.setLayout(uCapLayout);

        gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.horizontalSpan = 3;
        uploadCapGroup.setLayoutData(gridData);

        //Label column
        Label upCapModeLbl = new Label(uploadCapGroup, SWT.NULL);
        gridData = new GridData();
        upCapModeLbl.setLayoutData(gridData);
        Messages.setLanguageText(upCapModeLbl,"ConfigTransferAutoSpeed.mode");


        Label ucSetLbl = new Label(uploadCapGroup, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 2;
        Messages.setLanguageText(ucSetLbl,"ConfigTransferAutoSpeed.capacity.used");

        Label dlModeLbl = new Label(uploadCapGroup, SWT.NULL);
        Messages.setLanguageText(dlModeLbl,"ConfigTransferAutoSpeed.while.downloading");

        //add a drop down.
        String[] downloadModeNames = {
                " 80%",
                " 70%",
                " 60%",
                " 50%"
        };

        int[] downloadModeValues = {
                80,
                70,
                60,
                50
        };

        new IntListParameter(uploadCapGroup,
                SpeedLimitMonitor.USED_UPLOAD_CAPACITY_DOWNLOAD_MODE,
                downloadModeNames, downloadModeValues);


        //spacer
        Label cSpacer = new Label(cSection, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan=4;
        cSpacer.setLayoutData(gridData);

        //////////////////////////
        // DHT Ping Group
        //////////////////////////

        Group dhtGroup = new Group(cSection, SWT.NULL);
        Messages.setLanguageText(dhtGroup,"ConfigTransferAutoSpeed.data.update.frequency");
        dhtGroup.setLayout(subPanel);

        gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.horizontalSpan = 3;
        dhtGroup.setLayoutData(gridData);

        //how much data to accumulate before making an adjustment.
        Label iCount = new Label(dhtGroup, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan=2;
        gridData.horizontalAlignment=GridData.BEGINNING;
        iCount.setLayoutData(gridData);
        //iCount.setText("Adjustment interval: ");
        Messages.setLanguageText(iCount,"ConfigTransferAutoSpeed.adjustment.interval");

        IntParameter adjustmentInterval = new IntParameter(dhtGroup, SpeedManagerAlgorithmProviderV2.SETTING_INTERVALS_BETWEEN_ADJUST);
        gridData = new GridData();
        adjustmentInterval.setLayoutData(gridData);

        //spacer
        cSpacer = new Label(cSection, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan=1;
        cSpacer.setLayoutData(gridData);

        //how much data to accumulate before making an adjustment.
        Label skip = new Label(dhtGroup, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan=2;
        gridData.horizontalAlignment=GridData.BEGINNING;
        skip.setLayoutData(gridData);
        //skip.setText("Skip after adjustment: ");
        Messages.setLanguageText(skip,"ConfigTransferAutoSpeed.skip.after.adjust");

        BooleanParameter skipAfterAdjustment = new BooleanParameter(dhtGroup, SpeedManagerAlgorithmProviderV2.SETTING_WAIT_AFTER_ADJUST);
        gridData = new GridData();
        skipAfterAdjustment.setLayoutData(gridData);

        //spacer
        cSpacer = new Label(cSection, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan=3;
        cSpacer.setLayoutData(gridData);

        return cSection;
    }
}
