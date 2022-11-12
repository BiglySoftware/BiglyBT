/*
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
package com.biglybt.ui.swt.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

public class StringListChooser {

  private Display display;
  private Shell shell;
  private Label label;
  private Combo combo;

  private String result;

  public StringListChooser(final Shell parentShell) {
    result = null;

    display = parentShell.getDisplay();
    if(display == null || display.isDisposed()) return;
    display.syncExec(new Runnable() {
      @Override
      public void run() {
       createShell(parentShell);
      }
    });
  }

  private void createShell(Shell parentShell) {

    shell = ShellFactory.createShell(display,SWT.APPLICATION_MODAL | SWT.BORDER | SWT.TITLE | SWT.CLOSE);
    Utils.setShellIcon(shell);

    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    shell.setLayout(layout);
    GridData data;

    label = new Label(shell,SWT.WRAP);

    combo = new Combo(shell,SWT.READ_ONLY);

    Button ok = new Button(shell,SWT.PUSH);
    ok.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
       result = combo.getText();
       shell.dispose();
      }
    });
    ok.setText(MessageText.getString("Button.ok"));

    Button cancel = new Button(shell,SWT.PUSH);
    cancel.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {

    	  result = null;

       shell.dispose();
      }
    });
    cancel.setText(MessageText.getString("Button.cancel"));


    shell.addListener(SWT.Dispose,new Listener() {
      @Override
      public void handleEvent(Event arg0) {
      }
    });

    data = new GridData(GridData.FILL_HORIZONTAL);
    data.horizontalSpan = 2;
    data.heightHint = 30;
    label.setLayoutData(data);

    data = new GridData(GridData.FILL_HORIZONTAL);
    data.horizontalSpan = 2;
    combo.setLayoutData(data);

    data = new GridData();
    data.widthHint = 80;
    data.grabExcessHorizontalSpace = true;
    data.grabExcessVerticalSpace = true;
    data.verticalAlignment = SWT.END;
    data.horizontalAlignment = SWT.END;
    ok.setLayoutData(data);

    data = new GridData();
    data.grabExcessVerticalSpace = true;
    data.verticalAlignment = SWT.END;
    data.widthHint = 80;
    cancel.setLayoutData(data);

    shell.setSize(300,150);
    shell.layout();

    Utils.centerWindowRelativeTo(shell,parentShell);

  }

  public void setTitle(final String title) {
    Utils.execSWTThread(new AERunnable() {
      @Override
      public void runSupport() {
        if(display == null || display.isDisposed()) return;
        shell.setText(title);
      }
    });
  }

  public void setText(final String text) {
  	Utils.execSWTThread(new AERunnable() {
      @Override
      public void runSupport() {
        if(display == null || display.isDisposed()) return;
        label.setText(text.replaceAll("&", "&&"));
      }
    });
  }

  public void addOption(final String option) {
    Utils.execSWTThread(new AERunnable() {
      @Override
      public void runSupport() {
        if(display == null || display.isDisposed()) return;
        combo.add(option);
        if(combo.getItemCount() == 1) {
          combo.setText(option);
        }
      }
    });
  }

  public String open() {
    if(display == null || display.isDisposed()) return null;
    Utils.execSWTThread(new AERunnable() {
      @Override
      public void
      runSupport()
      {
        if(display == null || display.isDisposed()) {
        	return;
        }
    	  try{
    		  shell.open();
    	    
    		  Utils.readAndDispatchLoop(shell);

    	  }catch( Throwable e ){

    		  Debug.printStackTrace( e );
    	  }
      }
    });

    return result;
  }

}
