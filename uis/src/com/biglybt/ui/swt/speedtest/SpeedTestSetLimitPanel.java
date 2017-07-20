package com.biglybt.ui.swt.speedtest;

import com.biglybt.core.CoreFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.stats.TransferStatsView;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;
import com.biglybt.ui.swt.wizard.Wizard;

import com.biglybt.core.networkmanager.admin.NetworkAdminSpeedTesterResult;
import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;

/*
 * Created on May 1, 2007
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

public class SpeedTestSetLimitPanel extends AbstractWizardPanel {

    private int measuredUploadKbps, measuredDownloadKbps;
    private boolean downloadTestRan,uploadTestRan = true;
    private boolean downloadHitLimit, uploadHitLimit;

    private Button apply;

    private Combo downConfLevelCombo;
    private Combo upConfLevelCombo;

    private SpeedManager speedManager;
    private TransferStatsView.limitToTextHelper helper;


    public SpeedTestSetLimitPanel(Wizard wizard, IWizardPanel previousPanel, int upload, long maxup, int download, long maxdown) {
        super(wizard, previousPanel);

        downloadHitLimit 	= download > maxdown - 20*1024;
        uploadHitLimit 		= upload > maxup - 20*1024;

        measuredUploadKbps =upload/1024;
        if(measuredUploadKbps<5){
            uploadTestRan = false;
        }


        measuredDownloadKbps =download/1024;
        if(measuredDownloadKbps<5){
            downloadTestRan = false;
        }

        speedManager = CoreFactory.getSingleton().getSpeedManager();
        helper = new TransferStatsView.limitToTextHelper();
    }

    /**
     * Panel has text at the top explaining the result.
     * Then under that it has a label the measured upload value and the recommended value.
     * Then a button with apply.
     */
    @Override
    public void show() {

        wizard.setTitle(MessageText.getString("SpeedTestWizard.set.upload.title"));
        wizard.setCurrentInfo(MessageText.getString("SpeedTestWizard.set.upload.hint"));

        Composite rootPanel = wizard.getPanel();
        GridLayout layout = new GridLayout();
        layout.numColumns = 1;
        rootPanel.setLayout(layout);

        Composite panel = new Composite(rootPanel, SWT.NULL);
        GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
        Utils.setLayoutData(panel, gridData);

        layout = new GridLayout();
        layout.numColumns = 4;
        panel.setLayout(layout);

        Label explain = new Label(panel, SWT.WRAP);
        gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.horizontalSpan = 4;
        Utils.setLayoutData(explain, gridData);
        Messages.setLanguageText(explain,"SpeedTestWizard.set.upload.panel.explain");

        //spacer line
        Label spacer = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 4;
        Utils.setLayoutData(spacer, gridData);

        Label spacer1 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        spacer1.setLayoutData(gridData);

        Label bytesCol = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.widthHint=80;
        Utils.setLayoutData(bytesCol, gridData);
        Messages.setLanguageText(bytesCol,"SpeedTestWizard.set.upload.bytes.per.sec");

        Label bitsCol = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.widthHint=80;
        Utils.setLayoutData(bitsCol, gridData);
        Messages.setLanguageText(bitsCol,"SpeedTestWizard.set.upload.bits.per.sec");

        Label confLevel = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.widthHint=80;
        Utils.setLayoutData(confLevel, gridData);
        Messages.setLanguageText(confLevel,"SpeedTestWizard.set.limit.conf.level");

        //upload limit label.
        Label ul = new Label(panel, SWT.NULL );
        gridData = new GridData();
        Utils.setLayoutData(ul, gridData);
        Messages.setLanguageText(
        		ul,
                "SpeedView.stats.estupcap",
                new String[] { DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB)});

        final Text uploadLimitSetting = new Text(panel, SWT.BORDER );
        gridData = new GridData(GridData.BEGINNING);
        gridData.widthHint=80;
        Utils.setLayoutData(uploadLimitSetting, gridData);

        int uploadCapacity = determineRateSettingEx(measuredUploadKbps,uploadTestRan,true);

        //don't accept any value less the 20 kb/s
        if(uploadCapacity<20)
            uploadCapacity=20;

        uploadLimitSetting.setText( ""+uploadCapacity );
        uploadLimitSetting.addListener(SWT.Verify, new NumberListener(uploadLimitSetting));


        //echo
        final Label echo = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.widthHint = 80;
        Utils.setLayoutData(echo, gridData);
        echo.setText( DisplayFormatters.formatByteCountToBitsPerSec(uploadCapacity*1024) );
        //This space has a change listener the updates in bits/sec.

        //want a change listener to update the echo label which has the value in bits/sec.
        uploadLimitSetting.addListener(SWT.Modify, new ByteConversionListener(echo,uploadLimitSetting));

        //confidence setting.
        final String[] confName = helper.getSettableTypes();
        final String[] confValue = helper.getSettableTypes();

        //upload confidence setting.
        int uploadDropIndex = setDefaultConfidenceLevelEx(measuredUploadKbps,uploadTestRan,true,confValue);
        upConfLevelCombo = new Combo(panel, SWT.READ_ONLY );
        addDropElements(upConfLevelCombo,confName);
        upConfLevelCombo.select(uploadDropIndex);


        //download limit label.
        Label dl = new Label( panel, SWT.NULL );
        gridData = new GridData();
        Utils.setLayoutData(dl, gridData);
        Messages.setLanguageText(
                dl,
                "SpeedView.stats.estdowncap",
                new String[] { DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB)});

        final Text downloadLimitSetting = new Text(panel, SWT.BORDER);
        gridData = new GridData(GridData.BEGINNING);
        gridData.widthHint=80;
        Utils.setLayoutData(downloadLimitSetting, gridData);

        int bestDownloadSetting = determineRateSettingEx(measuredDownloadKbps,downloadTestRan,false);

        downloadLimitSetting.setText( ""+bestDownloadSetting );
        downloadLimitSetting.addListener(SWT.Verify, new NumberListener(downloadLimitSetting) );

        //echo
        final Label downEcho = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.widthHint = 80;
        Utils.setLayoutData(downEcho, gridData);
        downEcho.setText( DisplayFormatters.formatByteCountToBitsPerSec(bestDownloadSetting*1024) );

        //convert bytes to bits on the fly for user.
        downloadLimitSetting.addListener(SWT.Modify, new ByteConversionListener(downEcho, downloadLimitSetting) );
        int downIndex = setDefaultConfidenceLevelEx(measuredDownloadKbps,downloadTestRan,false,confValue);

        downConfLevelCombo = new Combo(panel, SWT.READ_ONLY );
        addDropElements(downConfLevelCombo,confName);
        downConfLevelCombo.select(downIndex);

        //spacer col
        Label c1 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.widthHint = 80;
        c1.setLayoutData(gridData);

        SpeedManager sm = CoreFactory.getSingleton().getSpeedManager();

        if ( uploadTestRan ){

            //Since cable modems can over estimate upload need to drop type setting to estimate.
            sm.setEstimatedUploadCapacityBytesPerSec(
        			measuredUploadKbps*1024,
        			uploadHitLimit?	// parg: as far as I can tell this stuff is deliberate, probably because the 'measured' conf settings screwed things up
        				SpeedManagerLimitEstimate.TYPE_ESTIMATED :SpeedManagerLimitEstimate.TYPE_ESTIMATED);
        }

        if ( downloadTestRan ){

        	sm.setEstimatedDownloadCapacityBytesPerSec(
        			measuredDownloadKbps*1024,
        			downloadHitLimit?
        				SpeedManagerLimitEstimate.TYPE_MEASURED_MIN :SpeedManagerLimitEstimate.TYPE_MEASURED);
        }

        apply = new Button(panel, SWT.PUSH);
        Messages.setLanguageText(apply, "SpeedTestWizard.set.upload.button.apply" );
        gridData = new GridData();
        gridData.widthHint = 70;
        Utils.setLayoutData(apply, gridData);
        apply.addListener(SWT.Selection, new Listener(){
            @Override
            public void handleEvent(Event event){

                //Turn the string into an int and make it kbps.
                int uploadLimitKBPS = Integer.parseInt( uploadLimitSetting.getText() );
                int downlaodLimitKBPS = Integer.parseInt( downloadLimitSetting.getText() );
                //No value less then 20 kpbs should be allowed.
                if(uploadLimitKBPS<20){
                    uploadLimitKBPS=20;
                }

                //download value can never be less then upload.
                if( downlaodLimitKBPS < uploadLimitKBPS ){
                    downlaodLimitKBPS = uploadLimitKBPS;
                }

                	// turn off auto-speed!

                COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, false );
                COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false );

                	//set upload limits

                COConfigurationManager.setParameter( "AutoSpeed Max Upload KBs", uploadLimitKBPS );
                COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_CONFIGKEY, uploadLimitKBPS );
                COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_SEEDING_CONFIGKEY , uploadLimitKBPS );

                	// - Do we set these?
                	//COConfigurationManager.setParameter( TransferSpeedValidator.DOWNLOAD_CONFIGKEY, downlaodLimitKBPS );

                if(downloadTestRan){
                    int dIndex = downConfLevelCombo.getSelectionIndex();
                    float downEstType = helper.textToType( confValue[dIndex] );
                    speedManager.setEstimatedUploadCapacityBytesPerSec( downlaodLimitKBPS , downEstType );
                }
                if(uploadTestRan){
                    int uIndex = upConfLevelCombo.getSelectionIndex();
                    float upEstType = helper.textToType( confValue[uIndex] );
                    speedManager.setEstimatedUploadCapacityBytesPerSec( uploadLimitKBPS , upEstType );
                }

                wizard.setFinishEnabled(true);
                wizard.setPreviousEnabled(false);
            }
        });


        //spacer col
        Label c3 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        c3.setLayoutData(gridData);

        //spacer line
        Label spacer2 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 3;
        spacer2.setLayoutData(gridData);

        //switch column width to 5 columns.
        Composite resultsPanel = new Composite(rootPanel, SWT.NULL);
        gridData = new GridData( GridData.VERTICAL_ALIGN_END | GridData.FILL_HORIZONTAL );
        Utils.setLayoutData(resultsPanel, gridData);

        layout = new GridLayout();
        layout.numColumns = 5;
        layout.makeColumnsEqualWidth=true;
        resultsPanel.setLayout(layout);


        //display last test result
        NetworkAdminSpeedTesterResult result = SpeedTestData.getInstance().getLastResult();
        if( result.hadError() ){
            //error
            String error = result.getLastError();
            createResultLabels(resultsPanel,true);
            createErrorDesc(resultsPanel,error);
            createTestDesc(resultsPanel);

        }else{
            //no error
            //print out the last result format.
            int upload = result.getUploadSpeed();
            int download = result.getDownloadSpeed();

            createResultLabels(resultsPanel,false);
            createResultData(resultsPanel, MessageText.getString("GeneralView.label.uploadspeed") ,upload);
            createResultData(resultsPanel, MessageText.getString("GeneralView.label.downloadspeed"), download);
            createTestDesc(resultsPanel);
        }

    }//show

    private void addDropElements(Combo combo, String[] elements){
        if(elements==null){
            return;
        }

        int n = elements.length;
        for(int i=0;i<n;i++){
            combo.add(elements[i]);
        }
    }//

    /**
     *
     * @param transferRateKBPS -
     * @param testRan -
     * @param isUpload -
     * @param values -
     * @return - index of dropdown that matches or -1 to indicate no match.
     */
    private int setDefaultConfidenceLevelEx(int transferRateKBPS, boolean testRan, boolean isUpload, String[] values){

        float retValType;
        SpeedManagerLimitEstimate est;
        if(isUpload){
            est = speedManager.getEstimatedUploadCapacityBytesPerSec();
        }else{
            est = speedManager.getEstimatedDownloadCapacityBytesPerSec();
        }
        float originalEstType = est.getEstimateType();

        //if it was previous Fixed leave it alone.
        if( originalEstType==SpeedManagerLimitEstimate.TYPE_MANUAL ){
            retValType = originalEstType;
        }else if( !testRan ){
            //if no test was run leave then confidence level alone.
            retValType = originalEstType;
        }else if( isUpload ){
            //Since cable modems can burst data, need to downgrade rating for uploads (unfortunately)
            retValType = SpeedManagerLimitEstimate.TYPE_ESTIMATED;
        }else if( transferRateKBPS < 550 && transferRateKBPS > 450 ){
            retValType = SpeedManagerLimitEstimate.TYPE_ESTIMATED;
        }else{
            //Otherwise we can rate result as measured.
            retValType = SpeedManagerLimitEstimate.TYPE_MEASURED;
        }

        String cType = helper.typeToText(retValType);

        //find the index for this string.
        if(cType==null){
            return -1;
        }

        for(int i=0; i<values.length;i++){
            if( cType.equalsIgnoreCase( values[i] ) ){
                return i;
            }
        }

        return -1;
    }//setDef


    /**
     * Create a label for the test. The layout is assumed to be five across. If an error
     * occured in the test then the units are not printed out.
     * @param panel -
     * @param hadError - true if the test had an error.
     */
    private void createResultLabels(Composite panel,boolean hadError){
        GridData gridData;

        //spacer column
        Label c1 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        c1.setLayoutData(gridData);

        //label
        Label c2 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.END;
        c2.setLayoutData(gridData);
        c2.setText( MessageText.getString("SpeedTestWizard.set.upload.result") );


        //bytes
        Label c3 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.CENTER;
        c3.setLayoutData(gridData);
        if(!hadError){
            c3.setText( MessageText.getString("SpeedTestWizard.set.upload.bytes.per.sec") );
        }

        //bits
        Label c4 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.CENTER;
        c4.setLayoutData(gridData);
        if(!hadError){
            c4.setText( MessageText.getString("SpeedTestWizard.set.upload.bits.per.sec") );
        }

        //spacer column
        Label c5 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        c5.setLayoutData(gridData);

    }

    private void createResultData(Composite panel,String label, int rate){
        GridData gridData;

        //spacer column
        Label c1 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        c1.setLayoutData(gridData);

        //label
        Label c2 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.END;
        c2.setLayoutData(gridData);
        c2.setText( label );


        //bytes
        Label c3 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.CENTER;
        c3.setLayoutData(gridData);
        c3.setText( DisplayFormatters.formatByteCountToKiBEtcPerSec(rate) );

        //bits
        Label c4 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        gridData.horizontalAlignment = GridData.CENTER;
        c4.setLayoutData(gridData);
        c4.setText( DisplayFormatters.formatByteCountToBitsPerSec(rate) );

        //spacer column
        Label c5 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 1;
        c5.setLayoutData(gridData);
    }

    private void createTestDesc(Composite panel){

    }

    private void createErrorDesc(Composite panel,String error){

    }


    public int determineRateSettingEx(int measuredRate, boolean testRan, boolean isUpload)
    {
        int retVal = measuredRate;

        //get speed-manager setting.
        SpeedManagerLimitEstimate est;
        if( isUpload ){
            est = speedManager.getEstimatedUploadCapacityBytesPerSec();
        }else{
            est = speedManager.getEstimatedDownloadCapacityBytesPerSec();
        }

        //Use previous value if no test of this type ran.
        if( !testRan ){
            retVal = est.getBytesPerSec()/1024;
        }

        //if the previous set to Manually use that value.
        if( est.getEstimateType()==SpeedManagerLimitEstimate.TYPE_MANUAL ){
            retVal = est.getBytesPerSec()/1024;
        }

        return retVal;
    }


    @Override
    public void finish(){
        wizard.switchToClose();
    }//finish

    @Override
    public IWizardPanel getFinishPanel(){

        return new SpeedTestFinishPanel(wizard,this);
    }

    @Override
    public boolean isFinishEnabled(){
        return true;
    }

    @Override
    public boolean isNextEnabled(){
        //This is the final step for now.
        return false;
    }

    /**
     * Convert the bytes into bit.
     */
    static class ByteConversionListener implements Listener
    {
        final Label echoLbl;
        final Text setting;

        public ByteConversionListener(final Label _echoLbl, final Text _setting){
            echoLbl = _echoLbl;
            setting = _setting;
        }

        @Override
        public void handleEvent(Event e){
            String newVal = setting.getText();
            try{
                int newValInt = Integer.parseInt(newVal);
                if( echoLbl!=null ){
                    echoLbl.setText( DisplayFormatters.formatByteCountToBitsPerSec(newValInt*1024) );
                }
            }catch(Throwable t){
                //echo.setText(" - ");
            }
        }
    }//class

    /**
     * Only numbers are allowed.
     */
    static class NumberListener implements Listener
    {
        final Text setting;
        public NumberListener(final Text _setting){
            setting = _setting;
        }

        @Override
        public void handleEvent(Event e){
            String text = e.text;
            char[] chars = new char[text.length()];
            text.getChars(0, chars.length, chars, 0);
            for (int i = 0; i < chars.length; i++) {
              if (!('0' <= chars[i] && chars[i] <= '9')) {
                e.doit = false;
                return;
              }
            }
        }
    }//class

}
