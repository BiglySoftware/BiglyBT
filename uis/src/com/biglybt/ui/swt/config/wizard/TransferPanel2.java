/*
 * File    : TransferPanel.java
 * Created : 12 oct. 2003 19:41:14
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

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.CoreFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

/**
 * @author Olivier
 *
 */
public class
TransferPanel2
	extends AbstractWizardPanel<ConfigureWizard>
{
	private static final int kbit = 1000;
	private static final int mbit = 1000*1000;

	private static final int[] connection_rates = {
		0,
		28800,
		56 * kbit,
		64 * kbit,
		96 * kbit,
		128 * kbit,
		192 * kbit,
		256 * kbit,
		384 * kbit,
		512 * kbit,
		640 * kbit,
		768 * kbit,
		1 * mbit,
		2 * mbit,
		5 * mbit,
		10 * mbit,
		20 * mbit,
		50 * mbit,
		100 * mbit,
	};


	private volatile boolean test_in_progress;

	private boolean manual_mode;

	private Label	uprate_label;

  public TransferPanel2(ConfigureWizard wizard, IWizardPanel previous) {
    super(wizard, previous);
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.wizard.IWizardPanel#show()
   */
  @Override
  public void show() {
    wizard.setTitle(MessageText.getString("configureWizard.transfer.title"));
    wizard.setCurrentInfo(MessageText.getString("configureWizard.transfer2.hint"));
    final Composite rootPanel = wizard.getPanel();
    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    rootPanel.setLayout(layout);

    Composite panel = new Composite(rootPanel, SWT.NULL);
    GridData gridData = new GridData(GridData.FILL_BOTH);
    panel.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 2;
    panel.setLayout(layout);

    Label label = new Label(panel, SWT.WRAP);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    label.setLayoutData(gridData);
    Messages.setLanguageText(label, "configureWizard.transfer2.message");

    final Group gRadio = Utils.createSkinnedGroup(panel, SWT.NULL);
    Messages.setLanguageText(gRadio, "configureWizard.transfer2.group");
    gRadio.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 2;
    gRadio.setLayout( layout );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    gRadio.setLayoutData(gridData);


    	// auto button

    Button auto_button = new Button (gRadio, SWT.RADIO);
    Messages.setLanguageText(auto_button, "auto.mode");
    auto_button.setSelection( true );

    new Label( gRadio, SWT.NULL );

    	// speed test button

    label = new Label( gRadio, SWT.NULL );
    Messages.setLanguageText(label, "configureWizard.transfer2.test.info");

    final Button speed_test = new Button( gRadio, SWT.NULL );

    Messages.setLanguageText( speed_test, "configureWizard.transfer2.test" );

    final SelectionAdapter speed_test_listener =
    	new SelectionAdapter()
    	{
    		@Override
		    public void
    		widgetSelected(
    			SelectionEvent arg0 )
    		{
    			speed_test.setEnabled( false );

    			test_in_progress = true;

    			updateNextEnabled();

				rootPanel.getShell().setEnabled( false );

    			UIFunctionsManager.getUIFunctions().installPlugin(
    				"mlab",
    				"dlg.install.mlab",
    				new UIFunctions.actionListener()
    				{
    					@Override
					    public void
    					actionComplete(
    						Object result )
    					{
    						if ( result instanceof Boolean ){

    							PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "mlab" );

    							if ( pi == null ){

    								Debug.out( "mlab plugin not found" );

    								enableTest();

    							}else{
	    							IPCInterface callback =
	    								new IPCInterface()
		    							{
	    									@Override
										    public Object
	    									invoke(
	    										String 		methodName,
	    										Object[]	params )
	    									{
	    										try{
		    										if ( methodName.equals( "results" )){

			    										Map<String,Object> 	results = (Map<String,Object>)params[0];

			    										Long	up_rate = (Long)results.get( "up" );

			    										if ( up_rate != null ){

			    											final int u = up_rate.intValue();

			    											if ( u > 0 ){

			    												Utils.execSWTThread(
			    													new Runnable()
			    													{
			    														@Override
																	    public void
			    														run()
			    														{
			    															updateUp( u, false );
			    														}
			    													});
			    											}
			    										}
		    										}

		    										return( null );

	    										}finally{

	    											enableTest();
	    										}
	    									}

	    									@Override
										    public boolean
	    									canInvoke(
	    										String methodName,
	    										Object[] params )
	    									{
	    										return( true );
	    									}
		    							};

		    						try{
		    							pi.getIPC().invoke(
		    								"runTest",
		    								new Object[]{ new HashMap<String,Object>(), callback, false });

		    						}catch( Throwable e ){

		    							Debug.out( e );

		    							enableTest();
		    						}
    							}
    						}else{

    							try{
    								Throwable error = (Throwable)result;

    								Debug.out( error );

    							}finally{

    								enableTest();
    							}
    						}
    					}

    					protected void
    					enableTest()
    					{
							Utils.execSWTThread(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											speed_test.setEnabled( true );

											test_in_progress = false;

											updateNextEnabled();

											rootPanel.getShell().setEnabled( true );
										}
									});
    					}
    				});
    		}
    	};

 	speed_test.addSelectionListener( speed_test_listener );

    	// manual

    final Button manual_button = new Button( gRadio, SWT.RADIO );
    Messages.setLanguageText(manual_button, "manual.mode");

    new Label( gRadio, SWT.NULL );

    	// drop down speed selector

    final Label manual_label = new Label( gRadio, SWT.NULL );
    Messages.setLanguageText(manual_label, "configureWizard.transfer2.mselect");

    String connection_labels[] = new String[connection_rates.length];

    connection_labels[0] = MessageText.getString( "configureWizard.transfer2.current" );

    String dial_up = MessageText.getString( "dial.up" );

    for (int i = 1; i < connection_rates.length; i++) {

    	connection_labels[i] = (i<3?(dial_up+ " "):"xxx/") + DisplayFormatters.formatByteCountToBitsPerSec2( connection_rates[i]/8);
    }

    final Combo connection_speed = new Combo(gRadio, SWT.SINGLE | SWT.READ_ONLY);

    for ( int i=0; i<connection_rates.length; i++ ){

    	connection_speed.add(connection_labels[i]);
    }

    connection_speed.select(0);

    connection_speed.addListener(
    	SWT.Selection,
    	new Listener()
    	{
    		@Override
		    public void
    		handleEvent(
    			Event arg0 )
    		{
    			int index = connection_speed.getSelectionIndex();

    			updateUp( connection_rates[index]/8, true );
     		}
    	});

    final Label manual2_label = new Label( gRadio, SWT.WRAP );
    Messages.setLanguageText(manual2_label, "configureWizard.transfer2.mselect.info");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    manual2_label.setLayoutData(gridData);

    Listener listener =
    	new Listener()
		{
			@Override
			public void
			handleEvent(
				Event arg0 )
			{
				boolean is_manual = manual_button.getSelection();

				speed_test.setEnabled( !is_manual );

				connection_speed.setEnabled( is_manual );
				manual_label.setEnabled( is_manual );
				manual2_label.setEnabled( is_manual );

				manual_mode = is_manual;

				updateNextEnabled();
			}
		};
    manual_button.addListener( SWT.Selection, listener );

    listener.handleEvent( null );

    uprate_label = new Label( panel, SWT.WRAP );
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.verticalIndent=10;
    uprate_label.setLayoutData(gridData);
    updateUp( 0, true );

    manual_mode = false;

    updateNextEnabled();

    if ( wizard.getWizardMode() == ConfigureWizard.WIZARD_MODE_SPEED_TEST_AUTO ){

    	Utils.execSWTThreadLater(
    		0,
    		new Runnable()
    		{
    			@Override
			    public void
    			run()
    			{
    				speed_test_listener.widgetSelected( null );
    			}
    		});
    }
  }

  private void
  updateUp(
	  int		rate,
	  boolean	manual )
  {
	wizard.setConnectionUploadLimit( rate, manual );

	if ( rate == 0 ){

		uprate_label.setText( MessageText.getString( "configureWizard.transfer2.rate.unchanged" ));

	}else{

		uprate_label.setText(
			MessageText.getString( "configureWizard.transfer2.rate.changed",
			new String[]{
				DisplayFormatters.formatByteCountToBitsPerSec2( rate ) + " (" + DisplayFormatters.formatByteCountToKiBEtcPerSec( rate ) + ")" ,
				DisplayFormatters.formatByteCountToKiBEtcPerSec( wizard.getUploadLimit()),
				String.valueOf( wizard.maxActiveTorrents ),
				String.valueOf( wizard.maxDownloads )
			}));
	}
  }

  private void
  updateNextEnabled()
  {
	 wizard.setPreviousEnabled( isPreviousEnabled() );

	 boolean enabled = isProgressEnabled();


	 if ( wizard.getWizardMode() != ConfigureWizard.WIZARD_MODE_FULL ){

		 wizard.setNextEnabled( false );

	   	 wizard.setFinishEnabled( enabled );
	 }else{

		 wizard.setNextEnabled( enabled );
	 }
  }

  public boolean
  isProgressEnabled()
  {
    if ( test_in_progress ){

    	return( false );
    }

    if ( manual_mode || wizard.getConnectionUploadLimit() > 0 ){

    	return( true );
    }

    return( false );
  }

  @Override
  public boolean
  isNextEnabled()
  {
	  return( isProgressEnabled() && wizard.getWizardMode() == ConfigureWizard.WIZARD_MODE_FULL);
  }

  @Override
  public boolean
  isPreviousEnabled()
  {
    return( ! ( test_in_progress || wizard.getWizardMode() != ConfigureWizard.WIZARD_MODE_FULL ));
  }

  @Override
  public IWizardPanel
  getFinishPanel()
  {
    return( new FinishPanel(wizard,this));
  }

  @Override
  public IWizardPanel
  getNextPanel()
  {
    return( new NatPanel((wizard),this));
  }

}
