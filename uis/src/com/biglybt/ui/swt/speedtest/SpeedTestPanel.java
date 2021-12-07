/*
 * Created on Apr 30, 2007
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


package com.biglybt.ui.swt.speedtest;


import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.*;
import com.biglybt.core.networkmanager.admin.impl.NetworkAdminSpeedTestSchedulerImpl;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;
import com.biglybt.ui.swt.wizard.WizardListener;

public class
SpeedTestPanel
	extends AbstractWizardPanel
	implements NetworkAdminSpeedTestScheduledTestListener, NetworkAdminSpeedTesterListener
{

    private NetworkAdminSpeedTestScheduler nasts;
	private NetworkAdminSpeedTestScheduledTest	scheduled_test;

    private Combo testCombo;
    private Button encryptToggle;
    private Color originalColor;

    private Button      test;
    private Button      abort;
    private Label       testCountDown1;
    private Label       testCountDown2;

    private Text textMessages;
	private ProgressBar progress;
	private Display 	display;

	private boolean		test_running;
	private boolean		switched_to_close;

    //measured upload and download results.
    int uploadTest, downloadTest;
    long maxUploadTest, maxDownloadTest;

    WizardListener clListener;

    private static final String START_VALUES = "   -         ";


    public
	SpeedTestPanel(
		SpeedTestWizard _wizard,
		IWizardPanel 	_previousPanel)
	{
	    super( _wizard, _previousPanel );
	    wizard	= _wizard;
		nasts = NetworkAdminSpeedTestSchedulerImpl.getInstance();
    }

	@Override
	public void
	show()
	{
		display = wizard.getDisplay();
		wizard.setTitle(MessageText.getString( "speedtest.wizard.run" ));
        wizard.setCurrentInfo( MessageText.getString("SpeedTestWizard.test.panel.currinfo") );
        wizard.setPreviousEnabled(false);
        wizard.setFinishEnabled(false);

        Composite rootPanel = wizard.getPanel();
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		rootPanel.setLayout(layout);

        Composite panel = new Composite(rootPanel, SWT.NULL);
        GridData gridData = new GridData(GridData.FILL_BOTH);
        panel.setLayoutData(gridData);

        /////////////////////////////////////////
        //Add group to link to Azureus Wiki page.
        /////////////////////////////////////////
        Group azWiki = new Group(panel, SWT.WRAP);
        GridData azwGridData = new GridData();
        azwGridData.widthHint = 350;
        azwGridData.horizontalSpan = 4;
        azWiki.setLayoutData(azwGridData);
        GridLayout azwLayout = new GridLayout();
        azwLayout.numColumns = 1;
        //azwLayout.marginHeight = 1;
        azWiki.setLayout(azwLayout);

        azWiki.setText(MessageText.getString("Utils.link.visit"));

        final Label linkLabel = new Label(azWiki, SWT.NULL);
        linkLabel.setText( Constants.APP_NAME + " Wiki Speed Test" );
        linkLabel.setData( Wiki.SPEED_TEST_FAQ);
        linkLabel.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
        linkLabel.setForeground(display.getSystemColor(SWT.COLOR_LINK_FOREGROUND));
        azwGridData = new GridData();
        azwGridData.horizontalIndent = 10;
        linkLabel.setLayoutData(azwGridData);
	    linkLabel.addMouseListener(new MouseAdapter() {
	      @Override
	      public void mouseDoubleClick(MouseEvent arg0) {
		      Utils.launch(arg0.widget.getData());
	      }
	      @Override
	      public void mouseUp(MouseEvent arg0) {
		      Utils.launch(arg0.widget.getData());
	      }
	    });

        //space line
        Label spacer = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 4;
        spacer.setLayoutData(gridData);

        //label explain section.
        layout = new GridLayout();
        layout.numColumns = 4;
        panel.setLayout(layout);

        Label explain = new Label(panel, SWT.WRAP);
        gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.horizontalSpan = 4;
        explain.setLayoutData(gridData);
        Messages.setLanguageText(explain,"SpeedTestWizard.test.panel.explain");


        //space line
        spacer = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 4;
        spacer.setLayoutData(gridData);

        //label type and button section.
        Label ul = new Label(panel, SWT.NULL );
        gridData = new GridData();
        ul.setLayoutData(gridData);
        Messages.setLanguageText(ul,"SpeedTestWizard.test.panel.label");

        testCombo = new Combo(panel, SWT.READ_ONLY);
        gridData = new GridData(GridData.FILL_HORIZONTAL);
        testCombo.setLayoutData(gridData);

        int[]	test_types  	= NetworkAdminSpeedTester.TEST_TYPES;
        int		up_only_index 	= 0;

        for (int i=0;i<test_types.length;i++){

        	int	test_type = test_types[i];

        	String	resource = null;

            if ( test_type == NetworkAdminSpeedTester.TEST_TYPE_UPLOAD_ONLY ){
        		resource = "up";
                up_only_index = i;
            }else if ( test_type == NetworkAdminSpeedTester.TEST_TYPE_DOWNLOAD_ONLY ){
        		resource = "down";
        	}else{
        		Debug.out( "Unknown test type" );
        	}
            //List all test in drop-down.
            testCombo.add( "BT " + MessageText.getString( "speedtest.wizard.test.mode." + resource ), i);
        }

        testCombo.select( up_only_index );

        test = new Button(panel, SWT.PUSH);
        Messages.setLanguageText(test,"dht.execute");//Run
        gridData = new GridData();
        gridData.widthHint = 70;
        test.setLayoutData(gridData);
        test.addListener(SWT.Selection, new RunButtonListener() );

        abort = new Button(panel, SWT.PUSH);
        Messages.setLanguageText(abort,"SpeedTestWizard.test.panel.abort");//Abort
        gridData = new GridData();
        gridData.widthHint = 70;
        abort.setLayoutData(gridData);
        abort.setEnabled(false);
        abort.addListener(SWT.Selection, new AbortButtonListener() );

        //toggle button line.
        Label enc = new Label( panel, SWT.NULL );
        gridData = new GridData();
        enc.setLayoutData(gridData);
        Messages.setLanguageText(enc,"SpeedTestWizard.test.panel.enc.label");

        encryptToggle = new Button(panel, SWT.TOGGLE);

        String statusString="SpeedTestWizard.test.panel.standard";
        if( encryptToggle.getSelection() ){
            statusString = "SpeedTestWizard.test.panel.encrypted";
        }
        Messages.setLanguageText(encryptToggle,statusString);
        gridData = new GridData();
        gridData.widthHint = 80;
        encryptToggle.setLayoutData(gridData);
        encryptToggle.addListener(SWT.Selection, new EncryptToggleButtonListener() );

        //finish line
        Label spacer2 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        gridData.horizontalSpan = 2;
        spacer2.setLayoutData(gridData);

        //test count down section.
        Label abortCountDown = new Label(panel, SWT.NULL);
        gridData = new GridData();
        abortCountDown.setLayoutData(gridData);
        Messages.setLanguageText(abortCountDown,"SpeedTestWizard.test.panel.abort.countdown");

        testCountDown1 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        testCountDown1.setLayoutData(gridData);
        testCountDown1.setText(START_VALUES);

        Label testFinishCountDown = new Label(panel, SWT.NULL);
        gridData = new GridData();
        testFinishCountDown.setLayoutData(gridData);
        Messages.setLanguageText(testFinishCountDown,"SpeedTestWizard.test.panel.test.countdown");

        testCountDown2 = new Label(panel, SWT.NULL);
        gridData = new GridData();
        testCountDown2.setLayoutData(gridData);
        testCountDown2.setText(START_VALUES);


        //progress bar section.
        progress = new ProgressBar(panel, SWT.SMOOTH);
		progress.setMinimum(0);
		progress.setMaximum(100);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 4;
		progress.setLayoutData(gridData);

        //message text section.
        textMessages = new Text(panel, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL );
		textMessages.setBackground(Colors.getSystemColor(display, SWT.COLOR_WHITE));
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 4;
		gridData.heightHint = 60;
		textMessages.setLayoutData(gridData);

        //this should only be new when returning from a previous panel.
        String lastData = SpeedTestData.getInstance().getLastTestData();
        if(lastData!=null){
            textMessages.setText(lastData);
        }

    }

	@Override
	public void
	finish()
	{
		test_running	= true;

        clListener = new WizardListener()
			{
				@Override
				public void
				closed()
				{
					cancel();
				}
			};

        wizard.addListener(clListener);

        wizard.setFinishEnabled( false );

        	// convert to mode

        final int test_mode = NetworkAdminSpeedTester.TEST_TYPES[testCombo.getSelectionIndex()];
        final boolean encState = encryptToggle.getSelection();

        Thread t =
			new AEThread("SpeedTest Performer")
			{
				@Override
				public void
				runSupport()
				{

                    runTest(test_mode, encState);
				}
			};

		t.setPriority(Thread.MIN_PRIORITY);
		t.setDaemon(true);
		t.start();
	}

	public void
	cancel()
	{
		if ( scheduled_test != null ){

			scheduled_test.abort();

			if ( !test.isDisposed()){

                test.setEnabled(true);
                abort.setEnabled(false);
                wizard.setNextEnabled(false);
                wizard.setFinishEnabled(false);
  			}
        }
	}

	protected void
	runTest( int test_mode, boolean encrypt_mode )
	{
		test_running	= true;

		if ( nasts.getCurrentTest() !=  null ){

            reportStage( MessageText.getString("SpeedTestWizard.test.panel.already.running") );
		}else{
				// what's the contract here in terms of listener removal?

			try{
                reportStage( MessageText.getString("SpeedTestWizard.stage.message.requesting") );
                scheduled_test = nasts.scheduleTest( NetworkAdminSpeedTestScheduler.TEST_TYPE_BT );

                scheduled_test.getTester().setMode( test_mode );
                scheduled_test.getTester().setUseCrypto( encrypt_mode );

                scheduled_test.addListener( this );
				scheduled_test.getTester().addListener( this );

				maxUploadTest 	= scheduled_test.getMaxUpBytePerSec();
				maxDownloadTest = scheduled_test.getMaxDownBytePerSec();

				scheduled_test.start();

			}catch( Throwable e ){

                String requestNotAccepted = MessageText.getString("SpeedTestWizard.test.panel.not.accepted");
                reportStage( requestNotAccepted + Debug.getNestedExceptionMessage(e));

			    if (!test.isDisposed()) {
				      display.asyncExec(new AERunnable(){
				        @Override
				        public void runSupport() {

			                test.setEnabled(true);
			                abort.setEnabled(false);
                            encryptToggle.setEnabled(true);
                        }
				      });
			    }

			}
		}//else
	}//runTest

	@Override
	public void
	stage(
		NetworkAdminSpeedTestScheduledTest 	test,
		String 								step )
	{
		reportStage( step );
	}

	@Override
	public void
	complete(
		NetworkAdminSpeedTestScheduledTest test )
	{
	}

	@Override
	public void
	stage(
		NetworkAdminSpeedTester 	tester,
		String 						step )
	{
		reportStage( step );
	}

	@Override
	public void
	complete(
		NetworkAdminSpeedTester			tester,
		NetworkAdminSpeedTesterResult 	result )
	{
        SpeedTestData.getInstance().setResult( result );
        reportComplete( result );
	}

	protected void
	reportComplete(
		final NetworkAdminSpeedTesterResult 	result )
	{
	    if ( !textMessages.isDisposed()) {
		      display.asyncExec(new AERunnable(){
		        @Override
		        public void runSupport() {
		        	if ( !textMessages.isDisposed()){
		        	  if ( result.hadError()){

                          String testFailed = MessageText.getString("SpeedTestWizard.test.panel.testfailed");//Test failed

                          textMessages.append( testFailed+": " + result.getLastError());
		        		  test.setEnabled( true );
		        		  abort.setEnabled(false);
                          encryptToggle.setEnabled(true);
                          wizard.setErrorMessage(testFailed);

		        	  }else{
                        uploadTest = result.getUploadSpeed();
                        downloadTest = result.getDownloadSpeed();
                        String uploadSpeedStr = MessageText.getString("GeneralView.label.uploadspeed");
                        String downlaodSpeedStr = MessageText.getString("GeneralView.label.downloadspeed");
                        textMessages.append(uploadSpeedStr+" " + DisplayFormatters.formatByteCountToKiBEtcPerSec(result.getUploadSpeed()) + Text.DELIMITER);
			            textMessages.append(downlaodSpeedStr+" " + DisplayFormatters.formatByteCountToKiBEtcPerSec(result.getDownloadSpeed()) + Text.DELIMITER);

                        wizard.setNextEnabled(true);

                        abort.setEnabled(false);
                        test.setEnabled(true);
                        encryptToggle.setEnabled(true);
                      }

	                  if( !result.hadError() ){
	                    switchToClose();
	                  }
		        	}
                }
		      });
        }
        wizard.removeListener(clListener);
        clListener=null;
    }

	protected void
	reportStage(
		final String 						step )
	{
	    if ( !textMessages.isDisposed()) {
		      display.asyncExec(new AERunnable(){
		        @Override
		        public void runSupport() {

		        	if ( !textMessages.isDisposed()){
	                  if(step==null)
	                    return;

	                  //intercept progress indications.
	                  if( step.startsWith("progress:")){
	                      //expect format of string to be "progress: # : ..." where # is 0-100
	                      int progressAmount = getProgressBarValueFromString(step);
	                      progress.setSelection(progressAmount);

	                      int[] timeLeft = getTimeLeftFromString(step);
	                      if(timeLeft!=null){
                              //ToDo: use SimpleDateFormat ... to internationalize this.
                              testCountDown1.setText( ""+timeLeft[0]+" sec " );//
	                          testCountDown2.setText( ""+timeLeft[1]+" sec " );
	                      }else{
	                          testCountDown1.setText(START_VALUES);
	                          testCountDown2.setText(START_VALUES);
	                      }
                          String modified = modifyProgressStatusString(step);
                          textMessages.append(modified);
                      }else{
	                      //print non-progress strings as is.
			              textMessages.append( step + Text.DELIMITER);
                      }
                  }
		        }
		      });
		    }
	}

    /**
     * Change the "progress status" string into something that can be displayed.
     * @param step - String must start with "progress:"
     * @return - a String that can be displayed in the Text Messages window.
     */
    private static String modifyProgressStatusString(String step){
        if(step==null){
            return " ";
        }
        if( !step.startsWith("progress:") ){
            return " ";
        }

        String[] values = step.split(":");
        //the expected format is:
        // progress: 87 : download ave 0 : upload ave 512438 : 93 : 3
        //values[2] should be download ave
        //values[3] should be upload ave

        if(values.length<4){
            return " ";
        }

        int downAve = getValueFromAveString(values[2]);
        int upAve = getValueFromAveString(values[3]);

        //ToDo: If an upload test then do only an upload. If a download test, then do only a download.
        //ToDo: need something that informs what the test type is.

        StringBuilder sb = new StringBuilder();
        sb.append(MessageText.getString("GeneralView.label.uploadspeed"));
        sb.append( DisplayFormatters.formatByteCountToKiBEtcPerSec( upAve ) ).append(" , ");
        sb.append(MessageText.getString("GeneralView.label.downloadspeed"));
        sb.append( DisplayFormatters.formatByteCountToKiBEtcPerSec( downAve) );
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Get the number after the last " " space in the String.
     * @param aveStr - String in format "download ave 32000"
     * @return int 32000, or -1 if an error.
     */
    private static int getValueFromAveString(String aveStr){
        try{
            int number=-2;
            aveStr = aveStr.trim();
            String[] parts = aveStr.split(" ");
            //the last item should be the number.
            if(parts!=null){
                number = Integer.parseInt( parts[parts.length-1].trim() );
            }
            return number;
        }catch(Throwable t){
            return -1;
        }
    }//getValueFromAveString

    /**
     * If you find the time left values then use them. On any error return null and the calling
     * function should handle that condition.
     * @param step - String in format "progress: #: text: text: #: #"     The last two items are
     *               the seconds till abort and seconds till complete respectively.
     * @return - int array of size 2 with time left in test, or null on any error.
     */
    private static int[] getTimeLeftFromString(String step){
        if(step==null)
            return null;
        if( !step.startsWith("progress:") )
            return null;

        String[] values = step.split(":");
        if(values.length<5){
            return null;
        }

        int[] times = new int[2];
        try{
            times[0] = Integer.parseInt( values[4].trim() );
            times[1] = Integer.parseInt( values[5].trim() );

            //don't allow time values less then zero.
            if(times[0]<0){
                times[0]=0;
            }

            if(times[1]<0){
                times[1]=0;
            }


        }catch(Exception e){
            return null;
        }
        return times;
    }//getTimeLeftFromString

    /**
     *
     * @param step - String with the expected format.  "progress: #" where # is 0 - 100.
     * @return The number as an integer, if the result is not known return 0.
     */
    private static int getProgressBarValueFromString(String step){
        if(step==null)
            return 0;

        if( !step.startsWith("progress:") )
            return 0;

        String[] value = step.split(":");
        if(value.length<2)
            return 0;

        int progress;
        try{
            progress = Integer.parseInt(value[1].trim());
        }catch(Exception e){
            return 0;
        }

        if( progress<0 || progress>100 )
            return 0;

        return progress;
    }//getProgressValueFromString

    protected void
	switchToClose()
	{
		switched_to_close	= true;

		wizard.switchToClose();
	}

	@Override
	public boolean
	isFinishEnabled()
	{
		return( !( switched_to_close || test_running ));
	}

	@Override
	public boolean
	isFinishSelectionOK()
	{
		return( !( switched_to_close || test_running ));
	}

	@Override
	public IWizardPanel
	getFinishPanel()
	{
		return( this );
	}


    @Override
    public boolean isNextEnabled(){
        //only enable after the test completes correctly.
        return ( (uploadTest>0 || downloadTest>0) && !test_running);
    }//isNextEnabled

    @Override
    public IWizardPanel getNextPanel() {

        SpeedTestData persist = SpeedTestData.getInstance();
        persist.setLastTestData( textMessages.getText() );

        return new SpeedTestSetLimitPanel( wizard, this, uploadTest, maxUploadTest, downloadTest, maxDownloadTest);
    }


    /**
     * An abort button listener
     */
    class AbortButtonListener implements Listener{

        @Override
        public void handleEvent(Event event) {
            //same action as "cancel" button.
            cancel();
            test.setEnabled(true);
            abort.setEnabled(false);
            encryptToggle.setEnabled(true);
            wizard.setNextEnabled(false);
            uploadTest=0;
            downloadTest=0;

            String testAbortedManually = MessageText.getString("SpeedTestWizard.test.panel.aborted");
            wizard.setErrorMessage(testAbortedManually);
            reportStage("\n"+testAbortedManually);

        }//handleEvent
    }


    /**
     * A run button listener
     */
    class RunButtonListener implements Listener{

        @Override
        public void handleEvent(Event event) {
            abort.setEnabled(true);
            test.setEnabled(false);
            encryptToggle.setEnabled(false);
            wizard.setErrorMessage("");
            wizard.setNextEnabled(false);
            textMessages.setText("");
            finish();
        }//handleEvent
    }

    /**
     * Run test with encryption toggle button listener.
     */
    class EncryptToggleButtonListener implements Listener{

        @Override
        public void handleEvent(Event event){

            if(encryptToggle.getSelection()){
                Messages.setLanguageText(encryptToggle,"SpeedTestWizard.test.panel.encrypted");
                originalColor = encryptToggle.getForeground();
                //Color highlightColor = ColorCache.getColor(display,178,78,127);
                Color highlightColor = Colors.getSystemColor(display, SWT.COLOR_DARK_YELLOW);
                encryptToggle.setBackground(highlightColor);
            }else{
                Messages.setLanguageText(encryptToggle,"SpeedTestWizard.test.panel.standard");
                if(originalColor!=null){
                    encryptToggle.setBackground(originalColor);
                }
            }
        }//handleEvent
    }

}
