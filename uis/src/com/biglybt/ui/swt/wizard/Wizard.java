/*
 * File    : Wizard.java
 * Created : 30 sept. 2003 00:06:56
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

package com.biglybt.ui.swt.wizard;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

/**
 * @author Olivier
 *
 */
public class Wizard {

	private final static int DEFAULT_WIDTH = 500;
  List<WizardListener>		listeners = new ArrayList<>(1);

  private final String	wizardKey;
  private final boolean	hasShellMetrics;
  
  Display display;
  Shell wizardWindow;
  Label title;
  Label currentInfo;
  Label errorMessage;
  IWizardPanel<?> currentPanel;
  Composite panel;
  Font titleFont;
  protected Button previous, next, finish, cancel;

  Listener closeCatcher;

  int wizardHeight;

  private boolean	completed;

  public
  Wizard(
	String keyTitle )
  {
	this( keyTitle, false );
  }

  public
  Wizard(
  	String 			keyTitle,
  	boolean 		modal )
  {
    this( keyTitle, modal, DEFAULT_WIDTH );
  }

  public
  Wizard(
	String		keyTitle,
  	boolean 	modal,
  	int			width )
  {
	wizardKey = "Wizard.shell:" + keyTitle;
	  
  	int style = SWT.DIALOG_TRIM | SWT.RESIZE;
  	if (modal) {
  		style |= SWT.APPLICATION_MODAL;
  	}
    wizardWindow = ShellFactory.createMainShell(style);
    this.display 	= wizardWindow.getDisplay();

    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    layout.horizontalSpacing = 0;
    layout.verticalSpacing = 0;
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    wizardWindow.setLayout(layout);
    Utils.setShellIcon(wizardWindow);

    Messages.setLanguageText( wizardWindow, keyTitle );
    
    hasShellMetrics = Utils.hasShellMetricsConfig( wizardKey );
    
    Utils.linkShellMetricsToConfig(wizardWindow, wizardKey);
    
    Composite cTitle = new Composite(wizardWindow, SWT.NULL);
    Color bg = Utils.isDarkAppearanceNative()?null:Colors.getSystemColor(display, SWT.COLOR_WHITE);
    cTitle.setBackground(bg);
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    cTitle.setLayoutData(gridData);
    GridLayout titleLayout = new GridLayout();
    titleLayout.numColumns = 1;
    cTitle.setLayout(titleLayout);
    title = new Label(cTitle, SWT.NULL);
    title.setBackground(bg);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    title.setLayoutData(gridData);
    Font font = title.getFont();
    FontData[] data = font.getFontData();
    for(int i = 0 ; i < data.length ; i++) {
      data[i].setStyle(SWT.BOLD);
    }
    titleFont=new Font(display,data);
    title.setFont(titleFont);
    currentInfo = new Label(cTitle, SWT.WRAP);
    gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL );
    currentInfo.setLayoutData(gridData);
    currentInfo.setBackground(bg);
    errorMessage = new Label(cTitle, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    errorMessage.setLayoutData(gridData);
    errorMessage.setBackground(bg);
    Color red = Colors.getSystemColor(display, SWT.COLOR_RED);
    errorMessage.setForeground(red);

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    Utils.createSkinnedLabelSeparator(wizardWindow, SWT.HORIZONTAL).setLayoutData(gridData);

    panel = new Composite(wizardWindow, SWT.NULL);
    gridData = new GridData(GridData.FILL_BOTH);
    panel.setLayoutData(gridData);

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    Utils.createSkinnedLabelSeparator(wizardWindow, SWT.HORIZONTAL).setLayoutData(gridData);

    Composite cButtons = new Composite(wizardWindow, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    cButtons.setLayoutData(gridData);
    GridLayout layoutButtons = new GridLayout();
    layoutButtons.numColumns = 5;
    cButtons.setLayout(layoutButtons);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    new Label(cButtons, SWT.NULL).setLayoutData(gridData);

    cancel = new Button(cButtons, SWT.PUSH);
    gridData = new GridData();
    gridData.widthHint = 90;
    gridData.horizontalAlignment = GridData.CENTER;
    cancel.setLayoutData(gridData);
    Messages.setLanguageText(cancel, "Button.cancel");

    previous = new Button(cButtons, SWT.PUSH);
    gridData = new GridData();
    gridData.widthHint = 90;
    gridData.horizontalAlignment = GridData.END;
    previous.setLayoutData(gridData);
    Messages.setLanguageText(previous, "wizard.previous");

    next = new Button(cButtons, SWT.PUSH);
    gridData = new GridData();
    gridData.widthHint = 90;
    gridData.horizontalAlignment = GridData.BEGINNING;
    next.setLayoutData(gridData);
    Messages.setLanguageText(next, "wizard.next");

    finish = new Button(cButtons, SWT.PUSH);
    gridData = new GridData();
    gridData.widthHint = 90;
    gridData.horizontalAlignment = GridData.CENTER;
    finish.setLayoutData(gridData);
    Messages.setLanguageText(finish, "wizard.finish");

    previous.addListener(SWT.Selection, new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
      @Override
      public void handleEvent(Event arg0) {
    	IWizardPanel<?>	prevPanel = currentPanel.getPreviousPanel();
        clearPanel();
        currentPanel = prevPanel;
        refresh();
      }
    });

    next.addListener(SWT.Selection, new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
      @Override
      public void handleEvent(Event arg0) {
        IWizardPanel<?> nextPanel = currentPanel.getNextPanel();
        clearPanel();
        currentPanel = nextPanel;
        refresh();
      }
    });

    closeCatcher = new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
      @Override
      public void handleEvent(Event event) {
        event.doit = false;
      }
    };

    finish.addListener(SWT.Selection, new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
		@Override
		public void handleEvent(Event arg0){
    		finishSelected();
		}
    });

    cancel.addListener(SWT.Selection, new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
      @Override
      public void handleEvent(Event arg0) {
    	  cancelSelected();
      }
    });

    wizardWindow.addDisposeListener(new DisposeListener() {
      @Override
      public void widgetDisposed(DisposeEvent de) {
        onClose();
      }
    });

 	wizardWindow.addListener(SWT.Traverse, new Listener() {

		@Override
		public void handleEvent(Event e) {

			if ( e.character == SWT.ESC){

				if ( cancel.isEnabled()){

					wizardWindow.dispose();
				}
			}
		}
	});

 	if ( !hasShellMetrics ){
 		wizardHeight = wizardWindow.computeSize(width,SWT.DEFAULT).y - 50;
 		wizardWindow.setSize(width, 400);
 	}
  }

  private void
  cancelSelected()
  {
	  completed = true;

	  if ( currentPanel != null ){

		  currentPanel.cancelled();
	  }
	  wizardWindow.dispose();
  }

  private void
  finishSelected()
  {
	   if ( currentPanel.isFinishSelectionOK()){
		   completed = true;
		   wizardWindow.addListener(SWT.Close, closeCatcher);
		   clearPanel();
		   currentPanel = currentPanel.getFinishPanel();
		   refresh();
		   currentPanel.finish();
	   }
  }

  private void clearPanel() {
	if ( panel.isDisposed()){
		return;
	}
    Control[] controls = panel.getChildren();
    for (int i = 0; i < controls.length; i++) {
      if (controls[i] != null && !controls[i].isDisposed())
        controls[i].dispose();
    }
    setTitle("");
    setCurrentInfo("");
  }

  private void refresh(){
    if (currentPanel == null){

    	setDefaultButton();

      	return;
    }

    previous.setEnabled(currentPanel.isPreviousEnabled());

    next.setEnabled(currentPanel.isNextEnabled());

    finish.setEnabled(currentPanel.isFinishEnabled());

	setDefaultButton();
    currentPanel.show();
    panel.layout();
    panel.redraw();
    ensureSize();
  }

	private void
	setDefaultButton()
	{
		if (!wizardWindow.isDisposed()){

		 	display.asyncExec(new AERunnable() {
				@Override
				public void runSupport() {

					if (!wizardWindow.isDisposed()){
					  	Button	default_button = null;

						if ( next.isEnabled()){

							default_button = next;

						}else if ( finish.isEnabled()){

							default_button = finish;

						}else if ( previous.isEnabled()){

							default_button = previous;

						}else if ( cancel.isEnabled()){

							default_button	= cancel;
						}

						if ( default_button != null ){

							wizardWindow.setDefaultButton( default_button );
						}
					}
		 	}
		});
	 }
  }

  public Composite getPanel() {
    return panel;
  }

  public void setTitle(String title) {
    this.title.setText(title);
  }

  public void setTitleAsResourceID(String id) {
  	Messages.setLanguageText(title, id);
  }

  public void setCurrentInfo(String currentInfo) {
	  currentInfo = currentInfo.replaceAll( "\n", "\n\t" );
	  this.currentInfo.setText("\t" + currentInfo);
	  this.currentInfo.getParent().layout();
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage.setText(errorMessage);
  }

  public void setNextEnabled(boolean enabled) {
    this.next.setEnabled(enabled);
	setDefaultButton();
  }

  public void setPreviousEnabled(boolean enabled) {
    this.previous.setEnabled(enabled);
	setDefaultButton();
  }

  public void setFinishEnabled(boolean enabled) {
    this.finish.setEnabled(enabled);
	setDefaultButton();
  }

  public void setFirstPanel(IWizardPanel<?> panel) {
    this.currentPanel = panel;
    refresh();
    ensureSize();
    if ( !hasShellMetrics ){
    	Utils.centreWindow( wizardWindow );
    }
    wizardWindow.open();
  }

  public Shell getWizardWindow() {
    return wizardWindow;
  }

  public String getErrorMessage() {
    return errorMessage.getText();
  }

  public Display getDisplay() {
    return display;
  }

  public void switchToClose() {
	  switchToClose(null);
  }

  public void switchToClose( final Runnable do_it) {
    if (!wizardWindow.isDisposed()) {
	    display.asyncExec(new AERunnable() {
	       @Override
	       public void runSupport() {
	        if (closeCatcher != null && wizardWindow != null && !wizardWindow.isDisposed()) {
	          wizardWindow.removeListener(SWT.Close, closeCatcher);
	          cancel.setText(MessageText.getString("wizard.close"));
	          cancel.setEnabled(true);
			  setDefaultButton();

			  if ( do_it != null ){
				  do_it.run();
			  }
	        }
	      }
	    });
    }
  }

  public void
  close()
  {
	  completed = true;

	  if ( !wizardWindow.isDisposed()){

		  wizardWindow.dispose();
	  }
  }

  public void onClose() {
  	if (titleFont != null && !titleFont.isDisposed()) {
  		titleFont.dispose();
  		titleFont=null;
  	}

  	for (int i=0;i<listeners.size();i++){

  		listeners.get(i).closed();
  	}

  	if ( !completed ){

  		completed = true;

  		if ( currentPanel != null ){

  			currentPanel.cancelled();
  		}
  	}
  }
  /**
   * @return Returns the currentPanel.
   */
  public IWizardPanel<?> getCurrentPanel() {
    return currentPanel;
  }

  private void ensureSize() {
  	if ( !hasShellMetrics ){
	  	Point p = panel.computeSize(wizardWindow.getSize().x,SWT.DEFAULT);
	  	int height = p.y + wizardHeight;
	  	if(height > wizardWindow.getSize().y)
	  		wizardWindow.setSize(p.x,height);
  	}
  }

  public void
  addListener(
  	WizardListener	l )
  {
  	if (wizardWindow.isDisposed() && closeCatcher != null) {
  		l.closed();
  	}
  	listeners.add(l);
  }

  public void
  removeListener(
  	WizardListener	l )
  {
  	listeners.remove(l);
  }
}
